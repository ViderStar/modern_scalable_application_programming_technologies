package by.bsuir.bank.atm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Из общей библиотеки подключаются обработчик ошибок и локализация. */
@SpringBootApplication(scanBasePackages = {"by.bsuir.bank.atm", "by.bsuir.bank.common.api", "by.bsuir.bank.common.i18n"})
@ConfigurationPropertiesScan
public class AtmServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AtmServiceApplication.class, args);
    }
}
