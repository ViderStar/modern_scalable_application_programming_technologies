package by.bsuir.bank.credit.domain;

import by.bsuir.bank.common.i18n.Messages;

/** Вид кредита определяет способ погашения. */
public enum CreditKind {

    /** Ежемесячное погашение долга равными аннуитетными платежами. */
    ANNUITY,

    /** Ежемесячное погашение процентов, вся сумма кредита возвращается в конце срока. */
    INTEREST_ONLY;

    /** Название вида кредита на языке запроса. */
    public String getTitle() {
        return Messages.get("credit.kind." + name());
    }
}
