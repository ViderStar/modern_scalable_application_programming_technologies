package by.bsuir.bank.credit.atm;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Запрос банкомата — содержимое его «внутреннего списка»: упорядоченные пары «имя — значение».
 * Имена элементов: CARD, PIN, OPERATION, AMOUNT, OPERATOR, PHONE.
 */
public record AtmRequest(@NotEmpty List<Item> items) {

    public record Item(String name, String value) {
    }
}
