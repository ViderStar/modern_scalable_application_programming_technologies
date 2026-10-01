package by.bsuir.bank.credit;

import by.bsuir.bank.common.ledger.InMemoryLedger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** В тестах вместо REST-клиента сервиса «Счета» подставляется главная книга в памяти. */
@TestConfiguration
public class TestLedgerConfig {

    @Bean
    @Primary
    public InMemoryLedger inMemoryLedger() {
        return new InMemoryLedger();
    }
}
