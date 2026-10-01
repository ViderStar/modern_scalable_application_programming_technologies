package by.bsuir.bank.account;

import by.bsuir.bank.account.domain.AccountNumbers;
import by.bsuir.bank.account.domain.Side;
import by.bsuir.bank.account.dto.AccountView;
import by.bsuir.bank.account.dto.BatchRequest;
import by.bsuir.bank.account.dto.EntryRequest;
import by.bsuir.bank.account.dto.OpenAccountRequest;
import by.bsuir.bank.account.dto.OperationRequest;
import by.bsuir.bank.common.api.BankException;
import by.bsuir.bank.account.service.LedgerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Интеграционные тесты главной книги на H2: счета, проводки, атомарность и идемпотентность. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:${random.uuid};DB_CLOSE_DELAY=-1",
        "bank.fund.capital.BYN=1000000"})
class LedgerServiceIT {

    @Autowired
    private LedgerService ledger;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("При старте открыты касса и фонд развития со стартовым капиталом")
    void systemAccountsAreOpenedOnStartup() {
        AccountView cash = ledger.system("1010", "BYN");
        AccountView fund = ledger.system("7327", "BYN");

        assertThat(cash.activity()).isEqualTo("ACTIVE");
        assertThat(fund.activity()).isEqualTo("PASSIVE");
        assertThat(fund.credit()).isEqualByComparingTo("1000000.00");
        assertThat(fund.balance()).isEqualByComparingTo("1000000.00");
        assertThat(ledger.report().accounts()).hasSizeGreaterThanOrEqualTo(6);
    }

    @Test
    @DisplayName("Номер нового счёта: балансовый счёт, код клиента, порядковый номер, ключ")
    void opensClientAccounts() {
        AccountView main = ledger.open(new OpenAccountRequest("3404", "BYN", 41, "Иванов Иван Иванович", "D-41"));
        AccountView interest = ledger.open(new OpenAccountRequest("3470", "BYN", 41, "Иванов Иван Иванович", "D-41"));

        assertThat(main.number()).startsWith("340400041001");
        assertThat(interest.number()).startsWith("347000041002");
        assertThat(AccountNumbers.isValid(main.number())).isTrue();
        assertThat(main.balance()).isEqualByComparingTo("0");
        assertThat(ledger.find(41, null)).hasSize(2);
        assertThat(ledger.find(null, "D-41")).extracting(AccountView::chartCode).containsExactly("3404", "3470");
    }

    @Test
    @DisplayName("Повторное открытие счёта по тому же договору возвращает существующий счёт")
    void openingIsIdempotentPerContract() {
        OpenAccountRequest request = new OpenAccountRequest("3414", "BYN", 42, "Петров Пётр Петрович", "D-42");

        assertThat(ledger.open(request).number()).isEqualTo(ledger.open(request).number());
        assertThat(ledger.find(42, null)).hasSize(1);
    }

    @Test
    @DisplayName("Счёт нельзя открыть по коду, которого нет в плане счетов, или в неизвестной валюте")
    void rejectsUnknownChartCodeAndCurrency() {
        assertThatThrownBy(() -> ledger.open(new OpenAccountRequest("9999", "BYN", 1, "x", null)))
                .isInstanceOf(BankException.class).hasMessageContaining("нет в плане счетов");
        assertThatThrownBy(() -> ledger.open(new OpenAccountRequest("3014", "RUB", 1, "x", null)))
                .isInstanceOf(BankException.class).hasMessageContaining("нет в справочнике");
    }

    @Test
    @DisplayName("Проводка меняет обороты; сальдо активного счёта растёт по дебету, пассивного — по кредиту")
    void postingChangesTurnovers() {
        AccountView cash = ledger.system("1010", "USD");
        AccountView client = ledger.open(new OpenAccountRequest("3014", "USD", 43, "Сидоров", "T-43"));

        ledger.post(batch(
                operation("Внесение денег в кассу", entry(cash, Side.DEBIT, "500")),
                operation("Перевод денег с кассы на текущий счёт", entry(cash, Side.CREDIT, "500"), entry(client, Side.CREDIT, "500"))));

        assertThat(ledger.get(cash.number())).satisfies(a -> {
            assertThat(a.debit()).isEqualByComparingTo("500");
            assertThat(a.credit()).isEqualByComparingTo("500");
            assertThat(a.balance()).isEqualByComparingTo("0");
        });
        assertThat(ledger.get(client.number()).balance()).isEqualByComparingTo("500");
        assertThat(ledger.journal("T-43", null, 10)).hasSize(2);
    }

    @Test
    @DisplayName("При нехватке средств откатывается весь пакет операций")
    void batchIsAtomic() {
        AccountView cash = ledger.system("1010", "EUR");
        AccountView client = ledger.open(new OpenAccountRequest("3014", "EUR", 44, "Кузнецов", "T-44"));
        String key = UUID.randomUUID().toString();

        assertThatThrownBy(() -> ledger.post(new BatchRequest(key, "T-44", null, List.of(
                operation("Внесение денег в кассу", entry(cash, Side.DEBIT, "100")),
                operation("Списание со счёта клиента", entry(client, Side.DEBIT, "1"), entry(cash, Side.DEBIT, "1"))))))
                .isInstanceOfSatisfying(BankException.class, e -> assertThat(e.getCode()).isEqualTo("INSUFFICIENT_FUNDS"));

        assertThat(ledger.get(cash.number()).debit()).isEqualByComparingTo("0");
        assertThat(ledger.journal("T-44", null, 10)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from posting_batch where batch_key = ?", Integer.class, key)).isZero();
    }

    @Test
    @DisplayName("Повтор пакета с тем же ключом не проводит операции второй раз")
    void postingIsIdempotent() {
        AccountView cash = ledger.system("1010", "BYN");
        BigDecimal before = ledger.get(cash.number()).debit();
        BatchRequest request = batch(operation("Внесение денег в кассу", entry(cash, Side.DEBIT, "250")));

        var first = ledger.post(request);
        var second = ledger.post(request);

        assertThat(second).extracting("id").isEqualTo(first.stream().map(o -> o.id()).toList());
        assertThat(ledger.get(cash.number()).debit()).isEqualByComparingTo(before.add(new BigDecimal("250")));
    }

    @Test
    @DisplayName("В одной операции нельзя смешивать счета разных валют")
    void rejectsMixedCurrencies() {
        AccountView byn = ledger.system("1010", "BYN");
        AccountView usd = ledger.system("1010", "USD");

        assertThatThrownBy(() -> ledger.post(batch(
                operation("Перевод", entry(byn, Side.DEBIT, "10"), entry(usd, Side.DEBIT, "10")))))
                .isInstanceOf(BankException.class).hasMessageContaining("в одной валюте");
    }

    private static BatchRequest batch(OperationRequest... operations) {
        return new BatchRequest(UUID.randomUUID().toString(), "T-43", null, List.of(operations));
    }

    private static OperationRequest operation(String description, EntryRequest... entries) {
        return new OperationRequest(description, List.of(entries));
    }

    private static EntryRequest entry(AccountView account, Side side, String amount) {
        return new EntryRequest(account.number(), side, new BigDecimal(amount));
    }
}
