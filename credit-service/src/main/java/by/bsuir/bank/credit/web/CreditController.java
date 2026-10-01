package by.bsuir.bank.credit.web;

import by.bsuir.bank.common.ledger.AccountInfo;
import by.bsuir.bank.credit.dto.CashRequest;
import by.bsuir.bank.credit.dto.CreditDetails;
import by.bsuir.bank.credit.dto.CreditIssued;
import by.bsuir.bank.credit.dto.CreditRequest;
import by.bsuir.bank.credit.dto.CreditView;
import by.bsuir.bank.credit.dto.PinEnvelope;
import by.bsuir.bank.credit.dto.ScheduleRequest;
import by.bsuir.bank.credit.dto.ScheduleView;
import by.bsuir.bank.credit.service.CardService;
import by.bsuir.bank.credit.service.CreditService;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/credits")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService service;
    private final CardService cards;

    @GetMapping
    public List<CreditView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public CreditDetails details(@PathVariable Long id) {
        return service.details(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreditIssued open(@Valid @RequestBody CreditRequest request) {
        return service.open(request);
    }

    /** График платежей до заключения договора. */
    @PostMapping("/schedule")
    public ScheduleView schedule(@Valid @RequestBody ScheduleRequest request) {
        return service.preview(request);
    }

    /** Выдача наличных с кредитного счёта через кассу банка. */
    @PostMapping("/{id}/cash")
    public AccountInfo cash(@PathVariable Long id, @Valid @RequestBody CashRequest request) {
        return service.cashOut(id, request.amount());
    }

    /** Перевыпуск PIN-кода карты (и разблокировка после трёх неверных попыток). */
    @PostMapping("/{id}/pin")
    public PinEnvelope reissuePin(@PathVariable Long id) {
        return cards.reissuePin(id);
    }
}
