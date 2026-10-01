package by.bsuir.bank.credit.atm;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.math.BigDecimal;
import java.util.List;

/** REST-клиент сервиса депозитов: вклады клиента для операции банкомата «Остаток депозитного счёта». */
@HttpExchange("/api/deposits")
public interface DepositApi {

    record DepositInfo(String number, String productName, String currency, BigDecimal amount, String status) {
    }

    @GetExchange
    List<DepositInfo> byClient(@RequestParam Long clientId);
}
