package by.bsuir.bank.atm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("atm")
public record AtmProperties(String terminalId, String bankUrl) {
}
