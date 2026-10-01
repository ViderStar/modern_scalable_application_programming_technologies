package by.bsuir.bank.atm.bank;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/** Протокол общения банкомата и банка: банкомат знает о банке только эти два запроса. */
@HttpExchange("/api/atm")
public interface BankGateway {

    @PostExchange("/transactions")
    BankReply send(@RequestBody Transaction transaction);

    @GetExchange("/operators")
    List<Operator> operators();
}
