package by.bsuir.bank.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Адреса смежных микросервисов (bank.services.* в application.yml, переопределяются переменными окружения). */
@ConfigurationProperties("bank.services")
public record ServiceUrls(String clients, String accounts, String deposits, String credits) {
}
