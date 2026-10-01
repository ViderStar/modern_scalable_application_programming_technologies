package by.bsuir.bank.client;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Функциональные тесты через WebDriver: настоящий браузер (Chrome без окна) открывает web-клиент
 * поднятого сервиса и проходит программу защиты. Результат каждого шага сверяется с таблицей client в H2.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid};DB_CLOSE_DELAY=-1")
class ClientUiIT {

    private static WebDriver driver;

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcTemplate jdbc;

    private WebDriverWait wait;

    @BeforeAll
    static void startBrowser() {
        try {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new", "--window-size=1400,1600", "--lang=ru");
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
    void openNewClientForm() {
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        driver.get("http://localhost:" + port + "/");
        wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(By.cssSelector("#clients tbody tr"), 0));
        driver.findElement(By.id("btn-add")).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("btn-save")));
    }

    @AfterEach
    void removeCreatedClients() {
        jdbc.update("delete from client where id > 6");
    }

    @Test
    @DisplayName("Форма «Список клиентов» отсортирована по фамилии")
    void listIsSortedByLastName() {
        driver.findElement(By.id("btn-cancel")).click();
        List<String> names = driver.findElements(By.cssSelector("#clients td.fio")).stream()
                .map(WebElement::getText).toList();
        assertThat(names).hasSize(6).isSorted();
        assertThat(names.get(3)).isEqualTo("Лебедевич Артем Владимирович");
    }

    @Test
    @DisplayName("п.1 Те же данные ещё раз — сообщение об ошибке, в базе один клиент")
    void sameClientTwice() {
        fill(TestClients.valid());
        save();
        waitForNotice("Клиент добавлен");

        driver.findElement(By.id("btn-add")).click();
        fill(TestClients.valid());
        save();

        assertThat(formAlert()).isEqualTo("Такой клиент уже есть в базе");
        assertThat(error("passportNumber")).isEqualTo("Клиент с таким паспортом уже зарегистрирован");
        assertThat(count("last_name = 'Иванов'")).isEqualTo(1);
    }

    @Test
    @DisplayName("п.2 Другой клиент с паспортом студента — сообщение об ошибке")
    void anotherClientWithSamePassport() {
        Map<String, Object> other = TestClients.valid();
        other.put("passportSeries", "MP");
        other.put("passportNumber", "3141592");          // паспорт клиента Лебедевич из начальных данных
        fill(other);
        save();

        assertThat(error("passportSeries")).isEqualTo("Клиент с таким паспортом уже зарегистрирован");
        assertThat(count("passport_series = 'MP' and passport_number = '3141592'")).isEqualTo(1);
        assertThat(count("last_name = 'Иванов'")).isZero();
    }

    @Test
    @DisplayName("п.3 Другой клиент с идентификационным номером студента — сообщение об ошибке")
    void anotherClientWithSameIdentificationNumber() {
        fill(TestClients.with("identificationNumber", "3140301A001PB5"));
        save();

        assertThat(error("identificationNumber")).isEqualTo("Клиент с таким идентификационным номером уже зарегистрирован");
        assertThat(count("identification_number = '3140301A001PB5'")).isEqualTo(1);
        assertThat(count("last_name = 'Иванов'")).isZero();
    }

    @Test
    @DisplayName("п.4 Фамилия «1234» — сообщение об ошибке, в базе такой фамилии нет")
    void digitsInsteadOfLastName() {
        fill(TestClients.with("lastName", "1234"));
        save();

        assertThat(error("lastName")).isEqualTo("Допустимы только буквы");
        assertThat(count("last_name = '1234'")).isZero();
    }

    @Test
    @DisplayName("п.5 Пробел вместо имени — сообщение об ошибке, клиент не записан")
    void spaceInsteadOfFirstName() {
        fill(TestClients.with("firstName", " "));
        save();

        assertThat(error("firstName")).isEqualTo("Обязательное поле");
        assertThat(count("last_name = 'Иванов'")).isZero();
    }

    @ParameterizedTest(name = "дата рождения «{0}»")
    @CsvSource(delimiter = '|', value = {
            "не дата|Обязательное поле",                       // маска не пропускает буквы — поле остаётся пустым
            "31.02.2015|Такой даты не существует, формат ДД.ММ.ГГГГ",
            "29.02.2015|Такой даты не существует, формат ДД.ММ.ГГГГ",
            "15.07.20|Такой даты не существует, формат ДД.ММ.ГГГГ",
    })
    @DisplayName("п.6 В поле даты рождения текст, не являющийся датой")
    void birthDateIsNotDate(String typed, String message) {
        fill(TestClients.with("birthDate", typed));
        save();

        assertThat(error("birthDate")).isEqualTo(message);
        assertThat(count("last_name = 'Иванов'")).isZero();
    }

    @ParameterizedTest(name = "{0}: «{1}»")
    @CsvSource(delimiter = '|', value = {
            "passportSeries|M|Серия — две заглавные латинские буквы",
            "passportNumber|12345|Номер паспорта — семь цифр",
            "identificationNumber|3170590A077|Формат: 7 цифр, буква, 3 цифры, 2 буквы, цифра",
            "homePhone|20145|Формат: 293-88-44",
            "mobilePhone|+375 (17) 765-43-21|Формат: +375 (29) 314-15-92, код 25, 29, 33 или 44",
            "issueDate|30.02.2018|Такой даты не существует, формат ДД.ММ.ГГГГ",
    })
    @DisplayName("п.6 Поля с маской: неполное или неверное значение")
    void maskedFields(String field, String typed, String message) {
        fill(TestClients.with(field, typed));
        save();

        assertThat(error(field)).isEqualTo(message);
        assertThat(count("last_name = 'Иванов'")).isZero();
    }

    @Test
    @DisplayName("Маска ввода отбрасывает недопустимые символы и расставляет разделители")
    void masksFilterInput() {
        type("passportNumber", "12ab34567999");
        type("passportSeries", "mp1");
        type("birthDate", "17051990");
        type("mobilePhone", "297654321");

        assertThat(value("passportNumber")).isEqualTo("1234567");
        assertThat(value("passportSeries")).isEqualTo("MP");
        assertThat(value("birthDate")).isEqualTo("17.05.1990");
        assertThat(value("mobilePhone")).isEqualTo("+375 (29) 765-43-21");
    }

    @ParameterizedTest(name = "без поля {0}")
    @ValueSource(strings = {
            "lastName", "firstName", "middleName", "birthDate", "sex", "passportSeries", "passportNumber",
            "issuedBy", "issueDate", "identificationNumber", "birthPlace", "residenceCityId", "residenceAddress",
            "registrationCityId", "maritalStatusId", "citizenshipId", "disabilityId"})
    @DisplayName("п.7 Не заполнено одно обязательное поле — сообщение об ошибке, записи нет")
    void requiredFieldIsMissing(String field) {
        Map<String, Object> client = TestClients.valid();
        client.remove(field);
        fill(client);
        save();

        assertThat(error(field)).isEqualTo("Обязательное поле");
        assertThat(count("1 = 1")).isEqualTo(6);
    }

    @Test
    @DisplayName("п.8 Только обязательные поля — клиент записан и появился в списке")
    void onlyRequiredFields() {
        fill(TestClients.onlyRequired());
        save();
        waitForNotice("Клиент добавлен");

        assertThat(driver.findElement(By.id("clients")).getText()).contains("Иванов Иван Иванович");
        assertThat(count("last_name = 'Иванов' and mobile_phone is null and monthly_income is null")).isEqualTo(1);
    }

    @Test
    @DisplayName("Редактирование и удаление клиента через web-клиент")
    void editAndDelete() {
        fill(TestClients.valid());
        save();
        waitForNotice("Клиент добавлен");

        row("Иванов Иван Иванович").findElement(By.cssSelector("button.edit")).click();
        wait.until(ExpectedConditions.attributeToBe(By.id("f-lastName"), "value", "Иванов"));
        assertThat(value("monthlyIncome")).isEqualTo("2500.50");
        type("lastName", "Сидоров");
        save();
        waitForNotice("Изменения сохранены");
        assertThat(count("last_name = 'Сидоров' and passport_number = '7654321'")).isEqualTo(1);

        row("Сидоров Иван Иванович").findElement(By.cssSelector("button.delete")).click();
        wait.until(ExpectedConditions.alertIsPresent()).accept();
        waitForNotice("Клиент удалён");
        assertThat(count("last_name = 'Сидоров'")).isZero();
    }

    // ---------- работа со страницей ----------

    private void fill(Map<String, Object> client) {
        client.forEach((field, value) -> {
            switch (field) {
                case "sex" -> driver.findElement(By.id("f-sex-" + value)).click();
                case "pensioner" -> {
                    if (Boolean.TRUE.equals(value)) {
                        driver.findElement(By.id("f-pensioner")).click();
                    }
                }
                case "residenceCityId", "registrationCityId", "maritalStatusId", "citizenshipId", "disabilityId" ->
                        new Select(driver.findElement(By.id("f-" + field))).selectByIndex((Integer) value);
                default -> type(field, String.valueOf(value));
            }
        });
    }

    private void type(String field, String text) {
        WebElement input = driver.findElement(By.id("f-" + field));
        input.clear();
        input.sendKeys(text);
    }

    private String value(String field) {
        return driver.findElement(By.id("f-" + field)).getDomProperty("value");
    }

    private void save() {
        driver.findElement(By.id("btn-save")).click();
    }

    private String error(String field) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("e-" + field))).getText();
    }

    private String formAlert() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("form-alert"))).getText();
    }

    private void waitForNotice(String text) {
        wait.until(ExpectedConditions.textToBe(By.id("notice"), text));
    }

    private WebElement row(String fullName) {
        return driver.findElement(By.xpath("//table[@id='clients']//tr[td[normalize-space()='" + fullName + "']]"));
    }

    /** Проверка «прямо в базе»: SQL-запрос к таблице client. */
    private int count(String where) {
        return jdbc.queryForObject("select count(*) from client where " + where, Integer.class);
    }
}
