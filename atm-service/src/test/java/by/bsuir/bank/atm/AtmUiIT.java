package by.bsuir.bank.atm;

import by.bsuir.bank.atm.bank.BankGateway;
import by.bsuir.bank.atm.bank.BankReply;
import by.bsuir.bank.atm.bank.DemoCard;
import by.bsuir.bank.atm.bank.Operator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Функциональные тесты интерфейса банкомата через WebDriver; банк заменён заглушкой. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AtmUiIT {

    private static final String PIN = "1234";

    private static WebDriver driver;

    @LocalServerPort
    private int port;
    @MockitoBean
    private BankGateway bank;

    private WebDriverWait wait;

    @BeforeAll
    static void startBrowser() {
        try {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new", "--window-size=1400,1000", "--lang=ru");
            driver = new ChromeDriver(options);
        } catch (Exception e) {
            Assumptions.abort("Chrome недоступен, функциональные тесты пропущены: " + e.getMessage());
        }
    }

    @AfterAll
    static void stopBrowser() {
        if (driver != null) {
            driver.quit();
        }
    }

    @BeforeEach
    void openAtm() {
        when(bank.send(any())).thenAnswer(call -> AtmSessionTestBank.reply(call.getArgument(0)));
        when(bank.operators()).thenReturn(List.of(new Operator("A1", "A1"), new Operator("MTS", "МТС")));
        when(bank.demoCards()).thenReturn(List.of(
                new DemoCard("9112380000000010", PIN, "Иванов Иван Иванович", "К-900001", false)));
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        driver.get("http://localhost:" + port + "/");
        waitForState("INSERT_CARD");
    }

    @Test
    @DisplayName("Клиент вставляет карту, вводит PIN-код, снимает деньги с чеком и забирает карту")
    void withdrawWithReceipt() {
        press("9112380000000010");
        assertThat(driver.findElement(By.id("display-input")).getText()).isEqualTo("9112 3800 0000 0010");
        click("key-enter");
        waitForState("PIN");

        press(PIN);
        assertThat(driver.findElement(By.id("display-input")).getText()).isEqualTo("●●●●");
        click("key-enter");
        waitForState("MENU");
        assertThat(driver.findElement(By.id("buffer")).getText()).contains("CARD", "PIN", "****").doesNotContain(PIN);

        click("opt-WITHDRAW");
        waitForState("AMOUNT");
        press("250");
        click("key-enter");
        waitForState("RECEIPT_PROMPT");
        assertThat(driver.findElement(By.id("display-title")).getText()).isEqualTo("Заберите деньги");
        assertThat(driver.findElement(By.id("cash")).getText()).isEqualTo("250.00 BYN");

        click("opt-YES");
        waitForState("MENU");
        assertThat(driver.findElement(By.id("receipt")).getText())
                .contains("ВЫДАЧА НАЛИЧНЫХ", "Сумма: 250.00 BYN", "Остаток: 750.00 BYN");

        click("opt-EJECT");
        waitForState("INSERT_CARD");
        assertThat(driver.findElement(By.id("notice")).getText()).startsWith("Заберите карту");
    }

    @Test
    @DisplayName("Три неверных PIN-кода с клавиатуры — работа с банкоматом завершается")
    void threeWrongPins() {
        driver.findElement(By.tagName("body")).sendKeys("9112380000000010", Keys.ENTER);
        waitForState("PIN");

        for (int attempt = 2; attempt >= 1; attempt--) {
            driver.findElement(By.tagName("body")).sendKeys("0000", Keys.ENTER);
            wait.until(ExpectedConditions.textToBe(By.id("notice"), "Неверный PIN-код. Осталось попыток: " + attempt));
        }
        driver.findElement(By.tagName("body")).sendKeys("0000", Keys.ENTER);

        waitForState("INSERT_CARD");
        assertThat(driver.findElement(By.id("notice")).getText()).startsWith("PIN-код введён неверно три раза");
    }

    @Test
    @DisplayName("Оплата мобильной связи с подтверждением данных и автоматической печатью чека")
    void payment() {
        press("9112380000000010");
        click("key-enter");
        waitForState("PIN");
        press(PIN);
        click("key-enter");
        waitForState("MENU");

        click("opt-PAYMENT");
        waitForState("OPERATOR");
        click("opt-MTS");
        waitForState("PHONE");
        press("0297654321");
        click("key-enter");
        waitForState("PAY_AMOUNT");
        press("25");
        click("key-enter");
        waitForState("CONFIRM");
        assertThat(driver.findElement(By.id("display-lines")).getText())
                .contains("Оператор: МТС", "Телефон: 0297654321", "Сумма: 25 BYN");

        click("opt-CONFIRM");
        waitForState("MESSAGE");
        assertThat(driver.findElement(By.id("display-title")).getText()).isEqualTo("Платёж принят");
        assertThat(driver.findElement(By.id("receipt")).getText()).contains("ОПЛАТА УСЛУГ СВЯЗИ", "Операция № AB12CD34");
    }

    @Test
    @DisplayName("Демо-карта вставляется кнопкой, интерфейс переключается на английский и белорусский")
    void demoCardAndLanguages() {
        try {
            assertThat(driver.findElement(By.id("demo-pin-0")).getText()).isEqualTo(PIN);
            click("demo-insert-0");
            assertThat(driver.findElement(By.id("display-input")).getText()).isEqualTo("9112 3800 0000 0010");
            click("key-enter");
            waitForState("PIN");

            click("lang-en");
            wait.until(ExpectedConditions.textToBe(By.id("display-title"), "Please insert your card"));
            assertThat(driver.findElement(By.id("key-enter")).getText()).isEqualToIgnoringCase("Enter");
            assertThat(driver.getTitle()).isEqualTo("ATM — BankEt");

            click("lang-be");
            wait.until(ExpectedConditions.textToBe(By.id("display-title"), "Устаўце, калі ласка, картку"));
            press("123");
            click("key-enter");
            wait.until(ExpectedConditions.textToBe(By.id("notice"), "Нумар карткі складаецца з 16 лічбаў"));
        } finally {
            driver.manage().deleteCookieNamed("bank_lang");
        }
    }

    private void press(String digits) {
        for (char digit : digits.toCharArray()) {
            click("key-" + digit);
        }
    }

    private void click(String id) {
        wait.until(ExpectedConditions.elementToBeClickable(By.id(id))).click();
    }

    private void waitForState(String state) {
        wait.until(ExpectedConditions.textToBe(By.id("state"), state));
        wait.until(ExpectedConditions.attributeToBe(By.id("display"), "data-state", state));
    }

    /** Та же заглушка банка, что и в модульных тестах автомата. */
    static final class AtmSessionTestBank {

        static BankReply reply(by.bsuir.bank.atm.bank.Transaction tx) {
            Map<String, String> items = new java.util.LinkedHashMap<>();
            tx.items().forEach(item -> items.put(item.name(), item.value()));
            if (!PIN.equals(items.get("PIN"))) {
                return new BankReply("ERROR", "WRONG_PIN", "Неверный PIN-код", Map.of());
            }
            return switch (items.get("OPERATION")) {
                case "WITHDRAW" -> new BankReply("OK", "OK", "Заберите деньги", Map.of(
                        "amount", Integer.parseInt(items.get("AMOUNT")),
                        "balance", 1000 - Integer.parseInt(items.get("AMOUNT")), "currency", "BYN", "bankDate", "2026-10-01"));
                case "PAYMENT" -> new BankReply("OK", "OK", "Платёж принят", Map.of("operator", "МТС",
                        "phone", items.get("PHONE"), "amount", Integer.parseInt(items.get("AMOUNT")), "balance", 975,
                        "currency", "BYN", "reference", "AB12CD34", "bankDate", "2026-10-01"));
                default -> new BankReply("OK", "OK", "Авторизация выполнена", Map.of("holder", "Иванов Иван Иванович"));
            };
        }
    }
}
