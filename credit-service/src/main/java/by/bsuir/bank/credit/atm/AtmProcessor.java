package by.bsuir.bank.credit.atm;

import by.bsuir.bank.common.api.ApiError;
import by.bsuir.bank.common.api.BankException;
import by.bsuir.bank.common.i18n.Localized;
import by.bsuir.bank.common.i18n.Messages;
import by.bsuir.bank.common.ledger.AccountInfo;
import by.bsuir.bank.common.ledger.Batch;
import by.bsuir.bank.common.ledger.LedgerApi;
import by.bsuir.bank.common.ledger.OpenAccount;
import by.bsuir.bank.credit.domain.Card;
import by.bsuir.bank.credit.domain.CreditContract;
import by.bsuir.bank.credit.domain.MobileOperator;
import by.bsuir.bank.credit.domain.MobilePayment;
import by.bsuir.bank.credit.repository.CardRepository;
import by.bsuir.bank.credit.repository.MobileOperatorRepository;
import by.bsuir.bank.credit.repository.MobilePaymentRepository;
import by.bsuir.bank.credit.service.CardService;
import by.bsuir.bank.credit.service.CreditPostings;
import by.bsuir.bank.credit.service.CreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Банковская сторона протокола «банк — банкомат». Каждая транзакция банкомата содержит номер карты,
 * PIN-код и описание операции; банк заново проверяет карту и PIN-код при каждом запросе.
 */
@Service
@RequiredArgsConstructor
public class AtmProcessor {

    public static final String OPERATOR_CHART_CODE = "3012";

    private static final String PHONE_FORMAT = "^0(25|29|33|44)\\d{7}$";

    private final CardRepository cards;
    private final CardService cardService;
    private final CreditService credits;
    private final MobileOperatorRepository operators;
    private final MobilePaymentRepository payments;
    private final LedgerApi ledger;
    private final DepositApi deposits;

    @Transactional
    public AtmResponse handle(AtmRequest request) {
        Map<String, String> tx = new LinkedHashMap<>();
        request.items().forEach(item -> tx.put(item.name(), item.value()));
        String operation = tx.get("OPERATION");
        if (tx.get("CARD") == null || tx.get("PIN") == null || operation == null) {
            return AtmResponse.error("BAD_REQUEST", Messages.get("atm.incomplete"));
        }

        Card card = cards.findByNumber(tx.get("CARD")).orElse(null);
        if (card == null) {
            return AtmResponse.error("CARD_NOT_FOUND", Messages.get("atm.cardNotFound"));
        }
        if (card.isBlocked()) {
            return AtmResponse.error("CARD_BLOCKED", Messages.get("atm.cardBlocked"));
        }
        if (!cardService.matches(card, tx.get("PIN"))) {
            int left = card.registerFailedAttempt();
            return left == 0
                    ? AtmResponse.error("CARD_BLOCKED", Messages.get("atm.blockedNow"))
                    : AtmResponse.error("WRONG_PIN", Messages.get("atm.wrongPin", left), Map.of("attemptsLeft", left));
        }
        card.setFailedAttempts(0);

        try {
            return switch (operation) {
                case "AUTHORIZE" -> AtmResponse.ok(Messages.get("atm.authorized"), Map.of("holder", card.getHolder()));
                case "BALANCE" -> balance(card);
                case "DEPOSIT_BALANCE" -> depositBalance(card);
                case "WITHDRAW" -> withdraw(card, tx.get("AMOUNT"));
                case "PAYMENT" -> payment(card, tx.get("OPERATOR"), tx.get("PHONE"), tx.get("AMOUNT"));
                default -> AtmResponse.error("UNKNOWN_OPERATION", Messages.get("atm.unknownOperation"));
            };
        } catch (BankException e) {
            return AtmResponse.error(e.getCode(), e.getMessage());
        } catch (RestClientResponseException e) {
            return rejected(e);
        } catch (RestClientException e) {
            return AtmResponse.error("SERVICE_UNAVAILABLE", Messages.get("atm.unavailable"));
        }
    }

    /** Отказ смежного сервиса (например, главной книги) передаётся банкомату с исходным кодом и текстом. */
    private static AtmResponse rejected(RestClientResponseException e) {
        try {
            ApiError error = e.getResponseBodyAs(ApiError.class);
            if (error != null && error.code() != null && error.message() != null) {
                return AtmResponse.error(error.code(), error.message());
            }
        } catch (RuntimeException ignored) {
            // тело ответа не в формате ApiError
        }
        return AtmResponse.error("BANK_ERROR", Messages.get("atm.rejected"));
    }

    public List<MobileOperator> operators() {
        return operators.findAll();
    }

    private AtmResponse balance(Card card) {
        CreditContract contract = card.getContract();
        AccountInfo account = ledger.account(contract.getMainAccount());
        return AtmResponse.ok(Messages.get("atm.balance"), receipt(card, Map.of(
                "account", account.number(), "balance", account.balance(), "currency", account.currency())));
    }

    private AtmResponse depositBalance(Card card) {
        List<Map<String, Object>> active = deposits.byClient(card.getContract().getClientId()).stream()
                .filter(deposit -> "ACTIVE".equals(deposit.status()))
                .map(deposit -> Map.<String, Object>of("number", deposit.number(), "product", deposit.productName(),
                        "amount", deposit.amount(), "currency", deposit.currency()))
                .toList();
        return AtmResponse.ok(Messages.get(active.isEmpty() ? "atm.noDeposits" : "atm.deposits"),
                receipt(card, Map.of("deposits", active)));
    }

    private AtmResponse withdraw(Card card, String text) {
        if (text == null || !text.matches("\\d{1,9}") || Long.parseLong(text) == 0) {
            return AtmResponse.error("INVALID_AMOUNT", Messages.get("atm.invalidWithdrawAmount"));
        }
        BigDecimal amount = new BigDecimal(text);
        AccountInfo account = credits.cashOut(card.getContract().getId(), amount);
        return AtmResponse.ok(Messages.get("atm.takeCash"), receipt(card, Map.of(
                "amount", amount, "balance", account.balance(), "currency", account.currency())));
    }

    private AtmResponse payment(Card card, String operatorCode, String phone, String text) {
        MobileOperator operator = operatorCode == null ? null : operators.findById(operatorCode).orElse(null);
        if (operator == null) {
            return AtmResponse.error("UNKNOWN_OPERATOR", Messages.get("atm.unknownOperator"));
        }
        if (phone == null || !phone.matches(PHONE_FORMAT)) {
            return AtmResponse.error("INVALID_PHONE", Messages.get("atm.invalidPhone"));
        }
        if (text == null || !text.matches("\\d{1,9}(\\.\\d{1,2})?") || new BigDecimal(text).signum() == 0) {
            return AtmResponse.error("INVALID_AMOUNT", Messages.get("atm.invalidPaymentAmount"));
        }
        BigDecimal amount = new BigDecimal(text);
        CreditContract contract = card.getContract();
        String currency = contract.getCurrency();
        AccountInfo main = ledger.account(contract.getMainAccount());
        if (main.balance().compareTo(amount) < 0) {
            return AtmResponse.error("INSUFFICIENT_FUNDS",
                    Messages.get("credit.insufficient", main.balance().setScale(2, RoundingMode.HALF_UP), currency));
        }

        // расчётный счёт оператора открывается при первом платеже; повторный запрос вернёт тот же счёт
        String operatorAccount = ledger.open(new OpenAccount(OPERATOR_CHART_CODE, currency, null,
                Localized.code("account.operator", operator.getName()), "OPERATOR-" + operator.getCode() + "-" + currency)).number();
        String reference = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDate date = ledger.bankDay().date();
        ledger.post(Batch.of("ATM-PAYMENT-" + reference, contract.getNumber(), date,
                CreditPostings.mobilePayment(main.number(), operatorAccount, operator.getName(), phone, amount)));

        MobilePayment payment = new MobilePayment();
        payment.setCardNumber(card.getNumber());
        payment.setOperator(operator.getCode());
        payment.setPhone(phone);
        payment.setAmount(amount);
        payment.setBankDate(date);
        payment.setReference(reference);
        payments.save(payment);

        return AtmResponse.ok(Messages.get("atm.paymentAccepted"), receipt(card, Map.of(
                "operator", operator.getName(), "phone", phone, "amount", amount, "currency", currency,
                "balance", main.balance().subtract(amount), "reference", reference)));
    }

    /** Общие реквизиты для чека: банковский день и держатель карты. */
    private Map<String, Object> receipt(Card card, Map<String, Object> details) {
        Map<String, Object> data = new LinkedHashMap<>(details);
        data.put("bankDate", ledger.bankDay().date().toString());
        data.put("holder", card.getHolder());
        return data;
    }
}
