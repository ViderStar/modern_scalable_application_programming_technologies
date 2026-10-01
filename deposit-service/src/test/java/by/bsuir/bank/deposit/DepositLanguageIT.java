package by.bsuir.bank.deposit;

import by.bsuir.bank.common.client.ClientApi;
import by.bsuir.bank.common.client.ClientInfo;
import by.bsuir.bank.common.ledger.InMemoryLedger;
import by.bsuir.bank.deposit.repository.DepositContractRepository;
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
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Интеграционные тесты локализации сервиса депозитов: справочник программ, проверки договора, протокол дня. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid};DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(TestLedgerConfig.class)
class DepositLanguageIT {

    private static final String CONTRACT = """
            {"number": "%s", "productId": 1, "currency": "BYN", "clientId": 1, "amount": %s, "rate": 7.00,
             "termMonths": 3, "startDate": "2026-10-01", "endDate": "2027-01-01"}""";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private DepositContractRepository contracts;
    @Autowired
    private InMemoryLedger ledger;
    @MockitoBean
    private ClientApi clients;

    @BeforeEach
    void cleanState() {
        contracts.deleteAll();
        ledger.reset();
        when(clients.get(1L)).thenReturn(new ClientInfo(1L, "Иванов Иван Иванович", "MP", "7654321", "3170590A077PB4"));
    }

    @Test
    @DisplayName("Виды депозита: название, описание и вид вклада на языке запроса")
    void products() throws Exception {
        mvc.perform(get("/api/products").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(jsonPath("$[0].name").value("Free Savings"))
                .andExpect(jsonPath("$[0].kindTitle").value("Revocable, on demand"))
                .andExpect(jsonPath("$[0].description").value("Revocable demand deposit, interest is paid monthly"))
                .andExpect(jsonPath("$[0].nameEn").doesNotExist());
        mvc.perform(get("/api/products").header(HttpHeaders.ACCEPT_LANGUAGE, "be"))
                .andExpect(jsonPath("$[1].name").value("Online-рашэнне"))
                .andExpect(jsonPath("$[1].kindTitle").value("Тэрміновы безадзыўны"));
    }

    @Test
    @DisplayName("Ошибки формы договора на английском и белорусском")
    void validation() throws Exception {
        create("en", "000001", "50")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please check the highlighted fields"))
                .andExpect(jsonPath("$.fields.number").value("Contract number format: Д-000001"));
        create("en", "Д-000001", "50")
                .andExpect(jsonPath("$.fields.amount").value("Minimum deposit amount: 100.00 BYN"));
        create("be", "Д-000001", "50")
                .andExpect(jsonPath("$.fields.amount").value("Мінімальная сума ўкладу — 100.00 BYN"));
    }

    @Test
    @DisplayName("Протокол закрытия дня и проводки договора читаются на языке запроса")
    void eventsAndPostings() throws Exception {
        create("ru", "Д-000001", "1000").andExpect(status().isCreated());
        long id = contracts.findAll().get(0).getId();

        mvc.perform(post("/api/internal/day-close").header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\": \"2026-10-11\"}"))
                .andExpect(jsonPath("$[0]").value("11.10.2026: Д-000001 — interest accrued 1.92 BYN"));

        mvc.perform(get("/api/deposits/" + id).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(jsonPath("$.contract.productName").value("Free Savings"))
                .andExpect(jsonPath("$.operations[0].description").value("Cash paid in at the cash desk"))
                .andExpect(jsonPath("$.operations[3].description").value("Deposit interest accrued"));
        mvc.perform(get("/api/deposits/" + id).header(HttpHeaders.ACCEPT_LANGUAGE, "be"))
                .andExpect(jsonPath("$.contract.kindTitle").value("Адзыўны, да запатрабавання"))
                .andExpect(jsonPath("$.operations[0].description").value("Унясенне грошай у касу"));
    }

    private ResultActions create(String language, String number, String amount) throws Exception {
        return mvc.perform(post("/api/deposits").header(HttpHeaders.ACCEPT_LANGUAGE, language)
                .contentType(MediaType.APPLICATION_JSON).content(CONTRACT.formatted(number, amount)));
    }
}
