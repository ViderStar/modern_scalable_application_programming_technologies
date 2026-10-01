package by.bsuir.bank.deposit.web;

import by.bsuir.bank.common.client.ClientApi;
import by.bsuir.bank.common.client.ClientInfo;
import by.bsuir.bank.common.ledger.BankDayInfo;
import by.bsuir.bank.common.ledger.DayCloseInfo;
import by.bsuir.bank.common.ledger.LedgerApi;
import by.bsuir.bank.deposit.domain.DepositProduct;
import by.bsuir.bank.deposit.dto.Meta;
import by.bsuir.bank.deposit.repository.DepositProductRepository;
import by.bsuir.bank.deposit.service.DepositService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Справочные данные формы договора. Клиенты и банковский день запрашиваются у смежных сервисов. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReferenceController {

    private final DepositProductRepository products;
    private final DepositService deposits;
    private final ClientApi clients;
    private final LedgerApi ledger;

    @GetMapping("/products")
    public List<DepositProduct> products() {
        return products.findAll(Sort.by("id"));
    }

    @GetMapping("/clients")
    public List<ClientInfo> clients() {
        return clients.list();
    }

    @GetMapping("/meta")
    public Meta meta() {
        return deposits.meta();
    }

    @GetMapping("/bank-day")
    public BankDayInfo bankDay() {
        return ledger.bankDay();
    }

    @PostMapping("/bank-day/close")
    public DayCloseInfo closeDay(@RequestParam(defaultValue = "1") int days) {
        return ledger.closeDay(days);
    }
}
