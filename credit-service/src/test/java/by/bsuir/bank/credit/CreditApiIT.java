package by.bsuir.bank.credit;

import by.bsuir.bank.common.client.ClientApi;
import by.bsuir.bank.common.client.ClientInfo;
import by.bsuir.bank.common.ledger.InMemoryLedger;
import by.bsuir.bank.credit.atm.DepositApi;
import by.bsuir.bank.credit.repository.CardRepository;
import by.bsuir.bank.credit.repository.CreditContractRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Интеграционные тесты REST сервиса кредитов: контроль данных договора, график платежей, выдача через кассу. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid};DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(TestLedgerConfig.class)
class CreditApiIT {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
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
    void cleanState() {
        cards.deleteAll();
        contracts.deleteAll();
        ledger.reset();
        ClientInfo client = new ClientInfo(1L, "Иванов Иван Иванович", "MP", "7654321", "3170590A077PB4");
        when(clients.get(1L)).thenReturn(client);
        when(clients.list()).thenReturn(List.of(client));
    }

    @Test
    @DisplayName("Справочники формы: виды кредита, валюты, следующий номер договора")
    void references() throws Exception {
        mvc.perform(get("/api/products")).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].kindTitle").value("Аннуитетные платежи"))
                .andExpect(jsonPath("$[1].kind").value("INTEREST_ONLY"));
        mvc.perform(get("/api/meta"))
                .andExpect(jsonPath("$.bankDate").value("2026-10-01"))
                .andExpect(jsonPath("$.nextNumber").value("К-000001"));
    }

    @Test
    @DisplayName("График платежей рассчитывается до заключения договора")
    void schedulePreview() throws Exception {
        mvc.perform(post("/api/credits/schedule").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": 1, \"amount\": 10000, \"termMonths\": 6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", hasSize(6)))
                .andExpect(jsonPath("$.rows[0].total").value(1753.51))
                .andExpect(jsonPath("$.totalInterest").value(521.06));
        assertThat(contracts.count()).isZero();
    }

    @Test
    @DisplayName("Договор заключается: возвращаются счета, карта и PIN-конверт")
    void createsContract() throws Exception {
        create(valid()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.contract.number").value("К-000001"))
                .andExpect(jsonPath("$.contract.debt").value(10000))
                .andExpect(jsonPath("$.card.cardNumber").isString())
                .andExpect(jsonPath("$.card.pin").isString());

        Long id = contracts.findAll().get(0).getId();
        mvc.perform(get("/api/credits/" + id))
                .andExpect(jsonPath("$.accounts", hasSize(2)))
                .andExpect(jsonPath("$.schedule.rows", hasSize(6)))
                .andExpect(jsonPath("$.operations", hasSize(1)));
        mvc.perform(get("/api/credits")).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].cardBlocked").value(false));
        mvc.perform(get("/api/meta")).andExpect(jsonPath("$.nextNumber").value("К-000002"));
    }

    @ParameterizedTest(name = "без поля {0}")
    @ValueSource(strings = {"number", "productId", "currency", "clientId", "amount", "rate", "termMonths", "startDate", "endDate"})
    @DisplayName("Все поля договора обязательны")
    void requiredFields(String field) throws Exception {
        Map<String, Object> request = valid();
        request.remove(field);

        create(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields." + field).value("Обязательное поле"));
        assertThat(contracts.count()).isZero();
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
            "number, Д-000001",
            "amount, 499.99",
            "amount, 50000.01",
            "rate, 10.00",
            "termMonths, 5",
            "termMonths, 61",
            "startDate, 2026-09-30",
            "endDate, 2027-05-01",
            "currency, USD",
    })
    @DisplayName("Условия договора сверяются с кредитной программой и банковским днём")
    void termsAreChecked(String field, String value) throws Exception {
        Map<String, Object> request = valid();
        request.put(field, value);

        create(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields." + ("currency".equals(field) ? "productId" : field)).exists());
        assertThat(contracts.count()).isZero();
        assertThat(ledger.journal()).isEmpty();
    }

    @Test
    @DisplayName("Номер договора уникален")
    void duplicateNumber() throws Exception {
        create(valid()).andExpect(status().isCreated());

        create(valid()).andExpect(status().isConflict()).andExpect(jsonPath("$.fields.number").exists());
        assertThat(contracts.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Выдача через кассу: сумма проверяется, превышение остатка отклоняется")
    void cash() throws Exception {
        create(valid()).andExpect(status().isCreated());
        String url = "/api/credits/" + contracts.findAll().get(0).getId() + "/cash";

        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 2500.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(7499.50));
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"amount\": -1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 7499.51}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    private Map<String, Object> valid() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("number", "К-000001");
        request.put("productId", 1);
        request.put("currency", "BYN");
        request.put("clientId", 1);
        request.put("amount", 10000);
        request.put("rate", 17.65);
        request.put("termMonths", 6);
        request.put("startDate", "2026-10-01");
        request.put("endDate", "2027-04-01");
        return request;
    }

    private ResultActions create(Map<String, Object> request) throws Exception {
        return mvc.perform(post("/api/credits").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)));
    }
}
