package by.bsuir.bank.atm;

import by.bsuir.bank.atm.bank.BankGateway;
import by.bsuir.bank.atm.bank.BankReply;
import by.bsuir.bank.atm.bank.DemoCard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Интеграционные тесты REST клиентского интерфейса банкомата; банк заменён заглушкой. */
@SpringBootTest
@AutoConfigureMockMvc
class AtmApiIT {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @MockitoBean
    private BankGateway bank;

    @Test
    @DisplayName("Сеанс: открытие, ввод карты и PIN-кода, запрос остатка")
    void sessionFlow() throws Exception {
        when(bank.send(any())).thenReturn(
                new BankReply("OK", "OK", "Авторизация выполнена", Map.of("holder", "Иванов Иван Иванович")),
                new BankReply("OK", "OK", "Остаток", Map.of("account", "2400000010012", "balance", 750.5,
                        "currency", "BYN", "bankDate", "2026-10-01")));
        String id = openSession();

        event(id, "enter", "{\"value\": \"9112380000000010\"}").andExpect(jsonPath("$.state").value("PIN"));
        event(id, "enter", "{\"value\": \"1234\"}")
                .andExpect(jsonPath("$.state").value("MENU"))
                .andExpect(jsonPath("$.options", hasSize(5)))
                .andExpect(jsonPath("$.buffer[1].value").value("****"));
        event(id, "select", "{\"option\": \"BALANCE\"}").andExpect(jsonPath("$.state").value("RECEIPT_PROMPT"));
        event(id, "select", "{\"option\": \"NO\"}")
                .andExpect(jsonPath("$.state").value("RESULT"))
                .andExpect(jsonPath("$.lines[0]").value("Доступно: 750.50 BYN"));

        mvc.perform(get("/api/sessions/" + id)).andExpect(jsonPath("$.state").value("RESULT"));
        mvc.perform(post("/api/sessions/" + id + "/cancel")).andExpect(jsonPath("$.state").value("MENU"));
    }

    @Test
    @DisplayName("Сеансы независимы друг от друга")
    void sessionsAreIsolated() throws Exception {
        String first = openSession();
        String second = openSession();

        event(first, "enter", "{\"value\": \"9112380000000010\"}").andExpect(jsonPath("$.state").value("PIN"));

        mvc.perform(get("/api/sessions/" + second)).andExpect(jsonPath("$.state").value("INSERT_CARD"));
    }

    @Test
    @DisplayName("Неизвестный сеанс — 404")
    void unknownSession() throws Exception {
        mvc.perform(get("/api/sessions/no-such-session"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Экран приходит на языке запроса")
    void screenLanguage() throws Exception {
        String id = openSession();

        mvc.perform(get("/api/sessions/" + id).header("Accept-Language", "en"))
                .andExpect(jsonPath("$.title").value("Please insert your card"));
        mvc.perform(post("/api/sessions/" + id + "/enter").header("Accept-Language", "be")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\": \"123\"}"))
                .andExpect(jsonPath("$.title").value("Устаўце, калі ласка, картку"))
                .andExpect(jsonPath("$.notice").value("Нумар карткі складаецца з 16 лічбаў"));
    }

    @Test
    @DisplayName("Демо-карты запрашиваются у банка; если банк недоступен, подсказка пуста")
    void demoCards() throws Exception {
        when(bank.demoCards()).thenReturn(List.of(
                new DemoCard("9112380000000010", "1111", "Иванов Иван Иванович", "К-900001", false)));
        mvc.perform(get("/api/demo-cards"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].pin").value("1111"));

        when(bank.demoCards()).thenThrow(new ResourceAccessException("Connection refused"));
        mvc.perform(get("/api/demo-cards")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    private String openSession() throws Exception {
        String response = mvc.perform(post("/api/sessions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.screen.state").value("INSERT_CARD"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asText();
    }

    private ResultActions event(String id, String action, String body) throws Exception {
        return mvc.perform(post("/api/sessions/" + id + "/" + action).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }
}
