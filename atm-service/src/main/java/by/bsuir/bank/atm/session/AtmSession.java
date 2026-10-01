package by.bsuir.bank.atm.session;

import by.bsuir.bank.atm.bank.BankGateway;
import by.bsuir.bank.atm.bank.BankReply;
import by.bsuir.bank.atm.bank.Item;
import by.bsuir.bank.atm.bank.Operator;
import by.bsuir.bank.atm.bank.Transaction;
import by.bsuir.bank.atm.session.Screen.Exchange;
import by.bsuir.bank.atm.session.Screen.Input;
import by.bsuir.bank.atm.session.Screen.Option;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Сеанс работы с банкоматом — конечный автомат. Вводимые данные накапливаются во внутреннем
 * списке {@link #buffer}; когда для операции собрано всё необходимое, список целиком
 * отправляется банку одной транзакцией и очищается.
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
                    notice = "PIN-код состоит из 4 цифр";
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
                    notice = "Номер телефона — 10 цифр, например 0291234567";
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
                    eject("Заберите карту. Спасибо, что воспользовались нашим банкоматом");
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
                    eject("Заберите карту. Спасибо, что воспользовались нашим банкоматом");
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
            case PIN, MENU -> eject("Операция отменена. Заберите карту");
            default -> {
                dropFromBuffer(Item.OPERATION, Item.AMOUNT, Item.OPERATOR, Item.PHONE);
                state = AtmState.MENU;
                notice = "Операция отменена";
            }
        }
        return screen();
    }

    // ---------- переходы автомата ----------

    private void begin() {
        touched = clock.instant();
        notice = null;
        cash = null;
        receipt = null;
    }

    private void insertCard(String number) {
        if (!number.matches("\\d{16}")) {
            notice = "Номер карты состоит из 16 цифр";
        } else if (!luhnValid(number)) {
            notice = "Карта не читается: проверьте номер";
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
            notice = "Нет связи с банком. Повторите попытку позже";
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
                eject("PIN-код введён неверно три раза. Работа завершена, заберите карту");
            } else {
                notice = "Неверный PIN-код. Осталось попыток: " + (MAX_PIN_ATTEMPTS - pinAttempts);
            }
        } else {
            eject(answer.message() + ". Заберите карту");
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
                    notice = "Нет связи с банком. Платежи временно недоступны";
                }
            }
            default -> execute();          // BALANCE, DEPOSIT_BALANCE: дополнительных данных не требуется
        }
    }

    private boolean acceptAmount(String value) {
        if (!value.matches("\\d{1,7}")) {
            notice = "Сумма — целое неотрицательное число";
            return false;
        }
        if (Integer.parseInt(value) == 0) {
            notice = "Сумма должна быть больше нуля";
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
            notice = "Нет связи с банком. Операция не выполнена";
        } else if (!reply.ok()) {
            if ("WRONG_PIN".equals(reply.code()) || "CARD_BLOCKED".equals(reply.code())) {
                eject(reply.message() + ". Заберите карту");
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

    /** Возврат карты: сеанс завершается, банкомат снова ждёт карту. */
    private void eject(String message) {
        buffer.clear();
        card = null;
        pinAttempts = 0;
        pendingOperation = null;
        operation = null;
        reply = null;
        state = AtmState.INSERT_CARD;
        notice = message;
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

    public synchronized Screen screen() {
        String title;
        List<String> lines = List.of();
        Input input = null;
        List<Option> options = List.of();
        switch (state) {
            case INSERT_CARD -> {
                title = "Вставьте, пожалуйста, карту";
                lines = List.of("(введите её номер)");
                input = new Input("CARD", 16, true);
            }
            case PIN -> {
                title = pendingOperation == null ? "Введите PIN-код" : "Введите PIN-код ещё раз";
                lines = pendingOperation == null ? List.of() : List.of("Для новой операции данные карты вводятся заново");
                input = new Input("PIN", 4, true);
            }
            case MENU -> {
                title = "Выберите операцию";
                options = List.of(
                        new Option("WITHDRAW", "Снять наличные"),
                        new Option("BALANCE", "Остаток кредитного счёта"),
                        new Option("DEPOSIT_BALANCE", "Остаток депозитного счёта"),
                        new Option("PAYMENT", "Оплата мобильной связи"),
                        new Option("EJECT", "Забрать карту"));
            }
            case AMOUNT -> {
                title = "Введите сумму";
                lines = List.of("Снятие наличных с кредитного счёта, " + CURRENCY);
                input = new Input("NUMBER", 7, false);
            }
            case OPERATOR -> {
                title = "Выберите оператора связи";
                options = operators.stream().map(o -> new Option(o.code(), o.name())).toList();
            }
            case PHONE -> {
                title = "Введите номер телефона";
                lines = List.of("10 цифр, например 0291234567");
                input = new Input("NUMBER", 10, true);
            }
            case PAY_AMOUNT -> {
                title = "Введите сумму платежа";
                lines = List.of("Оплата мобильной связи, " + CURRENCY);
                input = new Input("NUMBER", 7, false);
            }
            case CONFIRM -> {
                title = "Проверьте данные платежа";
                String code = valueOf(Item.OPERATOR);
                String name = operators.stream().filter(o -> o.code().equals(code)).map(Operator::name).findFirst().orElse(code);
                lines = List.of("Оператор: " + name, "Телефон: " + valueOf(Item.PHONE),
                        "Сумма: " + valueOf(Item.AMOUNT) + " " + CURRENCY);
                options = List.of(new Option("CONFIRM", "Подтвердить"), new Option("RETRY", "Ввести заново"));
            }
            case RECEIPT_PROMPT -> {
                title = "WITHDRAW".equals(operation) ? "Заберите деньги" : "Данные получены";
                lines = "WITHDRAW".equals(operation)
                        ? List.of("Выдано: " + Receipts.money(reply, "amount"), "Распечатать чек?")
                        : List.of("Распечатать чек?");
                options = List.of(new Option("YES", "Да"), new Option("NO", "Нет"));
            }
            case RESULT -> {
                if ("BALANCE".equals(operation)) {
                    title = "Остаток кредитного счёта";
                    lines = List.of("Доступно: " + Receipts.money(reply, "balance"));
                } else {
                    title = "Депозитные счета";
                    lines = Receipts.deposits(reply);
                }
                options = List.of(new Option("CONTINUE", "Продолжить работу"), new Option("EJECT", "Забрать карту"));
            }
            default -> {
                boolean success = reply != null && reply.ok();
                title = success ? "Платёж принят" : "Операция не выполнена";
                lines = success ? List.of("Оплачено: " + Receipts.money(reply, "amount"), "Возьмите чек") : List.of();
                options = List.of(new Option("CONTINUE", "Продолжить"));
            }
        }
        return new Screen(state.name(), title, lines, input, options, notice, card != null, cash, receipt,
                buffer.stream().map(Item::masked).toList(), exchange);
    }
}
