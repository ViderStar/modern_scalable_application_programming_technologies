package by.bsuir.bank.account.service;

import by.bsuir.bank.account.domain.Account;
import by.bsuir.bank.account.domain.AccountNumbers;
import by.bsuir.bank.account.domain.Activity;
import by.bsuir.bank.account.domain.ChartAccount;
import by.bsuir.bank.account.domain.Operation;
import by.bsuir.bank.account.domain.PostingBatch;
import by.bsuir.bank.account.dto.AccountReport;
import by.bsuir.bank.account.dto.AccountView;
import by.bsuir.bank.account.dto.BatchRequest;
import by.bsuir.bank.account.dto.EntryRequest;
import by.bsuir.bank.account.dto.OpenAccountRequest;
import by.bsuir.bank.account.dto.OperationRequest;
import by.bsuir.bank.account.dto.OperationView;
import by.bsuir.bank.account.repository.AccountRepository;
import by.bsuir.bank.account.repository.ChartAccountRepository;
import by.bsuir.bank.account.repository.CurrencyRepository;
import by.bsuir.bank.account.repository.OperationRepository;
import by.bsuir.bank.account.repository.PostingBatchRepository;
import by.bsuir.bank.common.api.BankException;
import by.bsuir.bank.common.i18n.Localized;
import by.bsuir.bank.common.i18n.Messages;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Главная книга: открытие счетов, проведение операций, отчёт о состоянии счетов. */
@Service
@RequiredArgsConstructor
public class LedgerService {

    public static final int BANK_OWNER = 0;

    private final AccountRepository accounts;
    private final ChartAccountRepository chart;
    private final CurrencyRepository currencies;
    private final OperationRepository operations;
    private final PostingBatchRepository batches;
    private final BankDayService bankDay;

    /**
     * Открывает лицевой счёт. Повторный запрос для того же договора и балансового счёта
     * возвращает уже открытый счёт — вызывающий сервис может безопасно повторять запрос.
     */
    @Transactional
    public AccountView open(OpenAccountRequest request) {
        ChartAccount chartAccount = chart.findById(request.chartCode())
                .orElseThrow(() -> BankException.invalid(Messages.get("ledger.noChart", request.chartCode())));
        if (!currencies.existsById(request.currency())) {
            throw BankException.invalid(Messages.get("ledger.noCurrency", request.currency()));
        }
        if (request.contractRef() != null) {
            var existing = accounts.findByContractRefAndChartCode(request.contractRef(), request.chartCode());
            if (existing.isPresent()) {
                return AccountView.from(existing.get());
            }
        }
        int ownerCode = request.clientId() == null ? BANK_OWNER : request.clientId();
        int ownerSeq = accounts.maxOwnerSeq(ownerCode) + 1;

        Account account = new Account();
        account.setNumber(AccountNumbers.generate(chartAccount.getCode(), ownerCode, ownerSeq));
        account.setChart(chartAccount);
        account.setCurrency(request.currency());
        account.setName(request.name());
        account.setOwnerCode(ownerCode);
        account.setOwnerSeq(ownerSeq);
        account.setContractRef(request.contractRef());
        account.setOpenedOn(bankDay.current());
        return AccountView.from(accounts.saveAndFlush(account));
    }

    /**
     * Проводит пакет операций в одной транзакции. После каждой операции остаток затронутых
     * счетов не должен стать отрицательным — иначе весь пакет откатывается.
     */
    @Transactional
    public List<OperationView> post(BatchRequest request) {
        if (batches.existsById(request.batchKey())) {
            return operations.findByBatchKeyOrderById(request.batchKey()).stream().map(OperationView::from).toList();
        }
        batches.saveAndFlush(new PostingBatch(request.batchKey()));

        LocalDate date = request.bankDate() != null ? request.bankDate() : bankDay.current();
        Map<String, Account> locked = new LinkedHashMap<>();
        List<Operation> posted = new ArrayList<>();
        for (OperationRequest item : request.operations()) {
            Operation operation = new Operation();
            operation.setBatchKey(request.batchKey());
            operation.setBankDate(date);
            operation.setDescription(item.description());
            operation.setContractRef(request.contractRef());
            operation.setCreatedAt(LocalDateTime.now());

            Set<Account> touched = new LinkedHashSet<>();
            String currency = null;
            for (EntryRequest entry : item.entries()) {
                Account account = locked.computeIfAbsent(entry.account(), number -> accounts.findByNumberForUpdate(number)
                        .orElseThrow(() -> BankException.notFound(Messages.get("ledger.accountNotFound", number))));
                if (currency != null && !currency.equals(account.getCurrency())) {
                    throw BankException.invalid(Messages.get("ledger.sameCurrency"));
                }
                currency = account.getCurrency();
                account.apply(entry.side(), entry.amount());
                operation.addEntry(account, entry.side(), entry.amount());
                touched.add(account);
            }
            touched.forEach(account -> checkNotOverdrawn(account, item.description()));
            posted.add(operations.save(operation));
        }
        operations.flush();
        return posted.stream().map(OperationView::from).toList();
    }

    private static void checkNotOverdrawn(Account account, String operation) {
        if (account.getChart().getActivity() != Activity.ACTIVE_PASSIVE
                && account.getBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw new BankException(HttpStatus.CONFLICT, "INSUFFICIENT_FUNDS",
                    Messages.get("ledger.insufficient", account.getNumber(), Localized.render(account.getName()),
                            Localized.render(operation)));
        }
    }

    @Transactional(readOnly = true)
    public AccountView get(String number) {
        return accounts.findByNumber(number).map(AccountView::from)
                .orElseThrow(() -> BankException.notFound(Messages.get("ledger.accountNotFound", number)));
    }

    /** Собственный счёт банка — касса (1010) или фонд развития (7327) — в заданной валюте. */
    @Transactional(readOnly = true)
    public AccountView system(String chartCode, String currency) {
        return accounts.findFirstByChartCodeAndCurrencyAndOwnerCodeAndContractRefIsNull(chartCode, currency, BANK_OWNER)
                .map(AccountView::from)
                .orElseThrow(() -> BankException.notFound(
                        Messages.get("ledger.systemAccountNotFound", chartCode, currency)));
    }

    @Transactional(readOnly = true)
    public List<AccountView> find(Integer clientId, String contractRef) {
        List<Account> found;
        if (contractRef != null) {
            found = accounts.findByContractRefOrderByNumber(contractRef);
        } else if (clientId != null) {
            found = accounts.findByOwnerCodeOrderByNumber(clientId);
        } else {
            found = accounts.findAllByOrderByChartCodeAscNumberAsc();
        }
        return found.stream().map(AccountView::from).toList();
    }

    @Transactional(readOnly = true)
    public AccountReport report() {
        List<AccountView> all = find(null, null);
        Map<String, BigDecimal[]> sums = new LinkedHashMap<>();
        for (AccountView account : all) {
            BigDecimal[] sum = sums.computeIfAbsent(account.currency(), c -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            sum[0] = sum[0].add(account.debit());
            sum[1] = sum[1].add(account.credit());
        }
        List<AccountReport.Total> totals = sums.entrySet().stream()
                .map(e -> new AccountReport.Total(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
        return new AccountReport(bankDay.current(), all, totals);
    }

    @Transactional(readOnly = true)
    public List<OperationView> journal(String contractRef, String account, int limit) {
        return operations.search(contractRef, account, PageRequest.of(0, limit)).stream()
                .map(OperationView::from)
                .toList();
    }
}
