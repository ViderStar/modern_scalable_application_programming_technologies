package by.bsuir.bank.deposit.web;

import by.bsuir.bank.deposit.dto.DepositDetails;
import by.bsuir.bank.deposit.dto.DepositRequest;
import by.bsuir.bank.deposit.dto.DepositView;
import by.bsuir.bank.deposit.service.DepositService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/deposits")
@RequiredArgsConstructor
public class DepositController {

    private final DepositService service;

    @GetMapping
    public List<DepositView> list(@RequestParam(required = false) Long clientId) {
        return service.list(clientId);
    }

    @GetMapping("/{id}")
    public DepositDetails details(@PathVariable Long id) {
        return service.details(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DepositView open(@Valid @RequestBody DepositRequest request) {
        return service.open(request);
    }

    /** Досрочный отзыв вклада. */
    @PostMapping("/{id}/withdraw")
    public DepositView withdraw(@PathVariable Long id) {
        return service.withdraw(id);
    }
}
