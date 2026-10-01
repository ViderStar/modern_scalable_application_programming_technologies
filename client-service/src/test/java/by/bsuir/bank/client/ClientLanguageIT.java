package by.bsuir.bank.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Интеграционные тесты локализации: язык ответа задаётся заголовком Accept-Language. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid};DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class ClientLanguageIT {

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest(name = "{0}: {1}, {2}")
    @CsvSource({
            "ru, Обязательное поле, Допустимы только буквы",
            "en, Required field, Letters only",
            "be, Абавязковае поле, Дапушчальныя толькі літары",
            "en-US, Required field, Letters only",
            "fr, Обязательное поле, Допустимы только буквы",
    })
    @DisplayName("Сообщения валидации приходят на языке запроса, неизвестный язык заменяется русским")
    void validationMessages(String language, String required, String lettersOnly) throws Exception {
        var client = TestClients.with("lastName", "1234");
        client.remove("firstName");

        mvc.perform(post("/api/clients").header(HttpHeaders.ACCEPT_LANGUAGE, language)
                        .contentType(MediaType.APPLICATION_JSON).content(TestClients.json(client)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.firstName").value(required))
                .andExpect(jsonPath("$.fields.lastName").value(lettersOnly));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
            "ru, Минск, Женат / замужем, Республика Беларусь",
            "en, Minsk, Married, Republic of Belarus",
            "be, Мінск, Жанаты / замужам, Рэспубліка Беларусь",
    })
    @DisplayName("Справочники отдаются на языке запроса")
    void dictionaries(String language, String city, String maritalStatus, String citizenship) throws Exception {
        mvc.perform(get("/api/dictionaries").header(HttpHeaders.ACCEPT_LANGUAGE, language))
                .andExpect(jsonPath("$.cities[*].name", hasItem(city)))
                .andExpect(jsonPath("$.maritalStatuses[*].name", hasItem(maritalStatus)))
                .andExpect(jsonPath("$.citizenships[0].name").value(citizenship))
                .andExpect(jsonPath("$.cities[0].nameEn").doesNotExist());
    }

    @Test
    @DisplayName("Ошибка уникальности и данные клиента — на белорусском языке")
    void businessMessages() throws Exception {
        var duplicate = TestClients.with("passportNumber", "3141592");

        mvc.perform(post("/api/clients").header(HttpHeaders.ACCEPT_LANGUAGE, "be")
                        .contentType(MediaType.APPLICATION_JSON).content(TestClients.json(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Такі кліент ужо ёсць у базе"))
                .andExpect(jsonPath("$.fields.passportNumber").value("Кліент з такім пашпартам ужо зарэгістраваны"));

        mvc.perform(get("/api/clients").header(HttpHeaders.ACCEPT_LANGUAGE, "be"))
                .andExpect(jsonPath("$[0].residenceCity").value("Гродна"))
                .andExpect(jsonPath("$[0].lastName").value("Абрамович"));
    }
}
