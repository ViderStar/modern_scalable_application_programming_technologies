package by.bsuir.bank.account;

import by.bsuir.bank.account.service.BankDayService;
import by.bsuir.bank.account.service.DayCloseNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Интеграционные тесты REST и процедуры закрытия дня; сервисы-участники заменены заглушкой. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid};DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class BankDayIT {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private BankDayService bankDay;
    @MockitoBean
    private DayCloseNotifier notifier;

    @Test
    @DisplayName("Закрытие дня объявляет новую дату каждому участнику и сдвигает банковский день")
    void closesSeveralDays() throws Exception {
        LocalDate start = bankDay.current();
        when(notifier.dayOpened(anyString(), any())).thenReturn(List.of("Начислены проценты"));

        mvc.perform(post("/api/bank-day/close").param("days", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankDate").value(start.plusDays(3).toString()))
                .andExpect(jsonPath("$.events", hasSize(6)));

        verify(notifier).dayOpened(contains("8083"), eq(start.plusDays(1)));
        verify(notifier).dayOpened(contains("8084"), eq(start.plusDays(3)));
        verify(notifier, times(6)).dayOpened(anyString(), any());
        mvc.perform(get("/api/bank-day")).andExpect(jsonPath("$.date").value(start.plusDays(3).toString()));
    }

    @Test
    @DisplayName("Недоступный участник пропускается, день закрывается")
    void unavailableParticipantIsSkipped() throws Exception {
        LocalDate start = bankDay.current();
        when(notifier.dayOpened(anyString(), any())).thenThrow(new ResourceAccessException("Connection refused"));

        mvc.perform(post("/api/bank-day/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankDate").value(start.plusDays(1).toString()))
                .andExpect(jsonPath("$.events", hasItem(containsString("недоступен"))));
    }

    @Test
    @DisplayName("Ошибка участника останавливает закрытие: дата не меняется")
    void participantFailureKeepsDayOpen() throws Exception {
        LocalDate start = bankDay.current();
        when(notifier.dayOpened(anyString(), any())).thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        mvc.perform(post("/api/bank-day/close"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("DAY_CLOSE_FAILED"));

        assertThat(bankDay.current()).isEqualTo(start);
    }

    @Test
    @DisplayName("Количество дней проверяется")
    void rejectsWrongDaysCount() throws Exception {
        mvc.perform(post("/api/bank-day/close").param("days", "0")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Справочники «План счетов» и валют, отчёт по счетам")
    void dictionariesAndReport() throws Exception {
        mvc.perform(get("/api/chart-of-accounts"))
                .andExpect(jsonPath("$[?(@.code == '7327')].activity").value("PASSIVE"))
                .andExpect(jsonPath("$[?(@.code == '1010')].activity").value("ACTIVE"));
        mvc.perform(get("/api/currencies")).andExpect(jsonPath("$[*].code", hasItem("BYN")));
        mvc.perform(get("/api/report"))
                .andExpect(jsonPath("$.accounts", hasSize(6)))
                .andExpect(jsonPath("$.totals[?(@.currency == 'BYN')].credit").value(100000000000.0));
    }

    @Test
    @DisplayName("Запрос на проводку проверяется: сумма положительна, счёт существует")
    void validatesPostingRequest() throws Exception {
        String negative = """
                {"batchKey": "K1", "operations": [{"description": "Тест",
                  "entries": [{"account": "1010000000019", "side": "DEBIT", "amount": -5}]}]}""";
        mvc.perform(post("/api/operations").contentType(MediaType.APPLICATION_JSON).content(negative))
                .andExpect(status().isBadRequest());

        String unknown = """
                {"batchKey": "K2", "operations": [{"description": "Тест",
                  "entries": [{"account": "0000000000000", "side": "DEBIT", "amount": 5}]}]}""";
        mvc.perform(post("/api/operations").contentType(MediaType.APPLICATION_JSON).content(unknown))
                .andExpect(status().isNotFound());
    }
}
