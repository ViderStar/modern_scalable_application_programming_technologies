package by.bsuir.bank.account.domain;

import by.bsuir.bank.common.i18n.Messages;

import java.math.BigDecimal;

/** Активность балансового счёта определяет, какая сторона увеличивает остаток. */
public enum Activity {

    ACTIVE,
    PASSIVE,
    ACTIVE_PASSIVE;

    /** Название на языке запроса: «Активный», «Пассивный», «Активно-пассивный». */
    public String getTitle() {
        return Messages.get("activity." + name());
    }

    /** Сальдо: у активного счёта дебет минус кредит, у пассивного — кредит минус дебет. */
    public BigDecimal balance(BigDecimal debit, BigDecimal credit) {
        return this == PASSIVE ? credit.subtract(debit) : debit.subtract(credit);
    }
}
