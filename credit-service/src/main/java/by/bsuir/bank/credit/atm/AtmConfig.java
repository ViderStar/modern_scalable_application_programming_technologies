package by.bsuir.bank.credit.atm;

import by.bsuir.bank.common.HttpApis;
import by.bsuir.bank.common.ServiceUrls;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AtmConfig {

    @Bean
    public DepositApi depositApi(RestClient.Builder builder, ServiceUrls urls) {
        return HttpApis.proxy(builder, urls.deposits(), DepositApi.class);
    }
}
