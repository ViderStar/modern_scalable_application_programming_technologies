package by.bsuir.bank.deposit.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Вид вклада определяет порядок выплаты процентов и балансовые счета из плана счетов. */
@Getter
@RequiredArgsConstructor
public enum DepositKind {

    /** Отзывный, до востребования: проценты выплачиваются ежемесячно, вклад можно забрать досрочно. */
    REVOCABLE("Отзывный, до востребования", "3404", "3470"),

    /** Срочный безотзывный: проценты выплачиваются в конце срока, досрочный отзыв невозможен. */
    IRREVOCABLE("Срочный безотзывный", "3414", "3471");

    private final String title;
    private final String mainChartCode;
    private final String interestChartCode;
}
