package by.bsuir.bank.common.ledger;

import java.util.List;

/** Одна операция — строка схемы бухгалтерских проводок. */
public record Posting(String description, List<Entry> entries) {

    public static Posting of(String description, Entry... entries) {
        return new Posting(description, List.of(entries));
    }
}
