package by.bsuir.bank.account;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Настройки главной книги: стартовый капитал фонда развития и участники закрытия дня. */
@ConfigurationProperties("bank")
public record BankProperties(Fund fund, DayClose dayClose) {

    public record Fund(Map<String, BigDecimal> capital) {
    }

    public record DayClose(List<Participant> participants) {
    }

    public record Participant(String name, String url) {
    }
}
