package by.bsuir.bank.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// из общей библиотеки подключаются обработчик ошибок и локализация
@SpringBootApplication(scanBasePackages = {"by.bsuir.bank.account", "by.bsuir.bank.common.api",
        "by.bsuir.bank.common.i18n"})
@ConfigurationPropertiesScan
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
