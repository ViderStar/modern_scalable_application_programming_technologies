package by.bsuir.bank.atm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Из общей библиотеки подключается только обработчик ошибок. */
@SpringBootApplication(scanBasePackages = {"by.bsuir.bank.atm", "by.bsuir.bank.common.api"})
@ConfigurationPropertiesScan
public class AtmServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AtmServiceApplication.class, args);
    }
}
