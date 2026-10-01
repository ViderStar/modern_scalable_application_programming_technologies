package by.bsuir.bank.credit;

import by.bsuir.bank.common.client.ClientApi;
import by.bsuir.bank.common.client.ClientInfo;
import by.bsuir.bank.common.ledger.InMemoryLedger;
import by.bsuir.bank.credit.atm.DepositApi;
import by.bsuir.bank.credit.repository.CardRepository;
import by.bsuir.bank.credit.repository.CreditContractRepository;
import by.bsuir.bank.credit.service.CreditService;
import by.bsuir.bank.credit.service.DemoCards;
import by.bsuir.bank.credit.service.DemoCards.DemoCardView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционные тесты демонстрационных карт и локализации ответов банка банкомату.
 * Фоновая выдача при старте отключена: тест вызывает её сам, когда заглушки смежных сервисов готовы.
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:${random.uuid};DB_CLOSE_DELAY=-1",
        "bank.demo.enabled=false"})
@AutoConfigureMockMvc
@Import(TestLedgerConfig.class)
class DemoCardsIT {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private DemoCards demoCards;
    @Autowired
    private CreditService credits;
    @Autowired
    private CreditContractRepository contracts;
    @Autowired
    private CardRepository cards;
    @Autowired
    private InMemoryLedger ledger;
    @MockitoBean
    private ClientApi clients;
    @MockitoBean
    private DepositApi deposits;

    @BeforeEach
    void seedDemoCards() {
        cards.deleteAll();
        contracts.deleteAll();
        ledger.reset();
        when(clients.get(anyLong())).thenAnswer(call ->
                new ClientInfo(call.getArgument(0), "Клиент №" + call.getArgument(0), "MP", "7654321", "3170590A077PB4"));
        demoCards.seed();
    }

    @Test
    @DisplayName("После старта выпущены три демо-карты с PIN-кодами из настроек")
    void demoCardsAreIssued() throws Exception {
        List<DemoCardView> list = demoCards.list();

        assertThat(list).extracting(DemoCardView::pin).containsExactly("1111", "2222", "3333");
        assertThat(list).extracting(DemoCardView::contract).containsExactly("К-900001", "К-900002", "К-900003");
        assertThat(list).allSatisfy(card -> {
            assertThat(card.cardNumber()).hasSize(16);
            assertThat(card.blocked()).isFalse();
        });
        mvc.perform(get("/api/atm/demo-cards"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].holder").value("Клиент №1"))
                .andExpect(jsonPath("$[0].pin").value("1111"));
        // PIN-код и для демо-карты хранится только хешем
        assertThat(cards.findAll()).allSatisfy(card -> assertThat(card.getPinHash()).startsWith("$2"));
    }

    @Test
    @DisplayName("Повторная выдача ничего не меняет, демо-договоры не занимают обычные номера")
    void seedingIsIdempotent() {
        demoCards.seed();

        assertThat(contracts.count()).isEqualTo(3);
        assertThat(ledger.journal()).hasSize(3);
        assertThat(credits.meta().nextNumber()).isEqualTo("К-000001");
    }

    @Test
    @DisplayName("Демо-карта работает в банкомате: авторизация и снятие с PIN-кодом из подсказки")
    void demoCardWorksInAtm() throws Exception {
        DemoCardView card = demoCards.list().get(0);

        transaction(card, "ru", "AUTHORIZE", "").andExpect(jsonPath("$.status").value("OK"));
        transaction(card, "ru", "WITHDRAW", ", {\"name\": \"AMOUNT\", \"value\": \"200\"}")
                .andExpect(jsonPath("$.data.balance").value(4800.00));
    }

    @Test
    @DisplayName("После перевыпуска PIN-кода подсказка его больше не показывает")
    void reissuedPinIsHidden() throws Exception {
        Long contractId = contracts.findByNumber("К-900002").orElseThrow().getId();

        mvc.perform(post("/api/credits/" + contractId + "/pin")).andExpect(status().isOk());

        assertThat(demoCards.list()).extracting(DemoCardView::pin).containsExactly("1111", null, "3333");
    }

    @Test
    @DisplayName("Ответы банка банкомату приходят на языке запроса")
    void atmRepliesAreLocalized() throws Exception {
        DemoCardView card = demoCards.list().get(2);
        DemoCardView wrong = new DemoCardView(card.cardNumber(), "0000", card.holder(), card.contract(), false);

        transaction(wrong, "en", "AUTHORIZE", "").andExpect(jsonPath("$.message").value("Wrong PIN. Attempts left: 2"));
        transaction(wrong, "be", "AUTHORIZE", "")
                .andExpect(jsonPath("$.message").value("Няправільны PIN-код. Засталося спроб: 1"));
        transaction(card, "en", "WITHDRAW", ", {\"name\": \"AMOUNT\", \"value\": \"100\"}")
                .andExpect(jsonPath("$.message").value("Please take your cash"));
        transaction(card, "be", "WITHDRAW", ", {\"name\": \"AMOUNT\", \"value\": \"99999\"}")
                .andExpect(jsonPath("$.message").value("Недастаткова сродкаў на рахунку: даступна 9900.00 BYN"));
        mvc.perform(get("/api/products").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(jsonPath("$[0].name").value("R-Money mix"))
                .andExpect(jsonPath("$[1].kindTitle").value("Interest monthly, principal at the end of the term"));
    }

    private org.springframework.test.web.servlet.ResultActions transaction(DemoCardView card, String language,
                                                                             String operation, String extra) throws Exception {
        String body = """
                {"items": [{"name": "CARD", "value": "%s"}, {"name": "PIN", "value": "%s"},
                           {"name": "OPERATION", "value": "%s"}%s]}""".formatted(card.cardNumber(), card.pin(), operation, extra);
        return mvc.perform(post("/api/atm/transactions").header(HttpHeaders.ACCEPT_LANGUAGE, language)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }
}
