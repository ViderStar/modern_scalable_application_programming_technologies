package by.bsuir.bank.deposit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Вместе с пакетом сервиса сканируется общая библиотека: REST-клиенты смежных сервисов и обработчик ошибок. */
@SpringBootApplication(scanBasePackages = {"by.bsuir.bank.deposit", "by.bsuir.bank.common"})
public class DepositServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DepositServiceApplication.class, args);
    }
}
