package by.bsuir.bank.atm.session;

import by.bsuir.bank.atm.bank.BankGateway;
import by.bsuir.bank.atm.bank.BankReply;
import by.bsuir.bank.atm.bank.Item;
import by.bsuir.bank.atm.bank.Operator;
import by.bsuir.bank.atm.bank.Transaction;
import by.bsuir.bank.atm.session.Screen.Exchange;
import by.bsuir.bank.atm.session.Screen.Input;
import by.bsuir.bank.atm.session.Screen.Option;
import by.bsuir.bank.common.i18n.Messages;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Сеанс работы с банкоматом — конечный автомат. Вводимые данные накапливаются во внутреннем
 * списке {@link #buffer}; когда для операции собрано всё необходимое, список целиком
 * отправляется банку одной транзакцией и очищается. Тексты экрана формируются на языке запроса.
 */
public class AtmSession {

    public static final int MAX_PIN_ATTEMPTS = 3;

    private static final String CURRENCY = "BYN";
    private static final Set<String> OPERATIONS = Set.of("WITHDRAW", "BALANCE", "DEPOSIT_BALANCE", "PAYMENT");

    private final BankGateway bank;
    private final String terminalId;
    private final Clock clock;

    private AtmState state = AtmState.INSERT_CARD;
    /** Внутренний список банкомата. */
    private final List<Item> buffer = new ArrayList<>();
    /** Номер «вставленной» карты: устройство помнит его, пока карта не возвращена. */
    private String card;
    private int pinAttempts;
    /** Операция, выбранная в меню до повторного ввода PIN-кода. */
    private String pendingOperation;
    private String operation;
    private BankReply reply;
    private List<Operator> operators = List.of();
    private String notice;
    private boolean noticeOk;
    private String cash;
    private List<String> receipt;
    private Exchange exchange;
    private Instant touched;

    public AtmSession(BankGateway bank, String terminalId, Clock clock) {
        this.bank = bank;
        this.terminalId = terminalId;
        this.clock = clock;
        this.touched = clock.instant();
    }

    public synchronized Instant touched() {
        return touched;
    }

    // ---------- события от клиента ----------

    /** Клавиша «Ввод»: клиент подтвердил набранное на клавиатуре значение. */
    public synchronized Screen enter(String text) {
        begin();
        String value = text == null ? "" : text.replace(" ", "");
        switch (state) {
            case INSERT_CARD -> insertCard(value);
            case PIN -> {
                if (!value.matches("\\d{4}")) {
                    warn("notice.pinLength");
                } else {
                    buffer.add(new Item(Item.PIN, value));
                    authorize();
                }
            }
            case AMOUNT -> {
                if (acceptAmount(value)) {
                    execute();
                }
            }
            case PHONE -> {
                if (!value.matches("\\d{10}")) {
                    warn("notice.phoneLength");
                } else {
                    buffer.add(new Item(Item.PHONE, value));
                    state = AtmState.PAY_AMOUNT;
                }
            }
            case PAY_AMOUNT -> {
                if (acceptAmount(value)) {
                    state = AtmState.CONFIRM;
                }
            }
            default -> {
                // в остальных состояниях клавиша «Ввод» ничего не делает
            }
        }
        return screen();
    }

    /** Боковая кнопка: выбран пункт меню. */
    public synchronized Screen select(String option) {
        begin();
        switch (state) {
            case MENU -> {
                if ("EJECT".equals(option)) {
                    eject(Messages.get("notice.eject"), true);
                } else if (OPERATIONS.contains(option)) {
                    chooseOperation(option);
                }
            }
            case OPERATOR -> {
                if (operators.stream().anyMatch(o -> o.code().equals(option))) {
                    buffer.add(new Item(Item.OPERATOR, option));
                    state = AtmState.PHONE;
                }
            }
            case CONFIRM -> {
                if ("CONFIRM".equals(option)) {
                    execute();
                } else if ("RETRY".equals(option)) {
                    dropFromBuffer(Item.OPERATOR, Item.PHONE, Item.AMOUNT);
                    state = AtmState.OPERATOR;
                }
            }
            case RECEIPT_PROMPT -> {
                if ("YES".equals(option)) {
                    receipt = Receipts.print(operation, reply, card, terminalId, clock);
                }
                state = "WITHDRAW".equals(operation) ? AtmState.MENU : AtmState.RESULT;
            }
            case RESULT, MESSAGE -> {
                if ("EJECT".equals(option)) {
                    eject(Messages.get("notice.eject"), true);
                } else {
                    state = AtmState.MENU;
                }
            }
            default -> {
                // в состояниях ввода боковые кнопки не используются
            }
        }
        return screen();
    }

    /** Клавиша «Отмена»: до входа в меню возвращает карту, внутри операции — возвращает в меню. */
    public synchronized Screen cancel() {
        begin();
        switch (state) {
            case INSERT_CARD -> {
            }
            case PIN, MENU -> eject(Messages.get("notice.cancelEject"), true);
            default -> {
                dropFromBuffer(Item.OPERATION, Item.AMOUNT, Item.OPERATOR, Item.PHONE);
                state = AtmState.MENU;
                notice = Messages.get("notice.cancelled");
                noticeOk = true;
            }
        }
        return screen();
    }

    // ---------- переходы автомата ----------

    private void begin() {
        touched = clock.instant();
        notice = null;
        noticeOk = false;
        cash = null;
        receipt = null;
    }

    private void insertCard(String number) {
        if (!number.matches("\\d{16}")) {
            warn("notice.cardLength");
        } else if (!luhnValid(number)) {
            warn("notice.cardUnreadable");
        } else {
            card = number;
            pinAttempts = 0;
            pendingOperation = null;
            buffer.clear();
            buffer.add(new Item(Item.CARD, number));
            state = AtmState.PIN;
        }
    }

    /** Авторизация карты на сервере банка: во внутреннем списке к этому моменту номер карты и PIN-код. */
    private void authorize() {
        List<Item> request = new ArrayList<>(buffer);
        request.add(new Item(Item.OPERATION, "AUTHORIZE"));
        BankReply answer = send(request);
        if (answer == null) {
            dropFromBuffer(Item.PIN);
            warn("notice.noLinkRetry");
        } else if (answer.ok()) {
            pinAttempts = 0;
            if (pendingOperation != null) {
                startOperation(pendingOperation);
            } else {
                state = AtmState.MENU;
            }
        } else if ("WRONG_PIN".equals(answer.code())) {
            dropFromBuffer(Item.PIN);
            pinAttempts++;
            if (pinAttempts >= MAX_PIN_ATTEMPTS) {
                eject(Messages.get("notice.threePins"), false);
            } else {
                warn("notice.wrongPin", MAX_PIN_ATTEMPTS - pinAttempts);
            }
        } else {
            eject(Messages.get("notice.takeCard", answer.message()), false);
        }
    }

    /**
     * Выбор операции в меню. Если предыдущая транзакция уже отправлена и список очищен,
     * данные карты вводятся заново: номер банкомат подставляет сам, PIN-код запрашивает у клиента.
     */
    private void chooseOperation(String option) {
        if (hasCredentials()) {
            startOperation(option);
        } else {
            buffer.clear();
            buffer.add(new Item(Item.CARD, card));
            pendingOperation = option;
            state = AtmState.PIN;
        }
    }

    private void startOperation(String option) {
        operation = option;
        pendingOperation = null;
        buffer.add(new Item(Item.OPERATION, option));
        switch (option) {
            case "WITHDRAW" -> state = AtmState.AMOUNT;
            case "PAYMENT" -> {
                try {
                    operators = bank.operators();
                    state = AtmState.OPERATOR;
                } catch (RestClientException e) {
                    dropFromBuffer(Item.OPERATION);
                    state = AtmState.MENU;
                    warn("notice.noLinkPayments");
                }
            }
            default -> execute();          // BALANCE, DEPOSIT_BALANCE: дополнительных данных не требуется
        }
    }

    private boolean acceptAmount(String value) {
        if (!value.matches("\\d{1,7}")) {
            warn("notice.amountInteger");
            return false;
        }
        if (Integer.parseInt(value) == 0) {
            warn("notice.amountPositive");
            return false;
        }
        buffer.add(new Item(Item.AMOUNT, String.valueOf(Integer.parseInt(value))));
        return true;
    }

    /** Отправка накопленной транзакции: содержимое списка уходит банку целиком, список очищается. */
    private void execute() {
        reply = send(new ArrayList<>(buffer));
        buffer.clear();
        if (reply == null) {
            state = AtmState.MESSAGE;
            warn("notice.noLinkOperation");
        } else if (!reply.ok()) {
            if ("WRONG_PIN".equals(reply.code()) || "CARD_BLOCKED".equals(reply.code())) {
                eject(Messages.get("notice.takeCard", reply.message()), false);
            } else {
                state = AtmState.MESSAGE;
                notice = reply.message();
            }
        } else if ("PAYMENT".equals(operation)) {
            receipt = Receipts.print(operation, reply, card, terminalId, clock);     // чек платежа печатается всегда
            state = AtmState.MESSAGE;
        } else {
            if ("WITHDRAW".equals(operation)) {
                cash = Receipts.money(reply, "amount");
            }
            state = AtmState.RECEIPT_PROMPT;
        }
    }

    private BankReply send(List<Item> items) {
        List<Item> masked = items.stream().map(Item::masked).toList();
        try {
            BankReply answer = bank.send(new Transaction(List.copyOf(items)));
            exchange = new Exchange(masked, answer);
            return answer;
        } catch (RestClientException e) {
            exchange = new Exchange(masked, null);
            return null;
        }
    }

    /** Сообщение об ошибке ввода или отказе на языке клиента. */
    private void warn(String key, Object... args) {
        notice = Messages.get(key, args);
        noticeOk = false;
    }

    /** Возврат карты: сеанс завершается, банкомат снова ждёт карту. farewell — прощание, а не ошибка. */
    private void eject(String message, boolean farewell) {
        buffer.clear();
        card = null;
        pinAttempts = 0;
        pendingOperation = null;
        operation = null;
        reply = null;
        state = AtmState.INSERT_CARD;
        notice = message;
        noticeOk = farewell;
    }

    private boolean hasCredentials() {
        return has(Item.CARD) && has(Item.PIN);
    }

    private boolean has(String name) {
        return buffer.stream().anyMatch(item -> item.name().equals(name));
    }

    private void dropFromBuffer(String... names) {
        Set<String> drop = Set.of(names);
        buffer.removeIf(item -> drop.contains(item.name()));
    }

    private String valueOf(String name) {
        return buffer.stream().filter(item -> item.name().equals(name)).map(Item::value).findFirst().orElse("");
    }

    /** Контрольная цифра номера карты по алгоритму Луна — так банкомат отсеивает опечатки без запроса в банк. */
    static boolean luhnValid(String number) {
        int sum = 0;
        boolean doubled = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = number.charAt(i) - '0';
            if (doubled) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubled = !doubled;
        }
        return sum % 10 == 0;
    }

    // ---------- экран ----------

    /** Пункты меню: ключ операции и подпись на языке клиента (option.<ключ>). */
    private static List<Option> options(String... keys) {
        return Arrays.stream(keys).map(key -> new Option(key, Messages.get("option." + key))).toList();
    }

    public synchronized Screen screen() {
        String title;
        List<String> lines = List.of();
        Input input = null;
        List<Option> options = List.of();
        switch (state) {
            case INSERT_CARD -> {
                title = Messages.get("screen.insertCard.title");
                lines = List.of(Messages.get("screen.insertCard.hint"));
                input = new Input("CARD", 16, true);
            }
            case PIN -> {
                title = Messages.get(pendingOperation == null ? "screen.pin.title" : "screen.pin.again");
                lines = pendingOperation == null ? List.of() : List.of(Messages.get("screen.pin.againHint"));
                input = new Input("PIN", 4, true);
            }
            case MENU -> {
                title = Messages.get("screen.menu.title");
                options = options("WITHDRAW", "BALANCE", "DEPOSIT_BALANCE", "PAYMENT", "EJECT");
            }
            case AMOUNT -> {
                title = Messages.get("screen.amount.title");
                lines = List.of(Messages.get("screen.amount.hint", CURRENCY));
                input = new Input("NUMBER", 7, false);
            }
            case OPERATOR -> {
                title = Messages.get("screen.operator.title");
                options = operators.stream().map(o -> new Option(o.code(), o.name())).toList();
            }
            case PHONE -> {
                title = Messages.get("screen.phone.title");
                lines = List.of(Messages.get("screen.phone.hint"));
                input = new Input("NUMBER", 10, true);
            }
            case PAY_AMOUNT -> {
                title = Messages.get("screen.payAmount.title");
                lines = List.of(Messages.get("screen.payAmount.hint", CURRENCY));
                input = new Input("NUMBER", 7, false);
            }
            case CONFIRM -> {
                title = Messages.get("screen.confirm.title");
                String code = valueOf(Item.OPERATOR);
                String name = operators.stream().filter(o -> o.code().equals(code)).map(Operator::name).findFirst().orElse(code);
                lines = List.of(Messages.get("screen.confirm.operator", name),
                        Messages.get("screen.confirm.phone", valueOf(Item.PHONE)),
                        Messages.get("screen.confirm.amount", valueOf(Item.AMOUNT), CURRENCY));
                options = options("CONFIRM", "RETRY");
            }
            case RECEIPT_PROMPT -> {
                boolean withdrawal = "WITHDRAW".equals(operation);
                title = Messages.get(withdrawal ? "screen.receipt.takeCash" : "screen.receipt.dataReady");
                lines = withdrawal
                        ? List.of(Messages.get("screen.receipt.dispensed", Receipts.money(reply, "amount")),
                                Messages.get("screen.receipt.print"))
                        : List.of(Messages.get("screen.receipt.print"));
                options = options("YES", "NO");
            }
            case RESULT -> {
                if ("BALANCE".equals(operation)) {
                    title = Messages.get("screen.result.balance");
                    lines = List.of(Messages.get("screen.result.available", Receipts.money(reply, "balance")));
                } else {
                    title = Messages.get("screen.result.deposits");
                    lines = Receipts.deposits(reply);
                }
                options = List.of(new Option("CONTINUE", Messages.get("option.CONTINUE_WORK")),
                        new Option("EJECT", Messages.get("option.EJECT")));
            }
            default -> {
                boolean success = reply != null && reply.ok();
                title = Messages.get(success ? "screen.message.paid" : "screen.message.failed");
                lines = success
                        ? List.of(Messages.get("screen.message.paidAmount", Receipts.money(reply, "amount")),
                                Messages.get("screen.message.takeReceipt"))
                        : List.of();
                options = options("CONTINUE");
            }
        }
        return new Screen(state.name(), title, lines, input, options, notice, noticeOk, card != null, cash, receipt,
                buffer.stream().map(Item::masked).toList(), exchange);
    }
}
