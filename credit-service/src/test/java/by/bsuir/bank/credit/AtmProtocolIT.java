package by.bsuir.bank.credit;

import by.bsuir.bank.common.client.ClientApi;
import by.bsuir.bank.common.client.ClientInfo;
import by.bsuir.bank.common.ledger.AccountInfo;
import by.bsuir.bank.common.ledger.InMemoryLedger;
import by.bsuir.bank.common.ledger.LedgerApi;
import by.bsuir.bank.common.ledger.OperationInfo;
import by.bsuir.bank.credit.atm.DepositApi;
import by.bsuir.bank.credit.atm.DepositApi.DepositInfo;
import by.bsuir.bank.credit.dto.CreditIssued;
import by.bsuir.bank.credit.dto.CreditRequest;
import by.bsuir.bank.credit.repository.CardRepository;
import by.bsuir.bank.credit.repository.CreditContractRepository;
import by.bsuir.bank.credit.repository.MobilePaymentRepository;
import by.bsuir.bank.credit.service.CreditService;
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
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static by.bsuir.bank.common.ledger.InMemoryLedger.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Интеграционные тесты банковской стороны протокола «банк — банкомат». */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:${random.uuid};DB_CLOSE_DELAY=-1",
        "bank.demo.enabled=false"})
@AutoConfigureMockMvc
@Import(TestLedgerConfig.class)
class AtmProtocolIT {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private CreditService credits;
    @Autowired
    private CreditContractRepository contracts;
    @Autowired
    private CardRepository cards;
    @Autowired
    private MobilePaymentRepository payments;
    @Autowired
    private InMemoryLedger ledger;
    @MockitoBean
    private ClientApi clients;
    @MockitoBean
    private DepositApi deposits;

    private CreditIssued credit;
    private String card;
    private String pin;

    @BeforeEach
    void issueCreditWithCard() {
        payments.deleteAll();
        cards.deleteAll();
        contracts.deleteAll();
        ledger.reset();
        when(clients.get(anyLong())).thenAnswer(call ->
                new ClientInfo(call.getArgument(0), "Иванов Иван Иванович", "MP", "7654321", "3170590A077PB4"));
        credit = credits.open(new CreditRequest("К-000001", 1L, "BYN", 1L, new BigDecimal("1000.00"),
                new BigDecimal("17.65"), 6, START, START.plusMonths(6)));
        card = credit.card().cardNumber();
        pin = credit.card().pin();
    }

    @Test
    @DisplayName("Авторизация: верный PIN-код принимается")
    void authorize() throws Exception {
        send("AUTHORIZE")
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.holder").value("Иванов Иван Иванович"));
    }

    @Test
    @DisplayName("Три неверных PIN-кода подряд блокируют карту; перевыпуск PIN-кода снимает блокировку")
    void wrongPinThreeTimes() throws Exception {
        send(card, wrongPin(), "AUTHORIZE").andExpect(jsonPath("$.code").value("WRONG_PIN"))
                .andExpect(jsonPath("$.data.attemptsLeft").value(2));
        send(card, wrongPin(), "AUTHORIZE").andExpect(jsonPath("$.data.attemptsLeft").value(1));
        send(card, wrongPin(), "AUTHORIZE").andExpect(jsonPath("$.code").value("CARD_BLOCKED"));
        send("AUTHORIZE").andExpect(jsonPath("$.code").value("CARD_BLOCKED"));       // верный PIN уже не помогает

        String response = mvc.perform(post("/api/credits/" + credit.contract().id() + "/pin"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        pin = json.readTree(response).get("pin").asText();
        send("AUTHORIZE").andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    @DisplayName("Счётчик неверных попыток сбрасывается после верного PIN-кода")
    void attemptsAreResetAfterSuccess() throws Exception {
        send(card, wrongPin(), "AUTHORIZE").andExpect(jsonPath("$.data.attemptsLeft").value(2));
        send("AUTHORIZE").andExpect(jsonPath("$.status").value("OK"));
        send(card, wrongPin(), "AUTHORIZE").andExpect(jsonPath("$.data.attemptsLeft").value(2));
    }

    @Test
    @DisplayName("Неизвестная карта и неполная транзакция отклоняются")
    void unknownCardAndIncompleteTransaction() throws Exception {
        send("9112380000009993", pin, "AUTHORIZE").andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"));
        transaction(List.of(item("CARD", card), item("OPERATION", "BALANCE")))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        send("TRANSFER").andExpect(jsonPath("$.code").value("UNKNOWN_OPERATION"));
        mvc.perform(post("/api/atm/transactions").contentType(MediaType.APPLICATION_JSON).content("{\"items\": []}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Просмотр остатка кредитного счёта")
    void balance() throws Exception {
        send("BALANCE")
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.balance").value(1000.00))
                .andExpect(jsonPath("$.data.currency").value("BYN"))
                .andExpect(jsonPath("$.data.account").value(credit.contract().mainAccount()))
                .andExpect(jsonPath("$.data.bankDate").value("2026-10-01"));
    }

    @Test
    @DisplayName("Снятие наличных: проводки через кассу, остаток уменьшается")
    void withdraw() throws Exception {
        send("WITHDRAW", item("AMOUNT", "300"))
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.message").value("Заберите деньги"))
                .andExpect(jsonPath("$.data.amount").value(300))
                .andExpect(jsonPath("$.data.balance").value(700.00));

        String cash = ledger.systemAccount(LedgerApi.CASH, "BYN").number();
        assertThat(ledger.balance(credit.contract().mainAccount())).isEqualByComparingTo("700.00");
        assertThat(ledger.debit(cash)).isEqualByComparingTo("300.00");
        assertThat(ledger.credit(cash)).isEqualByComparingTo("300.00");
        assertThat(ledger.operations("К-000001")).extracting(OperationInfo::description)
                .containsExactly("Выделение кредита банком", "Перевод кредита в кассу", "Получение кредита через кассу");
    }

    @Test
    @DisplayName("Снятие суммы больше остатка — сообщение об ошибке, счёт не меняется")
    void withdrawMoreThanAvailable() throws Exception {
        send("WITHDRAW", item("AMOUNT", "1001"))
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertThat(ledger.balance(credit.contract().mainAccount())).isEqualByComparingTo("1000.00");
        // отказ не ломает последующие операции с той же картой
        send("WITHDRAW", item("AMOUNT", "1000")).andExpect(jsonPath("$.status").value("OK"));
    }

    @ParameterizedTest(name = "сумма «{0}»")
    @ValueSource(strings = {"0", "-5", "abc", "10.5", "", "1e3", "12345678901"})
    @DisplayName("Сумма снятия — целое положительное число")
    void withdrawAmountIsValidated(String amount) throws Exception {
        send("WITHDRAW", item("AMOUNT", amount)).andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));

        assertThat(ledger.balance(credit.contract().mainAccount())).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("Платёж за мобильную связь: деньги уходят на расчётный счёт оператора")
    void payment() throws Exception {
        send("PAYMENT", item("OPERATOR", "MTS"), item("PHONE", "0297654321"), item("AMOUNT", "25.50"))
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.operator").value("МТС"))
                .andExpect(jsonPath("$.data.phone").value("0297654321"))
                .andExpect(jsonPath("$.data.balance").value(974.50))
                .andExpect(jsonPath("$.data.reference").isString());

        AccountInfo operator = ledger.accounts("OPERATOR-MTS-BYN").get(0);
        assertThat(operator.chartCode()).isEqualTo("3012");
        assertThat(operator.balance()).isEqualByComparingTo("25.50");
        assertThat(ledger.balance(credit.contract().mainAccount())).isEqualByComparingTo("974.50");
        assertThat(payments.findAll()).singleElement().satisfies(p -> {
            assertThat(p.getPhone()).isEqualTo("0297654321");
            assertThat(p.getAmount()).isEqualByComparingTo("25.50");
        });

        // второй платёж тому же оператору идёт на тот же счёт
        send("PAYMENT", item("OPERATOR", "MTS"), item("PHONE", "0331112233"), item("AMOUNT", "10"))
                .andExpect(jsonPath("$.status").value("OK"));
        assertThat(ledger.accounts("OPERATOR-MTS-BYN")).hasSize(1);
        assertThat(ledger.balance(operator.number())).isEqualByComparingTo("35.50");
    }

    @ParameterizedTest(name = "{0} / {1} / {2} -> {3}")
    @CsvSource({
            "BEELINE, 0297654321, 10, UNKNOWN_OPERATOR",
            "A1, 297654321, 10, INVALID_PHONE",
            "A1, 0177654321, 10, INVALID_PHONE",
            "A1, 02976543210, 10, INVALID_PHONE",
            "A1, 0297654321, 0, INVALID_AMOUNT",
            "A1, 0297654321, 10.555, INVALID_AMOUNT",
            "A1, 0297654321, 1000.01, INSUFFICIENT_FUNDS",
    })
    @DisplayName("Платёж проверяется: оператор, номер из 10 цифр, сумма, достаточность средств")
    void paymentIsValidated(String operator, String phone, String amount, String code) throws Exception {
        send("PAYMENT", item("OPERATOR", operator), item("PHONE", phone), item("AMOUNT", amount))
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.code").value(code));

        assertThat(ledger.balance(credit.contract().mainAccount())).isEqualByComparingTo("1000.00");
        assertThat(payments.count()).isZero();
    }

    @Test
    @DisplayName("Остаток депозитных счетов запрашивается у сервиса депозитов")
    void depositBalance() throws Exception {
        when(deposits.byClient(1L)).thenReturn(List.of(
                new DepositInfo("Д-000001", "Свободное накопление", "BYN", new BigDecimal("1500.00"), "ACTIVE"),
                new DepositInfo("Д-000002", "Online-решение", "BYN", new BigDecimal("900.00"), "CLOSED")));

        send("DEPOSIT_BALANCE")
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.deposits", hasSize(1)))
                .andExpect(jsonPath("$.data.deposits[0].number").value("Д-000001"))
                .andExpect(jsonPath("$.data.deposits[0].amount").value(1500.00));
    }

    @Test
    @DisplayName("Сервис депозитов недоступен — банкомат получает отказ, а не ошибку сервера")
    void depositServiceIsDown() throws Exception {
        when(deposits.byClient(1L)).thenThrow(new ResourceAccessException("Connection refused"));

        send("DEPOSIT_BALANCE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Справочник операторов связи")
    void operators() throws Exception {
        mvc.perform(get("/api/atm/operators")).andExpect(jsonPath("$", hasSize(3)));
    }

    // ---------- сборка транзакции банкомата ----------

    private ResultActions send(String operation, Map<String, String>... extra) throws Exception {
        return send(card, pin, operation, extra);
    }

    @SafeVarargs
    private ResultActions send(String cardNumber, String pinCode, String operation, Map<String, String>... extra) throws Exception {
        List<Map<String, String>> items = new ArrayList<>(List.of(
                item("CARD", cardNumber), item("PIN", pinCode), item("OPERATION", operation)));
        items.addAll(List.of(extra));
        return transaction(items);
    }

    private ResultActions transaction(List<Map<String, String>> items) throws Exception {
        return mvc.perform(post("/api/atm/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("items", items))))
                .andExpect(status().isOk());
    }

    private static Map<String, String> item(String name, String value) {
        return Map.of("name", name, "value", value);
    }

    private String wrongPin() {
        return "0000".equals(pin) ? "1111" : "0000";
    }
}
