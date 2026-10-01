package by.bsuir.bank.account;

import by.bsuir.bank.account.service.DayCloseNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Интеграционные тесты локализации главной книги: справочники, хранимые коды проводок, сообщения. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid};DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class LedgerLanguageIT {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private DayCloseNotifier notifier;

    @Test
    @DisplayName("Отчёт по счетам: наименования счетов банка, плана счетов и активность — на языке запроса")
    void reportIsLocalized() throws Exception {
        mvc.perform(get("/api/report"))
                .andExpect(jsonPath("$.accounts[0].name").value("Касса банка, BYN"))
                .andExpect(jsonPath("$.accounts[0].chartName").value("Денежные средства в кассе"))
                .andExpect(jsonPath("$.accounts[0].activityTitle").value("Активный"));
        mvc.perform(get("/api/report").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(jsonPath("$.accounts[0].name").value("Bank cash desk, BYN"))
                .andExpect(jsonPath("$.accounts[0].chartName").value("Cash on hand"))
                .andExpect(jsonPath("$.accounts[0].activityTitle").value("Active"));
        mvc.perform(get("/api/report").header(HttpHeaders.ACCEPT_LANGUAGE, "be"))
                .andExpect(jsonPath("$.accounts[0].name").value("Каса банка, BYN"))
                .andExpect(jsonPath("$.accounts[0].chartName").value("Грашовыя сродкі ў касе"))
                .andExpect(jsonPath("$.accounts[0].activityTitle").value("Актыўны"));
    }

    @Test
    @DisplayName("Одна и та же проводка читается на трёх языках: в базе хранится её код")
    void postingDescriptionIsRenderedOnRead() throws Exception {
        mvc.perform(get("/api/operations"))
                .andExpect(jsonPath("$[*].description", hasItem("Формирование фонда развития банка")));
        mvc.perform(get("/api/operations").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(jsonPath("$[*].description", hasItem("Bank development fund formed")))
                .andExpect(jsonPath("$[0].entries[0].accountName").value("Bank development fund, EUR"));
        mvc.perform(get("/api/operations").header(HttpHeaders.ACCEPT_LANGUAGE, "be"))
                .andExpect(jsonPath("$[*].description", hasItem("Фарміраванне фонду развіцця банка")));
    }

    @Test
    @DisplayName("Справочники «План счетов» и валют на английском и белорусском")
    void dictionaries() throws Exception {
        mvc.perform(get("/api/chart-of-accounts").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(jsonPath("$[?(@.code == '7327')].name").value("Bank development fund"))
                .andExpect(jsonPath("$[?(@.code == '7327')].activityTitle").value("Passive"))
                .andExpect(jsonPath("$[0].nameBe").doesNotExist());
        mvc.perform(get("/api/currencies").header(HttpHeaders.ACCEPT_LANGUAGE, "be"))
                .andExpect(jsonPath("$[?(@.code == 'BYN')].name").value("Беларускі рубель"));
    }

    @Test
    @DisplayName("Отказ в проводке и протокол закрытия дня — на языке запроса")
    void errorsAndEvents() throws Exception {
        String overdraft = """
                {"batchKey": "LANG-1", "operations": [{"description": "@posting.deposit.cashOut",
                  "entries": [{"account": "1010000000019", "side": "CREDIT", "amount": 5}]}]}""";
        mvc.perform(post("/api/operations").header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON).content(overdraft))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient funds on account 1010000000019 "
                        + "(\"Bank cash desk, BYN\") for operation \"Cash paid out from the cash desk\""));

        when(notifier.dayOpened(anyString(), any())).thenThrow(new ResourceAccessException("Connection refused"));
        mvc.perform(post("/api/bank-day/close").header(HttpHeaders.ACCEPT_LANGUAGE, "be"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0]").value(org.hamcrest.Matchers.endsWith(
                        "сэрвіс «Дэпазіты» недаступны, апрацоўка адкладзена")));
    }
}
