package by.bsuir.bank.credit.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

/** Настройка демонстрационных карт (bank.demo в application.yml). */
@ConfigurationProperties("bank.demo")
public record DemoProperties(boolean enabled, List<DemoCard> cards) {

    /** Демо-договор: клиент, кредитная программа, сумма, срок и заранее известный PIN-код карты. */
    public record DemoCard(String contract, Long clientId, Long productId, BigDecimal amount, int termMonths, String pin) {
    }
}
