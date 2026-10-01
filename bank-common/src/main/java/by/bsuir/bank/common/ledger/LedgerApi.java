package by.bsuir.bank.common.ledger;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/** REST-клиент сервиса «Счета»: банковский день, счета и проводки. */
@HttpExchange("/api")
public interface LedgerApi {

    String CASH = "1010";
    String FUND = "7327";

    @GetExchange("/bank-day")
    BankDayInfo bankDay();

    @PostExchange("/bank-day/close")
    DayCloseInfo closeDay(@RequestParam int days);

    @GetExchange("/currencies")
    List<CurrencyInfo> currencies();

    /** Собственный счёт банка: касса (1010) или фонд развития (7327) в заданной валюте. */
    @GetExchange("/accounts/system")
    AccountInfo systemAccount(@RequestParam String chartCode, @RequestParam String currency);

    @GetExchange("/accounts")
    List<AccountInfo> accounts(@RequestParam String contractRef);

    @GetExchange("/accounts/{number}")
    AccountInfo account(@PathVariable String number);

    @PostExchange("/accounts")
    AccountInfo open(@RequestBody OpenAccount request);

    @PostExchange("/operations")
    List<OperationInfo> post(@RequestBody Batch batch);

    @GetExchange("/operations")
    List<OperationInfo> operations(@RequestParam String contractRef);
}
