package by.bsuir.bank.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// вместе с пакетом сервиса сканируется локализация из общей библиотеки
@SpringBootApplication(scanBasePackages = {"by.bsuir.bank.client", "by.bsuir.bank.common.i18n"})
public class ClientServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientServiceApplication.class, args);
    }
}
