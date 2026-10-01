package by.bsuir.bank.common.ledger;

import by.bsuir.bank.common.i18n.Localized;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Тестовый двойник сервиса «Счета»: главная книга в памяти с теми же правилами
 * (сальдо по активности, запрет отрицательного остатка, атомарность и идемпотентность пакета).
 * Позволяет тестировать сервисы депозитов и кредитов без запуска смежного микросервиса.
 */
public class InMemoryLedger implements LedgerApi {

    public static final LocalDate START = LocalDate.of(2026, 10, 1);
    public static final BigDecimal CAPITAL = new BigDecimal("1000000.00");

    private final Map<String, AccountInfo> accounts = new LinkedHashMap<>();
    private final Map<String, List<OperationInfo>> batches = new LinkedHashMap<>();
    private LocalDate date;
    private long operationSeq;

    public InMemoryLedger() {
        reset();
    }

    /** Возвращает книгу в исходное состояние: касса и фонд развития в трёх валютах, день 01.10.2026. */
    public final void reset() {
        accounts.clear();
        batches.clear();
        operationSeq = 0;
        date = START;
        for (String currency : List.of("BYN", "USD", "EUR")) {
            put(CASH, currency, null, Localized.code("account.cash", currency), null, BigDecimal.ZERO);
            put(FUND, currency, null, Localized.code("account.fund", currency), null, CAPITAL);
        }
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public BigDecimal debit(String number) {
        return account(number).debit();
    }

    public BigDecimal credit(String number) {
        return account(number).credit();
    }

    public BigDecimal balance(String number) {
        return account(number).balance();
    }

    /** Журнал операций; названия операций и счетов банка раскрываются на языке читающего. */
    public List<OperationInfo> journal() {
        return batches.values().stream().flatMap(List::stream).map(InMemoryLedger::localized).toList();
    }

    private static OperationInfo localized(OperationInfo operation) {
        return new OperationInfo(operation.id(), operation.bankDate(), Localized.render(operation.description()),
                operation.contractRef(), operation.entries().stream()
                .map(e -> new OperationInfo.EntryInfo(e.account(), Localized.render(e.accountName()), e.chartCode(),
                        e.side(), e.amount()))
                .toList());
    }

    private static AccountInfo localized(AccountInfo a) {
        return new AccountInfo(a.number(), a.chartCode(), a.chartName(), a.activity(), a.activityTitle(), a.currency(),
                Localized.render(a.name()), a.clientId(), a.contractRef(), a.debit(), a.credit(), a.balance());
    }

    @Override
    public BankDayInfo bankDay() {
        return new BankDayInfo(date);
    }

    @Override
    public DayCloseInfo closeDay(int days) {
        date = date.plusDays(days);
        return new DayCloseInfo(date, List.of());
    }

    @Override
    public List<CurrencyInfo> currencies() {
        return List.of(new CurrencyInfo("BYN", "Белорусский рубль"), new CurrencyInfo("EUR", "Евро"),
                new CurrencyInfo("USD", "Доллар США"));
    }

    @Override
    public AccountInfo systemAccount(String chartCode, String currency) {
        return accounts.values().stream()
                .filter(a -> a.chartCode().equals(chartCode) && a.currency().equals(currency) && a.clientId() == null
                        && a.contractRef() == null)
                .findFirst().orElseThrow();
    }

    @Override
    public List<AccountInfo> accounts(String contractRef) {
        return accounts.values().stream().filter(a -> contractRef.equals(a.contractRef()))
                .map(InMemoryLedger::localized).toList();
    }

    @Override
    public AccountInfo account(String number) {
        AccountInfo account = accounts.get(number);
        if (account == null) {
            throw error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Счёт " + number + " не найден");
        }
        return account;
    }

    @Override
    public AccountInfo open(OpenAccount request) {
        if (request.contractRef() != null) {
            var existing = accounts.values().stream()
                    .filter(a -> request.contractRef().equals(a.contractRef()) && a.chartCode().equals(request.chartCode()))
                    .findFirst();
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        return put(request.chartCode(), request.currency(), request.clientId(), request.name(), request.contractRef(),
                BigDecimal.ZERO);
    }

    @Override
    public List<OperationInfo> post(Batch batch) {
        if (batches.containsKey(batch.batchKey())) {
            return batches.get(batch.batchKey()).stream().map(InMemoryLedger::localized).toList();
        }
        Map<String, AccountInfo> draft = new LinkedHashMap<>(accounts);
        List<OperationInfo> posted = new ArrayList<>();
        for (Posting posting : batch.operations()) {
            List<OperationInfo.EntryInfo> entries = new ArrayList<>();
            for (Entry entry : posting.entries()) {
                AccountInfo account = draft.get(entry.account());
                if (account == null) {
                    throw error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Счёт " + entry.account() + " не найден");
                }
                BigDecimal debit = entry.side() == Side.DEBIT ? account.debit().add(entry.amount()) : account.debit();
                BigDecimal credit = entry.side() == Side.CREDIT ? account.credit().add(entry.amount()) : account.credit();
                draft.put(account.number(), copy(account, debit, credit));
                entries.add(new OperationInfo.EntryInfo(account.number(), account.name(), account.chartCode(),
                        entry.side(), entry.amount()));
            }
            for (Entry entry : posting.entries()) {
                if (draft.get(entry.account()).balance().signum() < 0) {
                    throw error(HttpStatus.CONFLICT, "INSUFFICIENT_FUNDS",
                            "Недостаточно средств на счёте " + entry.account() + " для операции «"
                                    + Localized.render(posting.description()) + "»");
                }
            }
            posted.add(new OperationInfo(++operationSeq, batch.bankDate() != null ? batch.bankDate() : date,
                    posting.description(), batch.contractRef(), entries));
        }
        accounts.putAll(draft);
        batches.put(batch.batchKey(), posted);
        return posted.stream().map(InMemoryLedger::localized).toList();
    }

    @Override
    public List<OperationInfo> operations(String contractRef) {
        return journal().stream().filter(o -> contractRef.equals(o.contractRef())).toList();
    }

    private AccountInfo put(String chartCode, String currency, Integer clientId, String name, String contractRef,
                            BigDecimal credit) {
        boolean passive = chartCode.startsWith("3") || chartCode.startsWith("7");
        String number = chartCode + String.format("%09d", accounts.size() + 1);
        AccountInfo account = copy(new AccountInfo(number, chartCode, "", passive ? "PASSIVE" : "ACTIVE",
                passive ? "Пассивный" : "Активный", currency, name, clientId, contractRef, null, null, null),
                BigDecimal.ZERO, credit);
        accounts.put(number, account);
        return account;
    }

    private static AccountInfo copy(AccountInfo a, BigDecimal debit, BigDecimal credit) {
        BigDecimal balance = "PASSIVE".equals(a.activity()) ? credit.subtract(debit) : debit.subtract(credit);
        return new AccountInfo(a.number(), a.chartCode(), a.chartName(), a.activity(), a.activityTitle(),
                a.currency(), a.name(), a.clientId(), a.contractRef(), debit, credit, balance);
    }

    /** Ошибка в том же виде, в каком её вернул бы настоящий сервис: код состояния и тело ApiError. */
    private static HttpClientErrorException error(HttpStatus status, String code, String message) {
        String body = "{\"code\":\"" + code + "\",\"message\":\"" + message.replace("\"", "'") + "\",\"fields\":{}}";
        return HttpClientErrorException.create(status, status.getReasonPhrase(), new org.springframework.http.HttpHeaders(),
                body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }
}
