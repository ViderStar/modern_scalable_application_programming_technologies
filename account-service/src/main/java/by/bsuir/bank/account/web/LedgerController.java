package by.bsuir.bank.account.web;

import by.bsuir.bank.account.domain.ChartAccount;
import by.bsuir.bank.account.domain.Currency;
import by.bsuir.bank.account.dto.AccountReport;
import by.bsuir.bank.account.dto.AccountView;
import by.bsuir.bank.account.dto.BatchRequest;
import by.bsuir.bank.account.dto.OpenAccountRequest;
import by.bsuir.bank.account.dto.OperationView;
import by.bsuir.bank.account.repository.ChartAccountRepository;
import by.bsuir.bank.account.repository.CurrencyRepository;
import by.bsuir.bank.account.service.LedgerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
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
@RequestMapping("/api")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledger;
    private final ChartAccountRepository chart;
    private final CurrencyRepository currencies;

    @GetMapping("/currencies")
    public List<Currency> currencies() {
        return currencies.findAll(Sort.by("code"));
    }

    @GetMapping("/chart-of-accounts")
    public List<ChartAccount> chartOfAccounts() {
        return chart.findAll(Sort.by("code"));
    }

    @GetMapping("/accounts")
    public List<AccountView> accounts(@RequestParam(required = false) Integer clientId,
                                      @RequestParam(required = false) String contractRef) {
        return ledger.find(clientId, contractRef);
    }

    @GetMapping("/accounts/system")
    public AccountView systemAccount(@RequestParam String chartCode, @RequestParam String currency) {
        return ledger.system(chartCode, currency);
    }

    @GetMapping("/accounts/{number}")
    public AccountView account(@PathVariable String number) {
        return ledger.get(number);
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView open(@Valid @RequestBody OpenAccountRequest request) {
        return ledger.open(request);
    }

    @PostMapping("/operations")
    @ResponseStatus(HttpStatus.CREATED)
    public List<OperationView> post(@Valid @RequestBody BatchRequest request) {
        return ledger.post(request);
    }

    @GetMapping("/operations")
    public List<OperationView> journal(@RequestParam(required = false) String contractRef,
                                       @RequestParam(required = false) String account,
                                       @RequestParam(defaultValue = "200") int limit) {
        return ledger.journal(contractRef, account, Math.min(limit, 1000));
    }

    @GetMapping("/report")
    public AccountReport report() {
        return ledger.report();
    }
}
