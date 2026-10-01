package by.bsuir.bank.deposit;

import by.bsuir.bank.common.api.BankException;
import by.bsuir.bank.common.client.ClientApi;
import by.bsuir.bank.common.client.ClientInfo;
import by.bsuir.bank.common.ledger.AccountInfo;
import by.bsuir.bank.common.ledger.InMemoryLedger;
import by.bsuir.bank.common.ledger.LedgerApi;
import by.bsuir.bank.common.ledger.OperationInfo;
import by.bsuir.bank.deposit.dto.DepositRequest;
import by.bsuir.bank.deposit.dto.DepositView;
import by.bsuir.bank.deposit.repository.DepositContractRepository;
import by.bsuir.bank.deposit.service.DayCloseProcessor;
import by.bsuir.bank.deposit.service.DepositService;
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

import static by.bsuir.bank.common.ledger.InMemoryLedger.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Интеграционные тесты депозитного процесса: сервис, база H2 и главная книга в памяти.
 * Обороты счетов сверяются со схемой проводок «Депозитная программа» из задания.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid};DB_CLOSE_DELAY=-1")
@Import(TestLedgerConfig.class)
class DepositFlowIT {

    private static final long REVOCABLE_BYN = 1;      // «Свободное накопление», 7 %, проценты ежемесячно
    private static final long IRREVOCABLE_BYN = 2;    // «Online-решение», 12,9 %, проценты в конце срока

    @Autowired
    private DepositService deposits;
    @Autowired
    private DayCloseProcessor dayClose;
    @Autowired
    private DepositContractRepository contracts;
    @Autowired
    private InMemoryLedger ledger;
    @MockitoBean
    private ClientApi clients;

    private String cash;
    private String fund;

    @BeforeEach
    void cleanState() {
        contracts.deleteAll();
        ledger.reset();
        cash = ledger.systemAccount(LedgerApi.CASH, "BYN").number();
        fund = ledger.systemAccount(LedgerApi.FUND, "BYN").number();
        when(clients.get(anyLong())).thenAnswer(call ->
                new ClientInfo(call.getArgument(0), "Иванов Иван Иванович", "MP", "7654321", "3170590A077PB4"));
    }

    @Test
    @DisplayName("Заключение договора: два счёта по плану счетов и три проводки приёма вклада")
    void openingContract() {
        DepositView deposit = open("Д-000001", REVOCABLE_BYN, "1000.00", 3);

        List<AccountInfo> accounts = ledger.accounts("Д-000001");
        assertThat(accounts).extracting(AccountInfo::chartCode).containsExactly("3404", "3470");
        assertThat(accounts).allSatisfy(a -> {
            assertThat(a.activity()).isEqualTo("PASSIVE");
            assertThat(a.name()).isEqualTo("Иванов Иван Иванович");
        });
        assertThat(ledger.operations("Д-000001")).extracting(OperationInfo::description).containsExactly(
                "Внесение денег в кассу", "Перевод денег с кассы на текущий счёт", "Использование денег банком");

        // касса: дебет +1000 и кредит −1000; счёт вклада: кредит +1000 и дебет −1000; фонд: кредит +1000
        assertTurnover(cash, "1000.00", "1000.00", "0.00");
        assertTurnover(deposit.mainAccount(), "1000.00", "1000.00", "0.00");
        assertThat(ledger.balance(fund)).isEqualByComparingTo(InMemoryLedger.CAPITAL.add(new BigDecimal("1000.00")));
        assertThat(deposit.status()).isEqualTo("ACTIVE");
        assertThat(deposit.endDate()).isEqualTo(START.plusMonths(3));
    }

    @Test
    @DisplayName("Отзывный вклад: проценты начисляются ежедневно и выплачиваются раз в месяц")
    void revocableDepositPaysInterestMonthly() {
        DepositView deposit = open("Д-000001", REVOCABLE_BYN, "1000.00", 3);

        closeDays(30);                                           // 31.10: начислено за 30 дней, выплаты ещё нет
        assertTurnover(deposit.interestAccount(), "0.00", "5.75", "5.75");
        assertThat(view(deposit).paid()).isEqualByComparingTo("0");

        closeDays(1);                                            // 01.11: прошёл месяц — выплата 5,95
        assertTurnover(deposit.interestAccount(), "5.95", "5.95", "0.00");
        assertTurnover(cash, "1005.95", "1005.95", "0.00");
        assertThat(view(deposit).accrued()).isEqualByComparingTo("5.95");
        assertThat(view(deposit).paid()).isEqualByComparingTo("5.95");
        assertThat(view(deposit).status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Срочный вклад: проценты выплачиваются в конце срока вместе с возвратом суммы")
    void irrevocableDepositPaysAtMaturity() {
        DepositView deposit = open("Д-000002", IRREVOCABLE_BYN, "5000.00", 3);

        closeDays(91);                                           // 31.12: до окончания срока один день
        assertThat(view(deposit).paid()).isEqualByComparingTo("0");
        assertThat(ledger.balance(deposit.interestAccount())).isEqualByComparingTo("160.81");

        List<String> events = closeDays(1);                      // 01.01.2027: окончание договора, 92 дня
        assertThat(events).anyMatch(e -> e.contains("срок договора истёк"));
        DepositView closed = view(deposit);
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.closedOn()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(closed.accrued()).isEqualByComparingTo("162.58");
        assertThat(closed.paid()).isEqualByComparingTo("162.58");

        // все счета клиента обнулились, касса транзитом пропустила вклад и проценты, фонд потратил проценты
        assertTurnover(deposit.mainAccount(), "10000.00", "10000.00", "0.00");
        assertTurnover(deposit.interestAccount(), "162.58", "162.58", "0.00");
        assertTurnover(cash, "10162.58", "10162.58", "0.00");
        assertThat(ledger.balance(fund)).isEqualByComparingTo(InMemoryLedger.CAPITAL.subtract(new BigDecimal("162.58")));
        assertThat(ledger.operations("Д-000002")).extracting(OperationInfo::description).contains(
                "Перевод процентов в кассу", "Вывод процентов из кассы",
                "Окончание депозита", "Перевод депозита в кассу", "Вывод денег из кассы");

        assertThat(closeDays(5)).isEmpty();                      // закрытый договор больше не обрабатывается
    }

    @Test
    @DisplayName("Повторная обработка того же дня не начисляет проценты второй раз")
    void dayProcessingIsIdempotent() {
        DepositView deposit = open("Д-000001", REVOCABLE_BYN, "1000.00", 3);
        LocalDate date = START.plusDays(10);

        dayClose.process(date);
        BigDecimal accrued = ledger.credit(deposit.interestAccount());
        assertThat(dayClose.process(date)).isEmpty();

        assertThat(accrued).isEqualByComparingTo("1.92");
        assertThat(ledger.credit(deposit.interestAccount())).isEqualByComparingTo(accrued);
    }

    @Test
    @DisplayName("Пропущенные дни навёрстываются при следующей обработке")
    void skippedDaysAreCaughtUp() {
        DepositView deposit = open("Д-000001", REVOCABLE_BYN, "1000.00", 3);

        dayClose.process(START.plusDays(45));                    // сервис «проспал» 44 дня

        assertThat(view(deposit).accrued()).isEqualByComparingTo("8.63");
        assertThat(view(deposit).paid()).isEqualByComparingTo("8.63");
    }

    @Test
    @DisplayName("Досрочный отзыв: отзывный вклад возвращается с процентами, безотзывный — отказ")
    void earlyWithdrawal() {
        DepositView revocable = open("Д-000001", REVOCABLE_BYN, "1000.00", 6);
        DepositView irrevocable = open("Д-000002", IRREVOCABLE_BYN, "5000.00", 6);
        closeDays(10);

        DepositView closed = deposits.withdraw(revocable.id());
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.paid()).isEqualByComparingTo("1.92");
        assertThat(ledger.balance(revocable.mainAccount())).isEqualByComparingTo("0");
        assertThat(ledger.balance(revocable.interestAccount())).isEqualByComparingTo("0");

        assertThatThrownBy(() -> deposits.withdraw(irrevocable.id()))
                .isInstanceOfSatisfying(BankException.class, e -> assertThat(e.getCode()).isEqualTo("IRREVOCABLE"));
        assertThatThrownBy(() -> deposits.withdraw(revocable.id()))
                .isInstanceOfSatisfying(BankException.class, e -> assertThat(e.getCode()).isEqualTo("CONTRACT_CLOSED"));
        assertThat(view(irrevocable).status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("При двух активных депозитных программах в отчёте не менее шести счетов")
    void twoProgramsGiveSixAccounts() {
        DepositView first = open("Д-000001", REVOCABLE_BYN, "1000.00", 3);
        DepositView second = open("Д-000002", IRREVOCABLE_BYN, "5000.00", 3);

        assertThat(List.of(cash, fund, first.mainAccount(), first.interestAccount(),
                second.mainAccount(), second.interestAccount())).doesNotHaveDuplicates().hasSize(6);
        assertThat(ledger.accounts("Д-000002")).extracting(AccountInfo::chartCode).containsExactly("3414", "3471");
    }

    private DepositView open(String number, long productId, String amount, int months) {
        return deposits.open(new DepositRequest(number, productId, "BYN", 1L, new BigDecimal(amount),
                productId == REVOCABLE_BYN ? new BigDecimal("7.00") : new BigDecimal("12.90"),
                months, START, START.plusMonths(months)));
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

    private DepositView view(DepositView deposit) {
        return deposits.details(deposit.id()).contract();
    }

    private void assertTurnover(String account, String debit, String credit, String balance) {
        assertThat(ledger.debit(account)).as("дебет %s", account).isEqualByComparingTo(debit);
        assertThat(ledger.credit(account)).as("кредит %s", account).isEqualByComparingTo(credit);
        assertThat(ledger.balance(account)).as("сальдо %s", account).isEqualByComparingTo(balance);
    }
}
