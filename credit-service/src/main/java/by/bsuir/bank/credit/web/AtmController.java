package by.bsuir.bank.credit.web;

import by.bsuir.bank.credit.atm.AtmProcessor;
import by.bsuir.bank.credit.atm.AtmRequest;
import by.bsuir.bank.credit.atm.AtmResponse;
import by.bsuir.bank.credit.domain.MobileOperator;
import by.bsuir.bank.credit.service.DemoCards;
import by.bsuir.bank.credit.service.DemoCards.DemoCardView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Точка входа протокола «банк — банкомат». */
@RestController
@RequestMapping("/api/atm")
@RequiredArgsConstructor
public class AtmController {

    private final AtmProcessor processor;
    private final DemoCards demoCards;

    @PostMapping("/transactions")
    public AtmResponse transaction(@Valid @RequestBody AtmRequest request) {
        return processor.handle(request);
    }

    @GetMapping("/operators")
    public List<MobileOperator> operators() {
        return processor.operators();
    }

    /** Демонстрационные карты с PIN-кодами — подсказка для знакомства с банкоматом. */
    @GetMapping("/demo-cards")
    public List<DemoCardView> demoCards() {
        return demoCards.list();
    }
}
