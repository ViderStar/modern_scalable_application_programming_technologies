package by.bsuir.bank.account.web;

import by.bsuir.bank.account.dto.DayCloseResult;
import by.bsuir.bank.account.service.BankDayService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/bank-day")
@RequiredArgsConstructor
public class BankDayController {

    private final BankDayService bankDay;

    @GetMapping
    public Map<String, LocalDate> current() {
        return Map.of("date", bankDay.current());
    }

    /** Процедура «Закрытие банковского дня»: закрывает указанное число дней подряд. */
    @PostMapping("/close")
    public DayCloseResult close(@RequestParam(defaultValue = "1") int days) {
        return bankDay.close(days);
    }
}
