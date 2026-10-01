package by.bsuir.bank.atm.web;

import by.bsuir.bank.atm.bank.DemoCard;
import by.bsuir.bank.atm.session.AtmService;
import by.bsuir.bank.atm.session.AtmService.SessionView;
import by.bsuir.bank.atm.session.Screen;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** REST клиентского интерфейса банкомата: события клавиатуры и боковых кнопок, в ответ — новый экран. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AtmController {

    private final AtmService service;

    public record Entered(String value) {
    }

    public record Selected(String option) {
    }

    /** Подсказка эмулятора: демонстрационные карты и их PIN-коды. */
    @GetMapping("/demo-cards")
    public List<DemoCard> demoCards() {
        return service.demoCards();
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionView open() {
        return service.open();
    }

    @GetMapping("/sessions/{id}")
    public Screen screen(@PathVariable String id) {
        return service.get(id).screen();
    }

    @PostMapping("/sessions/{id}/enter")
    public Screen enter(@PathVariable String id, @RequestBody Entered event) {
        return service.get(id).enter(event.value());
    }

    @PostMapping("/sessions/{id}/select")
    public Screen select(@PathVariable String id, @RequestBody Selected event) {
        return service.get(id).select(event.option());
    }

    @PostMapping("/sessions/{id}/cancel")
    public Screen cancel(@PathVariable String id) {
        return service.get(id).cancel();
    }
}
