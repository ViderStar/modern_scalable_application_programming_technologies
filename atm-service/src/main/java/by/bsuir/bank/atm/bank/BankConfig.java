package by.bsuir.bank.atm.bank;

import by.bsuir.bank.atm.AtmProperties;
import by.bsuir.bank.common.HttpApis;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
public class BankConfig {

    @Bean
    public BankGateway bankGateway(RestClient.Builder builder, AtmProperties properties) {
        return HttpApis.proxy(builder, properties.bankUrl(), BankGateway.class);
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
