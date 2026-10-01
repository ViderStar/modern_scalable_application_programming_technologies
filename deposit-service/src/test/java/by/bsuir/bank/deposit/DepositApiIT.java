package by.bsuir.bank.deposit;

import by.bsuir.bank.common.client.ClientApi;
import by.bsuir.bank.common.client.ClientInfo;
import by.bsuir.bank.common.ledger.InMemoryLedger;
import by.bsuir.bank.deposit.repository.DepositContractRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Интеграционные тесты REST сервиса депозитов: контроль корректности данных договора и ошибки смежных сервисов. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid};DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(TestLedgerConfig.class)
class DepositApiIT {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private DepositContractRepository contracts;
    @MockitoSpyBean
    private InMemoryLedger ledger;
    @MockitoBean
    private ClientApi clients;

    @BeforeEach
    void cleanState() {
        contracts.deleteAll();
        ledger.reset();
        ClientInfo client = new ClientInfo(1L, "Иванов Иван Иванович", "MP", "7654321", "3170590A077PB4");
        when(clients.get(1L)).thenReturn(client);
        when(clients.list()).thenReturn(List.of(client));
    }

    @Test
    @DisplayName("Справочники формы: виды депозита, валюты, клиенты, следующий номер договора")
    void references() throws Exception {
        mvc.perform(get("/api/products")).andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].kindTitle").value("Отзывный, до востребования"));
        mvc.perform(get("/api/clients")).andExpect(jsonPath("$[0].fullName").value("Иванов Иван Иванович"));
        mvc.perform(get("/api/meta"))
                .andExpect(jsonPath("$.bankDate").value("2026-10-01"))
                .andExpect(jsonPath("$.currencies", hasSize(3)))
                .andExpect(jsonPath("$.nextNumber").value("Д-000001"));
    }

    @Test
    @DisplayName("Договор заключается и появляется в списке, номер следующего договора увеличивается")
    void createsContract() throws Exception {
        create(valid()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("Д-000001"))
                .andExpect(jsonPath("$.clientName").value("Иванов Иван Иванович"))
                .andExpect(jsonPath("$.mainAccount").isString())
                .andExpect(jsonPath("$.interestAccount").isString());

        mvc.perform(get("/api/deposits")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/deposits/" + contracts.findAll().get(0).getId()))
                .andExpect(jsonPath("$.accounts", hasSize(2)))
                .andExpect(jsonPath("$.operations", hasSize(3)));
        mvc.perform(get("/api/meta")).andExpect(jsonPath("$.nextNumber").value("Д-000002"));
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
            "number, 000001",
            "number, D-1",
            "amount, 99.99",
            "amount, -5",
            "amount, 100.001",
            "rate, 8.50",
            "termMonths, 0",
            "termMonths, 13",
            "startDate, 2026-10-02",
            "endDate, 2027-01-02",
            "currency, USD",
            "productId, 77",
    })
    @DisplayName("Условия договора сверяются с депозитной программой и банковским днём")
    void termsAreChecked(String field, String value) throws Exception {
        Map<String, Object> request = valid();
        request.put(field, value);

        String errorField = "currency".equals(field) ? "productId" : field;
        create(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields." + errorField).exists());
        assertThat(contracts.count()).isZero();
        assertThat(ledger.journal()).isEmpty();
    }

    @Test
    @DisplayName("Номер договора уникален")
    void duplicateNumber() throws Exception {
        create(valid()).andExpect(status().isCreated());

        create(valid()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.fields.number").exists());
        assertThat(contracts.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Клиент должен существовать в модуле «Клиенты»")
    void unknownClient() throws Exception {
        when(clients.get(99L)).thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));
        Map<String, Object> request = valid();
        request.put("clientId", 99);

        create(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.clientId").value("Клиент не найден в модуле «Клиенты»"));
        assertThat(ledger.journal()).isEmpty();
    }

    @Test
    @DisplayName("Сервис «Счета» недоступен — ответ 503, договор не создаётся")
    void ledgerIsDown() throws Exception {
        doThrow(new ResourceAccessException("Connection refused")).when(ledger).bankDay();

        create(valid()).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
        assertThat(contracts.count()).isZero();
    }

    @Test
    @DisplayName("Внутренний REST закрытия дня возвращает протокол начислений")
    void dayCloseEndpoint() throws Exception {
        create(valid()).andExpect(status().isCreated());

        mvc.perform(post("/api/internal/day-close").contentType(MediaType.APPLICATION_JSON).content("{\"date\": \"2026-10-11\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0]").value("2026-10-11: Д-000001 — начислены проценты 1.92 BYN"));
    }

    @Test
    @DisplayName("Безотзывный вклад нельзя отозвать через REST")
    void irrevocableWithdrawIsRejected() throws Exception {
        Map<String, Object> request = valid();
        request.put("productId", 2);
        request.put("amount", 500);
        request.put("rate", 12.90);
        create(request).andExpect(status().isCreated());

        mvc.perform(post("/api/deposits/" + contracts.findAll().get(0).getId() + "/withdraw"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IRREVOCABLE"));
    }

    private Map<String, Object> valid() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("number", "Д-000001");
        request.put("productId", 1);
        request.put("currency", "BYN");
        request.put("clientId", 1);
        request.put("amount", 1000);
        request.put("rate", 7.00);
        request.put("termMonths", 3);
        request.put("startDate", "2026-10-01");
        request.put("endDate", "2027-01-01");
        return request;
    }

    private ResultActions create(Map<String, Object> request) throws Exception {
        return mvc.perform(post("/api/deposits").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)));
    }
}
