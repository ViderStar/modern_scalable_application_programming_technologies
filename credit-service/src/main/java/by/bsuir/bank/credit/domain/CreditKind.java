package by.bsuir.bank.credit.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Вид кредита определяет способ погашения. */
@Getter
@RequiredArgsConstructor
public enum CreditKind {

    /** Ежемесячное погашение долга равными аннуитетными платежами. */
    ANNUITY("Аннуитетные платежи"),

    /** Ежемесячное погашение процентов, вся сумма кредита возвращается в конце срока. */
    INTEREST_ONLY("Проценты ежемесячно, долг в конце срока");

    private final String title;
}
