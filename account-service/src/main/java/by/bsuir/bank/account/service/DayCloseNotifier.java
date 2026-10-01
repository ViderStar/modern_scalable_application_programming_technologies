package by.bsuir.bank.account.service;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** REST-вызов сервиса-участника закрытия дня: POST {url}/api/internal/day-close. */
@Component
public class DayCloseNotifier {

    private final RestClient http;

    public DayCloseNotifier(RestClient.Builder builder) {
        this.http = builder.build();
    }

    /** Возвращает протокол операций, выполненных участником на дату нового банковского дня. */
    public List<String> dayOpened(String baseUrl, LocalDate date) {
        return http.post()
                .uri(baseUrl + "/api/internal/day-close")
                .body(Map.of("date", date.toString()))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }
}
