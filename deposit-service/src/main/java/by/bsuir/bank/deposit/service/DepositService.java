package by.bsuir.bank.deposit.service;

import by.bsuir.bank.common.api.BankException;
import by.bsuir.bank.common.client.ClientApi;
import by.bsuir.bank.common.client.ClientInfo;
import by.bsuir.bank.common.i18n.Messages;
import by.bsuir.bank.common.ledger.Batch;
import by.bsuir.bank.common.ledger.LedgerApi;
import by.bsuir.bank.common.ledger.OpenAccount;
import by.bsuir.bank.common.ledger.Posting;
import by.bsuir.bank.deposit.domain.DepositContract;
import by.bsuir.bank.deposit.domain.DepositKind;
import by.bsuir.bank.deposit.domain.DepositProduct;
import by.bsuir.bank.deposit.domain.DepositStatus;
import by.bsuir.bank.deposit.dto.DepositDetails;
import by.bsuir.bank.deposit.dto.DepositRequest;
import by.bsuir.bank.deposit.dto.DepositView;
import by.bsuir.bank.deposit.dto.Meta;
import by.bsuir.bank.deposit.repository.DepositContractRepository;
import by.bsuir.bank.deposit.repository.DepositProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DepositService {

    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final DepositContractRepository contracts;
    private final DepositProductRepository products;
    private final ClientApi clients;
    private final LedgerApi ledger;

    public Meta meta() {
        String last = contracts.maxNumber();
        int next = last == null ? 1 : Integer.parseInt(last.substring(2)) + 1;
        return new Meta(ledger.bankDay().date(), ledger.currencies(), String.format("Д-%06d", next));
    }

    @Transactional(readOnly = true)
    public List<DepositView> list(Long clientId) {
        List<DepositContract> found = clientId == null
                ? contracts.findAllByOrderByIdDesc()
                : contracts.findByClientIdOrderById(clientId);
        return found.stream().map(DepositView::from).toList();
    }

    @Transactional(readOnly = true)
    public DepositDetails details(Long id) {
        DepositContract contract = find(id);
        return new DepositDetails(DepositView.from(contract),
                ledger.accounts(contract.getNumber()), ledger.operations(contract.getNumber()));
    }

    /**
     * Заключение договора: проверка условий программы, открытие двух счетов (основной суммы и процентов)
     * и проводки приёма вклада. Запросы к главной книге идемпотентны по номеру договора,
     * поэтому повтор после сбоя не создаёт счета и проводки дважды.
     */
    @Transactional
    public DepositView open(DepositRequest request) {
        DepositProduct product = products.findById(request.productId())
                .orElseThrow(() -> BankException.fields(Map.of("productId", Messages.get("deposit.unknownProduct"))));
        LocalDate today = ledger.bankDay().date();
        checkTerms(request, product, today);
        if (contracts.existsByNumber(request.number())) {
            throw new BankException(HttpStatus.CONFLICT, "DUPLICATE_CONTRACT", Messages.get("contract.duplicate"),
                    Map.of("number", Messages.get("contract.duplicateNumber")));
        }
        ClientInfo client = findClient(request.clientId());

        DepositKind kind = product.getKind();
        String number = request.number();
        String currency = product.getCurrency();
        String main = ledger.open(new OpenAccount(kind.getMainChartCode(), currency,
                client.id().intValue(), client.fullName(), number)).number();
        String interest = ledger.open(new OpenAccount(kind.getInterestChartCode(), currency,
                client.id().intValue(), client.fullName(), number)).number();
        post(key(number, "OPEN"), number, today,
                DepositPostings.open(cash(currency), fund(currency), main, request.amount()));

        DepositContract contract = new DepositContract();
        contract.setNumber(number);
        contract.setProduct(product);
        contract.setClientId(client.id());
        contract.setClientName(client.fullName());
        contract.setCurrency(currency);
        contract.setAmount(request.amount());
        contract.setRate(product.getRate());
        contract.setTermMonths(request.termMonths());
        contract.setStartDate(today);
        contract.setEndDate(request.endDate());
        contract.setMainAccount(main);
        contract.setInterestAccount(interest);
        contract.setStatus(DepositStatus.ACTIVE);
        return DepositView.from(contracts.save(contract));
    }

    /** Контроль корректности условий договора относительно выбранной депозитной программы. */
    private void checkTerms(DepositRequest request, DepositProduct product, LocalDate today) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!product.getCurrency().equals(request.currency())) {
            errors.put("productId", Messages.get("contract.productCurrency", request.currency()));
        }
        if (request.amount().compareTo(product.getMinAmount()) < 0) {
            errors.put("amount", Messages.get("deposit.minAmount", product.getMinAmount(), product.getCurrency()));
        }
        if (request.rate().compareTo(product.getRate()) != 0) {
            errors.put("rate", Messages.get("contract.rate", product.getRate()));
        }
        if (request.termMonths() < product.getMinTermMonths() || request.termMonths() > product.getMaxTermMonths()) {
            errors.put("termMonths", Messages.get("contract.termRange",
                    product.getMinTermMonths(), product.getMaxTermMonths()));
        }
        if (!request.startDate().equals(today)) {
            errors.put("startDate", Messages.get("contract.startDate", RU_DATE.format(today)));
        }
        if (!request.endDate().equals(request.startDate().plusMonths(request.termMonths()))) {
            errors.put("endDate", Messages.get("contract.endDate"));
        }
        if (!errors.isEmpty()) {
            throw BankException.fields(errors);
        }
    }

    private ClientInfo findClient(Long clientId) {
        try {
            return clients.get(clientId);
        } catch (HttpClientErrorException.NotFound e) {
            throw BankException.fields(Map.of("clientId", Messages.get("contract.clientNotFound")));
        }
    }

    /** Досрочный отзыв вклада: доступен только для отзывных программ. */
    @Transactional
    public DepositView withdraw(Long id) {
        DepositContract contract = find(id);
        if (!contract.isActive()) {
            throw BankException.conflict("CONTRACT_CLOSED", Messages.get("deposit.closed", contract.getNumber()));
        }
        if (contract.getProduct().getKind() == DepositKind.IRREVOCABLE) {
            throw BankException.conflict("IRREVOCABLE", Messages.get("deposit.irrevocable"));
        }
        LocalDate today = ledger.bankDay().date();
        payInterest(contract, today);
        close(contract, today);
        return DepositView.from(contract);
    }

    /**
     * Обработка договора при открытии банковского дня date: начисление процентов за прошедшие дни,
     * выплата (ежемесячно либо в конце срока) и возврат вклада по окончании договора.
     * Обработка накопительная и идемпотентная: пропуск дня или повтор не искажают суммы.
     */
    @Transactional
    public List<String> processDay(Long id, LocalDate date) {
        DepositContract contract = find(id);
        List<String> events = new ArrayList<>();
        if (!contract.isActive() || !date.isAfter(contract.getStartDate())) {
            return events;
        }
        String number = contract.getNumber();
        String currency = contract.getCurrency();
        boolean matured = !date.isBefore(contract.getEndDate());
        LocalDate upTo = matured ? contract.getEndDate() : date;

        BigDecimal target = InterestCalculator.accrued(contract.getAmount(), contract.getRate(), contract.getStartDate(), upTo);
        BigDecimal delta = target.subtract(contract.getAccrued());
        if (delta.signum() > 0) {
            post(key(number, "ACCRUE-" + date), number, date,
                    List.of(DepositPostings.accrual(fund(currency), contract.getInterestAccount(), delta)));
            contract.setAccrued(target);
            events.add(Messages.get("deposit.event.accrued", RU_DATE.format(date), number, delta, currency));
        }

        int months = InterestCalculator.fullMonths(contract.getStartDate(), upTo);
        boolean monthlyPayout = contract.getProduct().getKind() == DepositKind.REVOCABLE && months > contract.getPaidMonths();
        if (matured || monthlyPayout) {
            BigDecimal paid = payInterest(contract, date);
            contract.setPaidMonths(months);
            if (paid.signum() > 0) {
                events.add(Messages.get("deposit.event.paid", RU_DATE.format(date), number, paid, currency));
            }
        }
        if (matured) {
            close(contract, date);
            events.add(Messages.get("deposit.event.matured", RU_DATE.format(date), number, contract.getAmount(), currency));
        }
        return events;
    }

    private BigDecimal payInterest(DepositContract contract, LocalDate date) {
        BigDecimal due = contract.getAccrued().subtract(contract.getPaid());
        if (due.signum() > 0) {
            post(key(contract.getNumber(), "PAYOUT-" + date), contract.getNumber(), date,
                    DepositPostings.payout(cash(contract.getCurrency()), contract.getInterestAccount(), due));
            contract.setPaid(contract.getAccrued());
        }
        return due;
    }

    private void close(DepositContract contract, LocalDate date) {
        String currency = contract.getCurrency();
        post(key(contract.getNumber(), "CLOSE"), contract.getNumber(), date,
                DepositPostings.close(cash(currency), fund(currency), contract.getMainAccount(), contract.getAmount()));
        contract.setStatus(DepositStatus.CLOSED);
        contract.setClosedOn(date);
    }

    private void post(String batchKey, String contractRef, LocalDate date, List<Posting> postings) {
        ledger.post(new Batch(batchKey, contractRef, date, postings));
    }

    private static String key(String number, String step) {
        return "DEP-" + number + "-" + step;
    }

    private String cash(String currency) {
        return ledger.systemAccount(LedgerApi.CASH, currency).number();
    }

    private String fund(String currency) {
        return ledger.systemAccount(LedgerApi.FUND, currency).number();
    }

    private DepositContract find(Long id) {
        return contracts.findById(id).orElseThrow(() -> BankException.notFound(Messages.get("deposit.notFound")));
    }
}
