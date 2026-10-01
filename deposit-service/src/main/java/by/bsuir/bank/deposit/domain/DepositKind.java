package by.bsuir.bank.deposit.domain;

import by.bsuir.bank.common.i18n.Messages;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Вид вклада определяет порядок выплаты процентов и балансовые счета из плана счетов. */
@Getter
@RequiredArgsConstructor
public enum DepositKind {

    /** Отзывный, до востребования: проценты выплачиваются ежемесячно, вклад можно забрать досрочно. */
    REVOCABLE("3404", "3470"),

    /** Срочный безотзывный: проценты выплачиваются в конце срока, досрочный отзыв невозможен. */
    IRREVOCABLE("3414", "3471");

    private final String mainChartCode;
    private final String interestChartCode;

    /** Название вида вклада на языке запроса. */
    public String getTitle() {
        return Messages.get("deposit.kind." + name());
    }
}
