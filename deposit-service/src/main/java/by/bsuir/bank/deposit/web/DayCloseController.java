package by.bsuir.bank.deposit.web;

import by.bsuir.bank.deposit.service.DayCloseProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Внутренний REST для сервиса «Счета»: обработка открытия нового банковского дня. */
@RestController
@RequiredArgsConstructor
public class DayCloseController {

    private final DayCloseProcessor processor;

    public record DayOpened(LocalDate date) {
    }

    @PostMapping("/api/internal/day-close")
    public List<String> dayOpened(@RequestBody DayOpened event) {
        return processor.process(event.date());
    }
}
