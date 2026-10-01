package by.bsuir.bank.atm;

import by.bsuir.bank.atm.bank.BankGateway;
import by.bsuir.bank.atm.bank.BankReply;
import by.bsuir.bank.atm.bank.Item;
import by.bsuir.bank.atm.bank.Operator;
import by.bsuir.bank.atm.bank.Transaction;
import by.bsuir.bank.atm.session.AtmSession;
import by.bsuir.bank.atm.session.Screen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.client.ResourceAccessException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Модульные тесты конечного автомата банкомата: банк заменён заглушкой, проверяются экраны и транзакции. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AtmSessionTest {

    static final String CARD = "9112380000000010";
    static final String PIN = "1234";

    @Mock
    private BankGateway bank;

    private AtmSession atm;

    @BeforeEach
    void bankAcceptsEverything() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T09:30:00Z"), ZoneId.of("UTC"));
        atm = new AtmSession(bank, "ATM-TEST", clock);
        when(bank.send(any())).thenAnswer(call -> reply(call.getArgument(0)));
        when(bank.operators()).thenReturn(List.of(new Operator("A1", "A1"), new Operator("MTS", "МТС")));
    }

    /** Заглушка банка: PIN 1234 верный, на счёте 1000 BYN. */
    private static BankReply reply(Transaction tx) {
        Map<String, String> items = new java.util.LinkedHashMap<>();
        tx.items().forEach(item -> items.put(item.name(), item.value()));
        if (!PIN.equals(items.get(Item.PIN))) {
            return new BankReply("ERROR", "WRONG_PIN", "Неверный PIN-код", Map.of("attemptsLeft", 2));
        }
        return switch (items.get(Item.OPERATION)) {
            case "AUTHORIZE" -> ok("Авторизация выполнена", Map.of("holder", "Иванов Иван Иванович"));
            case "BALANCE" -> ok("Остаток", Map.of("account", "2400000010012", "balance", 1000.0, "currency", "BYN",
                    "bankDate", "2026-10-01"));
            case "DEPOSIT_BALANCE" -> ok("Вклады", Map.of("bankDate", "2026-10-01", "deposits", List.of(
                    Map.of("number", "Д-000001", "product", "Свободное накопление", "amount", 1500, "currency", "BYN"))));
            case "WITHDRAW" -> Integer.parseInt(items.get(Item.AMOUNT)) > 1000
                    ? new BankReply("ERROR", "INSUFFICIENT_FUNDS", "Недостаточно средств на счёте: доступно 1000.00 BYN", Map.of())
                    : ok("Заберите деньги", Map.of("amount", Integer.parseInt(items.get(Item.AMOUNT)),
                    "balance", 1000 - Integer.parseInt(items.get(Item.AMOUNT)), "currency", "BYN", "bankDate", "2026-10-01"));
            case "PAYMENT" -> ok("Платёж принят", Map.of("operator", "МТС", "phone", items.get(Item.PHONE),
                    "amount", Integer.parseInt(items.get(Item.AMOUNT)), "balance", 975, "currency", "BYN",
                    "reference", "AB12CD34", "bankDate", "2026-10-01"));
            default -> new BankReply("ERROR", "UNKNOWN_OPERATION", "Операция не поддерживается", Map.of());
        };
    }

    private static BankReply ok(String message, Map<String, Object> data) {
        return new BankReply("OK", "OK", message, data);
    }

    @Test
    @DisplayName("Начальный экран: банкомат ждёт карту")
    void initialScreen() {
        Screen screen = atm.screen();

        assertThat(screen.state()).isEqualTo("INSERT_CARD");
        assertThat(screen.title()).isEqualTo("Вставьте, пожалуйста, карту");
        assertThat(screen.input().length()).isEqualTo(16);
        assertThat(screen.cardInside()).isFalse();
        assertThat(screen.buffer()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "123", "911238000000001", "9112380000000011", "911238000000001a"})
    @DisplayName("Номер карты проверяется до обращения в банк: 16 цифр и контрольная цифра")
    void cardNumberIsChecked(String number) {
        Screen screen = atm.enter(number);

        assertThat(screen.state()).isEqualTo("INSERT_CARD");
        assertThat(screen.notice()).isNotBlank();
        verify(bank, never()).send(any());
    }

    @Test
    @DisplayName("Вход: номер карты и PIN-код попадают во внутренний список, банк авторизует карту")
    void login() {
        Screen afterCard = atm.enter("9112 3800 0000 0010");
        assertThat(afterCard.state()).isEqualTo("PIN");
        assertThat(afterCard.cardInside()).isTrue();
        assertThat(afterCard.buffer()).containsExactly(new Item("CARD", CARD));

        Screen menu = atm.enter(PIN);
        assertThat(menu.state()).isEqualTo("MENU");
        assertThat(menu.options()).extracting(Screen.Option::key)
                .containsExactly("WITHDRAW", "BALANCE", "DEPOSIT_BALANCE", "PAYMENT", "EJECT");
        // PIN-код на экран и в журнал обмена выводится звёздочками
        assertThat(menu.buffer()).containsExactly(new Item("CARD", CARD), new Item("PIN", "****"));
        assertThat(menu.exchange().request()).extracting(Item::value).containsExactly(CARD, "****", "AUTHORIZE");
        assertThat(sent().items()).containsExactly(new Item("CARD", CARD), new Item("PIN", PIN), new Item("OPERATION", "AUTHORIZE"));
    }

    @Test
    @DisplayName("Неверный PIN-код запрашивается повторно, после трёх ошибок работа завершается")
    void threeWrongPins() {
        atm.enter(CARD);

        Screen first = atm.enter("0000");
        assertThat(first.state()).isEqualTo("PIN");
        assertThat(first.notice()).isEqualTo("Неверный PIN-код. Осталось попыток: 2");
        assertThat(first.buffer()).containsExactly(new Item("CARD", CARD));

        assertThat(atm.enter("1111").notice()).isEqualTo("Неверный PIN-код. Осталось попыток: 1");

        Screen ejected = atm.enter("2222");
        assertThat(ejected.state()).isEqualTo("INSERT_CARD");
        assertThat(ejected.notice()).startsWith("PIN-код введён неверно три раза");
        assertThat(ejected.cardInside()).isFalse();
        assertThat(ejected.buffer()).isEmpty();
        verify(bank, times(3)).send(any());
    }

    @Test
    @DisplayName("PIN-код из трёх цифр в банк не отправляется")
    void shortPinIsRejectedLocally() {
        atm.enter(CARD);

        assertThat(atm.enter("123").notice()).isEqualTo("PIN-код состоит из 4 цифр");
        verify(bank, never()).send(any());
    }

    @Test
    @DisplayName("Заблокированная карта возвращается клиенту")
    void blockedCardIsEjected() {
        doReturn(new BankReply("ERROR", "CARD_BLOCKED", "Карта заблокирована. Обратитесь в банк", Map.of()))
                .when(bank).send(any());
        atm.enter(CARD);

        Screen screen = atm.enter(PIN);

        assertThat(screen.state()).isEqualTo("INSERT_CARD");
        assertThat(screen.notice()).contains("Карта заблокирована");
    }

    @Test
    @DisplayName("Снятие наличных: список уходит банку целиком и очищается, по желанию печатается чек")
    void withdrawal() {
        login(atm);

        assertThat(atm.select("WITHDRAW").state()).isEqualTo("AMOUNT");
        Screen done = atm.enter("300");

        assertThat(sent().items()).containsExactly(new Item("CARD", CARD), new Item("PIN", PIN),
                new Item("OPERATION", "WITHDRAW"), new Item("AMOUNT", "300"));
        assertThat(done.state()).isEqualTo("RECEIPT_PROMPT");
        assertThat(done.title()).isEqualTo("Заберите деньги");
        assertThat(done.cash()).isEqualTo("300.00 BYN");
        assertThat(done.buffer()).isEmpty();

        Screen menu = atm.select("YES");
        assertThat(menu.state()).isEqualTo("MENU");
        assertThat(menu.receipt()).contains("ВЫДАЧА НАЛИЧНЫХ", "Сумма: 300.00 BYN", "Остаток: 700.00 BYN",
                "Карта **** **** **** 0010", "Банкомат ATM-TEST", "Банковский день: 01.10.2026");
        assertThat(menu.cash()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "000", "12a", "-5", "1.5", "12345678"})
    @DisplayName("Сумма — целое положительное число; иначе запрос в банк не отправляется")
    void amountIsChecked(String amount) {
        login(atm);
        atm.select("WITHDRAW");

        Screen screen = atm.enter(amount);

        assertThat(screen.state()).isEqualTo("AMOUNT");
        assertThat(screen.notice()).isNotBlank();
        verify(bank, times(1)).send(any());                 // только авторизация
    }

    @Test
    @DisplayName("Недостаточно средств: показывается сообщение банка, затем главное меню")
    void insufficientFunds() {
        login(atm);
        atm.select("WITHDRAW");

        Screen message = atm.enter("5000");
        assertThat(message.state()).isEqualTo("MESSAGE");
        assertThat(message.title()).isEqualTo("Операция не выполнена");
        assertThat(message.notice()).startsWith("Недостаточно средств");
        assertThat(message.cash()).isNull();

        assertThat(atm.select("CONTINUE").state()).isEqualTo("MENU");
    }

    @Test
    @DisplayName("Перед следующей операцией PIN-код вводится заново, номер карты банкомат помнит сам")
    void secondOperationRequiresPinAgain() {
        login(atm);
        atm.select("WITHDRAW");
        atm.enter("100");
        atm.select("NO");

        Screen pin = atm.select("BALANCE");
        assertThat(pin.state()).isEqualTo("PIN");
        assertThat(pin.title()).isEqualTo("Введите PIN-код ещё раз");
        assertThat(pin.buffer()).containsExactly(new Item("CARD", CARD));

        Screen prompt = atm.enter(PIN);
        assertThat(prompt.state()).isEqualTo("RECEIPT_PROMPT");
        assertThat(sent().items()).containsExactly(new Item("CARD", CARD), new Item("PIN", PIN), new Item("OPERATION", "BALANCE"));
    }

    @Test
    @DisplayName("Остаток счёта: вопрос о чеке, затем состояние счёта и выбор — продолжить или забрать карту")
    void balanceInquiry() {
        login(atm);

        Screen prompt = atm.select("BALANCE");
        assertThat(prompt.state()).isEqualTo("RECEIPT_PROMPT");
        assertThat(prompt.lines()).containsExactly("Распечатать чек?");

        Screen result = atm.select("YES");
        assertThat(result.state()).isEqualTo("RESULT");
        assertThat(result.lines()).containsExactly("Доступно: 1000.00 BYN");
        assertThat(result.receipt()).contains("ОСТАТОК КРЕДИТНОГО СЧЁТА", "Счёт: 2400000010012", "Доступно: 1000.00 BYN");
        assertThat(result.options()).extracting(Screen.Option::key).containsExactly("CONTINUE", "EJECT");

        Screen ejected = atm.select("EJECT");
        assertThat(ejected.state()).isEqualTo("INSERT_CARD");
        assertThat(ejected.cardInside()).isFalse();
    }

    @Test
    @DisplayName("Остаток депозитного счёта: без чека сразу показываются вклады")
    void depositBalance() {
        login(atm);
        atm.select("DEPOSIT_BALANCE");

        Screen result = atm.select("NO");

        assertThat(result.state()).isEqualTo("RESULT");
        assertThat(result.title()).isEqualTo("Депозитные счета");
        assertThat(result.lines()).containsExactly("Д-000001: 1500.00 BYN");
        assertThat(result.receipt()).isNull();
        assertThat(atm.select("CONTINUE").state()).isEqualTo("MENU");
    }

    @Test
    @DisplayName("Платёж: оператор, номер, сумма, подтверждение; чек печатается автоматически")
    void payment() {
        login(atm);

        Screen operators = atm.select("PAYMENT");
        assertThat(operators.state()).isEqualTo("OPERATOR");
        assertThat(operators.options()).extracting(Screen.Option::label).containsExactly("A1", "МТС");

        assertThat(atm.select("MTS").state()).isEqualTo("PHONE");
        assertThat(atm.enter("029765432").notice()).startsWith("Номер телефона — 10 цифр");
        assertThat(atm.enter("0297654321").state()).isEqualTo("PAY_AMOUNT");

        Screen confirm = atm.enter("25");
        assertThat(confirm.state()).isEqualTo("CONFIRM");
        assertThat(confirm.lines()).containsExactly("Оператор: МТС", "Телефон: 0297654321", "Сумма: 25 BYN");
        verify(bank, times(1)).send(any());                 // до подтверждения в банк ушла только авторизация

        Screen done = atm.select("CONFIRM");
        assertThat(sent().items()).containsExactly(new Item("CARD", CARD), new Item("PIN", PIN),
                new Item("OPERATION", "PAYMENT"), new Item("OPERATOR", "MTS"),
                new Item("PHONE", "0297654321"), new Item("AMOUNT", "25"));
        assertThat(done.state()).isEqualTo("MESSAGE");
        assertThat(done.title()).isEqualTo("Платёж принят");
        assertThat(done.receipt()).contains("ОПЛАТА УСЛУГ СВЯЗИ", "Оператор: МТС", "Телефон: 0297654321",
                "Сумма: 25.00 BYN", "Операция № AB12CD34");
        assertThat(done.buffer()).isEmpty();
    }

    @Test
    @DisplayName("Платёж: «Ввести заново» стирает оператора, номер и сумму, но не данные карты")
    void paymentRetry() {
        login(atm);
        atm.select("PAYMENT");
        atm.select("A1");
        atm.enter("0291112233");
        atm.enter("10");

        Screen again = atm.select("RETRY");

        assertThat(again.state()).isEqualTo("OPERATOR");
        assertThat(again.buffer()).extracting(Item::name).containsExactly("CARD", "PIN", "OPERATION");
    }

    @Test
    @DisplayName("«Отмена» внутри операции возвращает в меню, в меню — возвращает карту")
    void cancel() {
        login(atm);
        atm.select("WITHDRAW");

        Screen menu = atm.cancel();
        assertThat(menu.state()).isEqualTo("MENU");
        assertThat(menu.notice()).isEqualTo("Операция отменена");
        assertThat(menu.buffer()).extracting(Item::name).containsExactly("CARD", "PIN");

        Screen ejected = atm.cancel();
        assertThat(ejected.state()).isEqualTo("INSERT_CARD");
        assertThat(ejected.buffer()).isEmpty();
    }

    @Test
    @DisplayName("Нет связи с банком: авторизация не проходит, PIN-код из списка удаляется")
    void bankIsUnavailable() {
        doThrow(new ResourceAccessException("Connection refused")).when(bank).send(any());
        atm.enter(CARD);

        Screen screen = atm.enter(PIN);

        assertThat(screen.state()).isEqualTo("PIN");
        assertThat(screen.notice()).startsWith("Нет связи с банком");
        assertThat(screen.buffer()).containsExactly(new Item("CARD", CARD));
        assertThat(screen.exchange().reply()).isNull();
    }

    @Test
    @DisplayName("Забрать карту: сеанс завершается, банкомат ждёт следующую карту")
    void eject() {
        login(atm);

        Screen screen = atm.select("EJECT");

        assertThat(screen.state()).isEqualTo("INSERT_CARD");
        assertThat(screen.notice()).startsWith("Заберите карту");
        assertThat(screen.cardInside()).isFalse();
        assertThat(screen.buffer()).isEmpty();
    }

    @Test
    @DisplayName("Экран, сообщения и чек формируются на языке клиента")
    void screensAreLocalized() {
        try {
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            assertThat(atm.screen().title()).isEqualTo("Please insert your card");
            atm.enter(CARD);
            assertThat(atm.enter("0000").notice()).isEqualTo("Wrong PIN. Attempts left: 2");
            Screen menu = atm.enter(PIN);
            assertThat(menu.options()).extracting(Screen.Option::label)
                    .containsExactly("Withdraw cash", "Loan account balance", "Deposit account balance", "Mobile top-up", "Take the card");

            LocaleContextHolder.setLocale(Locale.forLanguageTag("be"));
            atm.select("WITHDRAW");
            Screen done = atm.enter("300");
            assertThat(done.title()).isEqualTo("Забярыце грошы");
            assertThat(atm.select("YES").receipt()).contains("BANKET", "ВЫДАЧА НАЯЎНЫХ", "Сума: 300.00 BYN", "ДЗЯКУЙ!");

            Screen ejected = atm.select("EJECT");
            assertThat(ejected.notice()).startsWith("Забярыце картку");
            assertThat(ejected.noticeOk()).isTrue();
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    static void login(AtmSession atm) {
        atm.enter(CARD);
        atm.enter(PIN);
    }

    private Transaction sent() {
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(bank, atLeastOnce()).send(captor.capture());
        return captor.getValue();
    }
}
