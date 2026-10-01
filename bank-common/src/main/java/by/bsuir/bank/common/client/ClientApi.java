package by.bsuir.bank.common.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

/** REST-клиент модуля «Клиенты» (ЛР1). */
@HttpExchange("/api/clients")
public interface ClientApi {

    @GetExchange
    List<ClientInfo> list();

    @GetExchange("/{id}")
    ClientInfo get(@PathVariable Long id);
}
