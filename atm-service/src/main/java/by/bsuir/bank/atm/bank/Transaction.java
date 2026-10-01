package by.bsuir.bank.atm.bank;

import java.util.List;

/** Транзакция — всё содержимое внутреннего списка банкомата, отправляемое банку одним сообщением. */
public record Transaction(List<Item> items) {
}
