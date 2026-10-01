package by.bsuir.bank.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// из общей библиотеки подключается только обработчик ошибок
@SpringBootApplication(scanBasePackages = {"by.bsuir.bank.account", "by.bsuir.bank.common.api"})
@ConfigurationPropertiesScan
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
