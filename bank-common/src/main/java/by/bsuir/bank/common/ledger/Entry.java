package by.bsuir.bank.common.ledger;

import java.math.BigDecimal;

/** Строка проводки: счёт, сторона и сумма. */
public record Entry(String account, Side side, BigDecimal amount) {

    public static Entry debit(String account, BigDecimal amount) {
        return new Entry(account, Side.DEBIT, amount);
    }

    public static Entry credit(String account, BigDecimal amount) {
        return new Entry(account, Side.CREDIT, amount);
    }
}
