package by.bsuir.bank.credit;

import by.bsuir.bank.common.api.BankException;
import by.bsuir.bank.common.client.ClientApi;
import by.bsuir.bank.common.client.ClientInfo;
import by.bsuir.bank.common.ledger.AccountInfo;
import by.bsuir.bank.common.ledger.InMemoryLedger;
import by.bsuir.bank.common.ledger.LedgerApi;
import by.bsuir.bank.common.ledger.OperationInfo;
import by.bsuir.bank.credit.atm.DepositApi;
import by.bsuir.bank.credit.dto.CreditDetails;
import by.bsuir.bank.credit.dto.CreditIssued;
import by.bsuir.bank.credit.dto.CreditRequest;
import by.bsuir.bank.credit.dto.CreditView;
import by.bsuir.bank.credit.dto.ScheduleRow;
import by.bsuir.bank.credit.repository.CardRepository;
import by.bsuir.bank.credit.repository.CreditContractRepository;
import by.bsuir.bank.credit.service.DayCloseProcessor;
import by.bsuir.bank.credit.service.CreditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static by.bsuir.bank.common.ledger.InMemoryLedger.CAPITAL;
import static by.bsuir.bank.common.ledger.InMemoryLedger.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Интеграционные тесты кредитного процесса: сервис, база H2 и главная книга в памяти.
 * Обороты счетов сверяются со схемой проводок «Кредитная программа» из задания.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid};DB_CLOSE_DELAY=-1")
@Import(TestLedgerConfig.class)
class CreditFlowIT {

    private static final long ANNUITY = 1;          // «R-деньги mix», 17,65 %
    private static final long INTEREST_ONLY = 2;    // «R-Онлайн», 17,65 %

    @Autowired
    private CreditService credits;
    @Autowired
    private DayCloseProcessor dayClose;
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

    private String cash;
    private String fund;

    @BeforeEach
    void cleanState() {
        cards.deleteAll();
        contracts.deleteAll();
        ledger.reset();
        cash = ledger.systemAccount(LedgerApi.CASH, "BYN").number();
        fund = ledger.systemAccount(LedgerApi.FUND, "BYN").number();
        when(clients.get(anyLong())).thenAnswer(call ->
                new ClientInfo(call.getArgument(0), "Иванов Иван Иванович", "MP", "7654321", "3170590A077PB4"));
    }

    @Test
    @DisplayName("Заключение договора: два активных счёта, выделение кредита из фонда, график и карта")
    void openingContract() {
        CreditIssued issued = open("К-000001", ANNUITY, "10000.00", 6);
        CreditView credit = issued.contract();

        List<AccountInfo> accounts = ledger.accounts("К-000001");
        assertThat(accounts).extracting(AccountInfo::chartCode).containsExactly("2400", "2470");
        assertThat(accounts).allSatisfy(a -> assertThat(a.activity()).isEqualTo("ACTIVE"));
        assertThat(ledger.operations("К-000001")).extracting(OperationInfo::description)
                .containsExactly("Выделение кредита банком");

        // фонд (пассивный): дебет −10000; текущий счёт (активный): дебет +10000
        assertThat(ledger.balance(fund)).isEqualByComparingTo(CAPITAL.subtract(new BigDecimal("10000.00")));
        assertTurnover(credit.mainAccount(), "10000.00", "0.00", "10000.00");

        assertThat(credits.details(credit.id()).schedule().rows()).hasSize(6);
        assertThat(credit.debt()).isEqualByComparingTo("10000.00");
        assertThat(issued.card().cardNumber()).hasSize(16).isEqualTo(credit.cardNumber());
        assertThat(issued.card().pin()).matches("\\d{4}");
        assertThat(cards.findByNumber(credit.cardNumber()).orElseThrow().getPinHash())
                .startsWith("$2").doesNotContain(issued.card().pin());
    }

    @Test
    @DisplayName("Выдача через кассу: с текущего счёта в кассу и из кассы клиенту")
    void cashOut() {
        CreditView credit = open("К-000001", ANNUITY, "10000.00", 6).contract();

        AccountInfo after = credits.cashOut(credit.id(), new BigDecimal("4000.00"));

        assertThat(after.balance()).isEqualByComparingTo("6000.00");
        assertTurnover(credit.mainAccount(), "10000.00", "4000.00", "6000.00");
        assertTurnover(cash, "4000.00", "4000.00", "0.00");
        assertThat(ledger.operations("К-000001")).extracting(OperationInfo::description)
                .contains("Перевод кредита в кассу", "Получение кредита через кассу");

        assertThatThrownBy(() -> credits.cashOut(credit.id(), new BigDecimal("6000.01")))
                .isInstanceOfSatisfying(BankException.class, e -> assertThat(e.getCode()).isEqualTo("INSUFFICIENT_FUNDS"));
        assertThat(ledger.balance(credit.mainAccount())).isEqualByComparingTo("6000.00");
    }

    @Test
    @DisplayName("Аннуитет: в дату платежа проводятся проценты и часть долга, остальные дни счета не меняются")
    void annuityPaymentOnDueDate() {
        CreditView credit = open("К-000001", ANNUITY, "10000.00", 6).contract();
        credits.cashOut(credit.id(), new BigDecimal("10000.00"));

        assertThat(closeDays(30)).isEmpty();                                   // 31.10 — платежей ещё нет
        List<String> events = closeDays(1);                                    // 01.11 — первый платёж
        assertThat(events).containsExactly("2026-11-01: К-000001 — платёж №1: проценты 147.08, основной долг 1606.43 BYN");

        // процентный счёт: дебет +147,08 (перевод из кассы) и кредит −147,08 (начисление банком)
        assertTurnover(credit.interestAccount(), "147.08", "147.08", "0.00");
        // текущий счёт: +10000 выдача, −10000 снятие, +1606,43 погашение из кассы, −1606,43 возврат в фонд
        assertTurnover(credit.mainAccount(), "11606.43", "11606.43", "0.00");
        // касса: транзит выданного кредита и принятого платежа 1753,51
        assertTurnover(cash, "11753.51", "11753.51", "0.00");
        // фонд: −10000 выдача, +147,08 проценты, +1606,43 возврат долга
        assertThat(ledger.balance(fund)).isEqualByComparingTo(CAPITAL.subtract(new BigDecimal("8246.49")));

        CreditDetails details = credits.details(credit.id());
        assertThat(details.contract().debt()).isEqualByComparingTo("8393.57");
        assertThat(details.contract().interestPaid()).isEqualByComparingTo("147.08");
        assertThat(details.schedule().rows().get(0).paidOn()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(details.schedule().rows().get(1).paidOn()).isNull();
    }

    @Test
    @DisplayName("После последнего платежа договор закрывается, банк получает сумму кредита и проценты")
    void contractIsClosedAfterLastPayment() {
        CreditView credit = open("К-000001", ANNUITY, "10000.00", 6).contract();
        credits.cashOut(credit.id(), new BigDecimal("10000.00"));

        List<String> events = closeDays(182);                                  // до 01.04.2027 включительно

        assertThat(events).hasSize(7).last().isEqualTo("2027-04-01: К-000001 — кредит погашен, договор закрыт");
        CreditView closed = credits.details(credit.id()).contract();
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.debt()).isEqualByComparingTo("0");
        assertThat(closed.interestPaid()).isEqualByComparingTo("521.06");
        assertThat(ledger.balance(fund)).isEqualByComparingTo(CAPITAL.add(new BigDecimal("521.06")));
        assertThat(ledger.balance(credit.mainAccount())).isEqualByComparingTo("0");
        assertThat(ledger.balance(credit.interestAccount())).isEqualByComparingTo("0");
        assertThat(ledger.operations("К-000001")).extracting(OperationInfo::description).contains("Окончание кредита");
        assertThat(closeDays(40)).isEmpty();
    }

    @Test
    @DisplayName("Кредит с ежемесячной уплатой процентов: долг возвращается последним платежом")
    void interestOnlyCredit() {
        CreditView credit = open("К-000002", INTEREST_ONLY, "6000.00", 4).contract();

        closeDays(92);                                                         // три платежа: только проценты
        assertThat(credits.details(credit.id()).contract().debt()).isEqualByComparingTo("6000.00");
        assertThat(credits.details(credit.id()).contract().interestPaid()).isEqualByComparingTo("264.75");
        assertThat(ledger.operations("К-000002")).extracting(OperationInfo::description)
                .doesNotContain("Погашение долга за кредит из кассы");

        closeDays(31);                                                         // 01.02.2027 — проценты и весь долг
        CreditView closed = credits.details(credit.id()).contract();
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.interestPaid()).isEqualByComparingTo("353.00");
        assertThat(ledger.balance(fund)).isEqualByComparingTo(CAPITAL.add(new BigDecimal("353.00")));
        // деньги клиент не снимал — выданная сумма осталась на его текущем счёте
        assertThat(ledger.balance(credit.mainAccount())).isEqualByComparingTo("6000.00");
    }

    @Test
    @DisplayName("Повторная обработка дня не проводит платёж второй раз, пропущенные дни навёрстываются")
    void processingIsIdempotentAndCatchesUp() {
        CreditView credit = open("К-000001", ANNUITY, "10000.00", 6).contract();

        assertThat(dayClose.process(START.plusMonths(2))).hasSize(2);          // сразу два просроченных платежа
        assertThat(dayClose.process(START.plusMonths(2))).isEmpty();

        assertThat(credits.details(credit.id()).contract().debt()).isEqualByComparingTo("6763.52");
        assertThat(ledger.debit(credit.interestAccount())).isEqualByComparingTo("270.54");
    }

    @Test
    @DisplayName("Сумма сверх лимита программы и заём больше фонда банка отклоняются")
    void limits() {
        assertThatThrownBy(() -> open("К-000001", ANNUITY, "50000.01", 6))
                .isInstanceOfSatisfying(BankException.class, e -> assertThat(e.getFields()).containsKey("amount"));
        assertThatThrownBy(() -> open("К-000001", INTEREST_ONLY, "6000.00", 37))
                .isInstanceOfSatisfying(BankException.class, e -> assertThat(e.getFields()).containsKey("termMonths"));
        assertThat(contracts.count()).isZero();
        assertThat(ledger.journal()).isEmpty();
    }

    private CreditIssued open(String number, long productId, String amount, int months) {
        return credits.open(new CreditRequest(number, productId, "BYN", 1L, new BigDecimal(amount),
                new BigDecimal("17.65"), months, START, START.plusMonths(months)));
    }

    /** Закрытие дней подряд — то, что делает сервис «Счета», вызывая участников. */
    private List<String> closeDays(int days) {
        List<String> events = new java.util.ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate next = ledger.bankDay().date().plusDays(1);
            events.addAll(dayClose.process(next));
            ledger.setDate(next);
        }
        return events;
    }

    private void assertTurnover(String account, String debit, String credit, String balance) {
        assertThat(ledger.debit(account)).as("дебет %s", account).isEqualByComparingTo(debit);
        assertThat(ledger.credit(account)).as("кредит %s", account).isEqualByComparingTo(credit);
        assertThat(ledger.balance(account)).as("сальдо %s", account).isEqualByComparingTo(balance);
    }
}
