package by.bsuir.bank.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционные тесты REST-сервиса на базе H2 в памяти: запрос идёт мимо web-клиента,
 * поэтому проверяется именно серверная валидация. После каждого запроса состояние
 * таблицы client проверяется SQL-запросом «прямо в базе».
 */
// своя база на каждый класс тестов: контексты не делят состояние
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid};DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class ClientApiIT {

    private static final int SEEDED = 6;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("База заполняется при старте, список отсортирован по фамилии")
    void listIsSortedByLastName() throws Exception {
        mvc.perform(get("/api/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(SEEDED)))
                .andExpect(jsonPath("$[*].lastName",
                        contains("Абрамович", "Жуков", "Ковальчук", "Лебедевич", "Савицкая", "Шевчук")));
    }

    @Test
    @DisplayName("Справочники отдаются для выпадающих списков, городов не меньше пяти")
    void dictionariesAreAvailable() throws Exception {
        mvc.perform(get("/api/dictionaries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cities.length()").value(8))
                .andExpect(jsonPath("$.maritalStatuses.length()").value(4))
                .andExpect(jsonPath("$.citizenships.length()").value(5))
                .andExpect(jsonPath("$.disabilities.length()").value(4));
    }

    @Test
    @DisplayName("п.1 Повторный ввод тех же данных — ошибка, в базе один клиент")
    void sameClientTwice() throws Exception {
        create(TestClients.valid()).andExpect(status().isCreated());

        create(TestClients.valid())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fields.passportNumber").exists())
                .andExpect(jsonPath("$.fields.identificationNumber").exists());

        assertThat(count("last_name = 'Иванов'")).isEqualTo(1);
    }

    @Test
    @DisplayName("п.2 Другой клиент с тем же паспортом — ошибка, паспорт в базе один")
    void anotherClientWithSamePassport() throws Exception {
        create(TestClients.valid()).andExpect(status().isCreated());

        Map<String, Object> other = TestClients.valid();
        other.put("lastName", "Петров");
        other.put("firstName", "Пётр");
        other.put("identificationNumber", "3010185A001PB9");
        create(other)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fields.passportSeries").exists())
                .andExpect(jsonPath("$.fields.passportNumber").exists())
                .andExpect(jsonPath("$.fields.identificationNumber").doesNotExist());

        assertThat(count("passport_series = 'MP' and passport_number = '7654321'")).isEqualTo(1);
        assertThat(count("last_name = 'Петров'")).isZero();
    }

    @Test
    @DisplayName("п.3 Другой клиент с тем же идентификационным номером — ошибка")
    void anotherClientWithSameIdentificationNumber() throws Exception {
        create(TestClients.valid()).andExpect(status().isCreated());

        Map<String, Object> other = TestClients.valid();
        other.put("lastName", "Петров");
        other.put("passportNumber", "1111111");
        create(other)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fields.identificationNumber").exists())
                .andExpect(jsonPath("$.fields.passportNumber").doesNotExist());

        assertThat(count("identification_number = '3170590A077PB4'")).isEqualTo(1);
    }

    @Test
    @DisplayName("п.4 Фамилия «1234» — ошибка, клиента 1234 в базе нет")
    void digitsInsteadOfLastName() throws Exception {
        create(TestClients.with("lastName", "1234"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.lastName").value("Допустимы только буквы"));

        assertThat(count("last_name = '1234'")).isZero();
    }

    @Test
    @DisplayName("п.5 Пробел вместо имени — ошибка, клиент не записан")
    void spaceInsteadOfFirstName() throws Exception {
        create(TestClients.with("firstName", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.firstName").value("Обязательное поле"));

        assertThat(count("last_name = 'Иванов'")).isZero();
    }

    @ParameterizedTest(name = "дата рождения «{0}»")
    @ValueSource(strings = {"не дата", "31.02.2015", "29.02.2015"})
    @DisplayName("п.6 Текст, не являющийся датой, — ошибка")
    void birthDateIsNotDate(String birthDate) throws Exception {
        create(TestClients.with("birthDate", birthDate))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.birthDate").exists());

        assertThat(count("last_name = 'Иванов'")).isZero();
    }

    @ParameterizedTest(name = "{0} = «{1}»")
    @CsvSource(delimiter = '|', value = {
            "passportSeries|мр",
            "passportNumber|12345",
            "identificationNumber|3170590-077PB4",
            "homePhone|2014567",
            "mobilePhone|+375 (17) 765-43-21",
            "issueDate|30.02.2018",
            "email|ivanov@",
    })
    @DisplayName("п.6 Поля с маской проверяются на сервере")
    void maskedFieldsAreValidatedByService(String field, String value) throws Exception {
        create(TestClients.with(field, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields." + field).exists());

        assertThat(count("last_name = 'Иванов'")).isZero();
    }

    @ParameterizedTest(name = "без поля {0}")
    @MethodSource("requiredFields")
    @DisplayName("п.7 Без любого обязательного поля запись не выполняется")
    void requiredFieldIsMissing(String field) throws Exception {
        Map<String, Object> client = TestClients.valid();
        client.remove(field);

        create(client)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields." + field).value("Обязательное поле"));

        assertThat(count("1 = 1")).isEqualTo(SEEDED);
    }

    static List<String> requiredFields() {
        return TestClients.REQUIRED_FIELDS;
    }

    @Test
    @DisplayName("п.8 Только обязательные поля — клиент записан")
    void onlyRequiredFields() throws Exception {
        create(TestClients.onlyRequired())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.fullName").value("Иванов Иван Иванович"))
                .andExpect(jsonPath("$.mobilePhone").isEmpty())
                .andExpect(jsonPath("$.monthlyIncome").isEmpty());

        assertThat(count("last_name = 'Иванов' and home_phone is null and email is null")).isEqualTo(1);
    }

    @Test
    @DisplayName("Текст в денежном поле — ошибка разбора запроса с указанием поля")
    void textInMoneyField() throws Exception {
        create(TestClients.with("monthlyIncome", "много"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.monthlyIncome").exists());
    }

    @Test
    @DisplayName("Редактирование: изменения сохраняются, чужой паспорт занять нельзя")
    void update() throws Exception {
        long id = jdbc.queryForObject("select id from client where last_name = 'Жуков'", Long.class);

        Map<String, Object> changed = TestClients.valid();
        changed.put("lastName", "Жуков-Новый");
        mvc.perform(put("/api/clients/" + id).contentType(MediaType.APPLICATION_JSON).content(TestClients.json(changed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Жуков-Новый"));
        assertThat(count("last_name = 'Жуков-Новый' and passport_number = '7654321'")).isEqualTo(1);

        // паспорт клиента Лебедевич (MP 3141592) уже занят
        changed.put("passportNumber", "3141592");
        mvc.perform(put("/api/clients/" + id).contentType(MediaType.APPLICATION_JSON).content(TestClients.json(changed)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Удаление клиента")
    void remove() throws Exception {
        long id = jdbc.queryForObject("select id from client where last_name = 'Шевчук'", Long.class);

        mvc.perform(delete("/api/clients/" + id)).andExpect(status().isNoContent());
        assertThat(count("1 = 1")).isEqualTo(SEEDED - 1);

        mvc.perform(delete("/api/clients/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/clients/" + id)).andExpect(status().isNotFound());
    }

    private ResultActions create(Map<String, Object> client) throws Exception {
        return mvc.perform(post("/api/clients").contentType(MediaType.APPLICATION_JSON).content(TestClients.json(client)));
    }

    /** Проверка «прямо в базе»: SQL-запрос к таблице client в обход сервиса и репозиториев. */
    private int count(String where) {
        return jdbc.queryForObject("select count(*) from client where " + where, Integer.class);
    }
}
