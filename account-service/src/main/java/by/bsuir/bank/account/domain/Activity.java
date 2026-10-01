package by.bsuir.bank.account.domain;

import java.math.BigDecimal;

/** Активность балансового счёта определяет, какая сторона увеличивает остаток. */
public enum Activity {

    ACTIVE("Активный"),
    PASSIVE("Пассивный"),
    ACTIVE_PASSIVE("Активно-пассивный");

    private final String title;

    Activity(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    /** Сальдо: у активного счёта дебет минус кредит, у пассивного — кредит минус дебет. */
    public BigDecimal balance(BigDecimal debit, BigDecimal credit) {
        return this == PASSIVE ? credit.subtract(debit) : debit.subtract(credit);
    }
}
