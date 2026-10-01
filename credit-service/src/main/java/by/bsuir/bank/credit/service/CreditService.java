package by.bsuir.bank.credit.service;

import by.bsuir.bank.common.api.BankException;
import by.bsuir.bank.common.client.ClientApi;
import by.bsuir.bank.common.client.ClientInfo;
import by.bsuir.bank.common.ledger.AccountInfo;
import by.bsuir.bank.common.ledger.Batch;
import by.bsuir.bank.common.ledger.LedgerApi;
import by.bsuir.bank.common.ledger.OpenAccount;
import by.bsuir.bank.common.ledger.Posting;
import by.bsuir.bank.credit.domain.ContractStatus;
import by.bsuir.bank.credit.domain.CreditContract;
import by.bsuir.bank.credit.domain.CreditProduct;
import by.bsuir.bank.credit.domain.PaymentItem;
import by.bsuir.bank.credit.dto.CreditDetails;
import by.bsuir.bank.credit.dto.CreditIssued;
import by.bsuir.bank.credit.dto.CreditRequest;
import by.bsuir.bank.credit.dto.CreditView;
import by.bsuir.bank.credit.dto.Meta;
import by.bsuir.bank.credit.dto.PinEnvelope;
import by.bsuir.bank.credit.dto.ScheduleRequest;
import by.bsuir.bank.credit.dto.ScheduleRow;
import by.bsuir.bank.credit.dto.ScheduleView;
import by.bsuir.bank.credit.repository.CardRepository;
import by.bsuir.bank.credit.repository.CreditContractRepository;
import by.bsuir.bank.credit.repository.CreditProductRepository;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditService {

    public static final String MAIN_CHART_CODE = "2400";
    public static final String INTEREST_CHART_CODE = "2470";

    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final CreditContractRepository contracts;
    private final CreditProductRepository products;
    private final CardRepository cards;
    private final CardService cardService;
    private final ClientApi clients;
    private final LedgerApi ledger;

    public Meta meta() {
        String last = contracts.maxNumber();
        int next = last == null ? 1 : Integer.parseInt(last.substring(2)) + 1;
        return new Meta(ledger.bankDay().date(), ledger.currencies(), String.format("К-%06d", next));
    }

    /** Предварительный расчёт графика платежей для формы договора. */
    @Transactional(readOnly = true)
    public ScheduleView preview(ScheduleRequest request) {
        CreditProduct product = product(request.productId());
        return ScheduleView.of(ScheduleCalculator.build(product.getKind(), request.amount(), product.getRate(),
                request.termMonths(), ledger.bankDay().date()));
    }

    @Transactional(readOnly = true)
    public List<CreditView> list() {
        return contracts.findAllByOrderByIdDesc().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public CreditDetails details(Long id) {
        CreditContract contract = find(id);
        List<ScheduleRow> rows = contract.getSchedule().stream().map(ScheduleRow::from).toList();
        return new CreditDetails(view(contract), ScheduleView.of(rows),
                ledger.accounts(contract.getNumber()), ledger.operations(contract.getNumber()));
    }

    /**
     * Заключение договора: проверка условий программы, открытие текущего и процентного счетов,
     * выделение кредита из фонда развития, расчёт графика платежей и выпуск карты к счёту.
     */
    @Transactional
    public CreditIssued open(CreditRequest request) {
        CreditProduct product = product(request.productId());
        LocalDate today = ledger.bankDay().date();
        checkTerms(request, product, today);
        if (contracts.existsByNumber(request.number())) {
            throw new BankException(HttpStatus.CONFLICT, "DUPLICATE_CONTRACT", "Договор уже существует",
                    Map.of("number", "Договор с таким номером уже заключён"));
        }
        ClientInfo client = findClient(request.clientId());

        String number = request.number();
        String currency = product.getCurrency();
        String main = ledger.open(new OpenAccount(MAIN_CHART_CODE, currency,
                client.id().intValue(), client.fullName(), number)).number();
        String interest = ledger.open(new OpenAccount(INTEREST_CHART_CODE, currency,
                client.id().intValue(), client.fullName(), number)).number();
        post(key(number, "ISSUE"), number, today, List.of(CreditPostings.issue(fund(currency), main, request.amount())));

        CreditContract contract = new CreditContract();
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
        contract.setStatus(ContractStatus.ACTIVE);
        for (ScheduleRow row : ScheduleCalculator.build(product.getKind(), request.amount(), product.getRate(),
                request.termMonths(), today)) {
            PaymentItem item = new PaymentItem();
            item.setContract(contract);
            item.setSeq(row.seq());
            item.setDueDate(row.dueDate());
            item.setPrincipal(row.principal());
            item.setInterest(row.interest());
            item.setBalanceAfter(row.balanceAfter());
            contract.getSchedule().add(item);
        }
        contracts.saveAndFlush(contract);
        PinEnvelope card = cardService.issue(contract);
        return new CreditIssued(view(contract), card);
    }

    /** Контроль корректности условий договора относительно выбранной кредитной программы. */
    private void checkTerms(CreditRequest request, CreditProduct product, LocalDate today) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!product.getCurrency().equals(request.currency())) {
            errors.put("productId", "Программа недоступна в валюте " + request.currency());
        }
        if (request.amount().compareTo(product.getMinAmount()) < 0 || request.amount().compareTo(product.getMaxAmount()) > 0) {
            errors.put("amount", "Сумма кредита по программе: от " + product.getMinAmount()
                    + " до " + product.getMaxAmount() + " " + product.getCurrency());
        }
        if (request.rate().compareTo(product.getRate()) != 0) {
            errors.put("rate", "Ставка по программе — " + product.getRate() + " %");
        }
        if (request.termMonths() < product.getMinTermMonths() || request.termMonths() > product.getMaxTermMonths()) {
            errors.put("termMonths", "Срок по программе: от " + product.getMinTermMonths()
                    + " до " + product.getMaxTermMonths() + " мес.");
        }
        if (!request.startDate().equals(today)) {
            errors.put("startDate", "Договор заключается текущим банковским днём: " + RU_DATE.format(today));
        }
        if (!request.endDate().equals(request.startDate().plusMonths(request.termMonths()))) {
            errors.put("endDate", "Дата окончания не соответствует сроку договора");
        }
        if (!errors.isEmpty()) {
            throw BankException.fields(errors);
        }
    }

    /**
     * Снятие наличных с кредитного счёта — в кассе отделения или в банкомате.
     * Возвращает состояние счёта после операции. Метод намеренно не транзакционный: локальных данных
     * он не меняет, а отказ (нехватка средств) не должен помечать на откат транзакцию вызывающего кода.
     */
    public AccountInfo cashOut(Long id, BigDecimal amount) {
        CreditContract contract = find(id);
        BigDecimal available = ledger.account(contract.getMainAccount()).balance();
        if (available.compareTo(amount) < 0) {
            throw BankException.conflict("INSUFFICIENT_FUNDS",
                    "Недостаточно средств на счёте: доступно " + available + " " + contract.getCurrency());
        }
        post(key(contract.getNumber(), "CASH-" + UUID.randomUUID()), contract.getNumber(), null,
                CreditPostings.cashOut(cash(contract.getCurrency()), contract.getMainAccount(), amount));
        return ledger.account(contract.getMainAccount());
    }

    /**
     * Обработка договора при открытии банковского дня date: проводятся все платежи графика,
     * срок которых наступил. Считается, что клиент платит без задержки. Ключ пакета проводок
     * привязан к номеру платежа, поэтому повторная обработка дня не проводит платёж дважды.
     */
    @Transactional
    public List<String> processDay(Long id, LocalDate date) {
        CreditContract contract = find(id);
        List<String> events = new ArrayList<>();
        if (!contract.isActive()) {
            return events;
        }
        String number = contract.getNumber();
        String currency = contract.getCurrency();
        for (PaymentItem item : contract.getSchedule()) {
            if (item.isPaid() || item.getDueDate().isAfter(date)) {
                continue;
            }
            boolean last = item.getSeq() == contract.getTermMonths();
            post(key(number, "PAY-" + item.getSeq()), number, date, CreditPostings.payment(cash(currency), fund(currency),
                    contract.getMainAccount(), contract.getInterestAccount(), item.getInterest(), item.getPrincipal(), last));
            item.setPaidOn(date);
            contract.setInterestPaid(contract.getInterestPaid().add(item.getInterest()));
            contract.setPrincipalPaid(contract.getPrincipalPaid().add(item.getPrincipal()));
            events.add(date + ": " + number + " — платёж №" + item.getSeq() + ": проценты " + item.getInterest()
                    + ", основной долг " + item.getPrincipal() + " " + currency);
        }
        if (contract.getSchedule().stream().allMatch(PaymentItem::isPaid)) {
            contract.setStatus(ContractStatus.CLOSED);
            contract.setClosedOn(date);
            events.add(date + ": " + number + " — кредит погашен, договор закрыт");
        }
        return events;
    }

    private ClientInfo findClient(Long clientId) {
        try {
            return clients.get(clientId);
        } catch (HttpClientErrorException.NotFound e) {
            throw BankException.fields(Map.of("clientId", "Клиент не найден в модуле «Клиенты»"));
        }
    }

    private CreditProduct product(Long id) {
        return products.findById(id)
                .orElseThrow(() -> BankException.fields(Map.of("productId", "Вид кредита отсутствует в справочнике")));
    }

    private CreditView view(CreditContract contract) {
        return CreditView.from(contract, cards.findByContractId(contract.getId()).orElse(null));
    }

    private void post(String batchKey, String contractRef, LocalDate date, List<Posting> postings) {
        ledger.post(new Batch(batchKey, contractRef, date, postings));
    }

    private static String key(String number, String step) {
        return "CRD-" + number + "-" + step;
    }

    private String cash(String currency) {
        return ledger.systemAccount(LedgerApi.CASH, currency).number();
    }

    private String fund(String currency) {
        return ledger.systemAccount(LedgerApi.FUND, currency).number();
    }

    private CreditContract find(Long id) {
        return contracts.findById(id).orElseThrow(() -> BankException.notFound("Кредитный договор не найден"));
    }
}
