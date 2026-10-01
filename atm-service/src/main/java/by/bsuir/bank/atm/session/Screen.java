package by.bsuir.bank.atm.session;

import by.bsuir.bank.atm.bank.BankReply;
import by.bsuir.bank.atm.bank.Item;

import java.util.List;

/**
 * Всё, что видит клиент банкомата: содержимое экрана, ожидаемый ввод, пункты меню у боковых кнопок,
 * выданные деньги и чек. Для учебных целей сюда же выводятся внутренний список и последний обмен с банком.
 */
public record Screen(
        String state,
        String title,
        List<String> lines,
        Input input,
        List<Option> options,
        String notice,
        boolean cardInside,
        String cash,
        List<String> receipt,
        List<Item> buffer,
        Exchange exchange
) {

    /** Ожидаемый ввод с клавиатуры: только цифры, length — их количество (exact) либо максимум. */
    public record Input(String kind, int length, boolean exact) {
    }

    public record Option(String key, String label) {
    }

    /** Последняя транзакция, отправленная банку, и его ответ (PIN-код скрыт). */
    public record Exchange(List<Item> request, BankReply reply) {
    }
}
