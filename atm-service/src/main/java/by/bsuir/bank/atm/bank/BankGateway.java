package by.bsuir.bank.atm.bank;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/** Протокол общения банкомата и банка: транзакции и справочник операторов; демо-карты — только для эмулятора. */
@HttpExchange("/api/atm")
public interface BankGateway {

    @PostExchange("/transactions")
    BankReply send(@RequestBody Transaction transaction);

    @GetExchange("/operators")
    List<Operator> operators();

    /** Демонстрационные карты банка с PIN-кодами — подсказка на странице эмулятора. */
    @GetExchange("/demo-cards")
    List<DemoCard> demoCards();
}
