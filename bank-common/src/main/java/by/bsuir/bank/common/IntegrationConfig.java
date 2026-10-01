package by.bsuir.bank.common;

import by.bsuir.bank.common.client.ClientApi;
import by.bsuir.bank.common.ledger.LedgerApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** REST-клиенты сервисов «Клиенты» и «Счета» для сервисов депозитов и кредитов. */
@Configuration
@EnableConfigurationProperties(ServiceUrls.class)
public class IntegrationConfig {

    @Bean
    public ClientApi clientApi(RestClient.Builder builder, ServiceUrls urls) {
        return HttpApis.proxy(builder, urls.clients(), ClientApi.class);
    }

    @Bean
    public LedgerApi ledgerApi(RestClient.Builder builder, ServiceUrls urls) {
        return HttpApis.proxy(builder, urls.accounts(), LedgerApi.class);
    }
}
