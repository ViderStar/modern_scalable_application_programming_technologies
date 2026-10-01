package by.bsuir.bank.deposit.service;

import by.bsuir.bank.common.ledger.Entry;
import by.bsuir.bank.common.ledger.Posting;

import java.math.BigDecimal;
import java.util.List;

/**
 * Проводки процесса «Депозитная программа» — в точности по упрощённой схеме из задания.
 * Касса — активный счёт; фонд развития, счёт вклада и процентный счёт — пассивные.
 */
public final class DepositPostings {

    /** Заключение договора: деньги вносятся в кассу, зачисляются на счёт вклада и передаются в фонд развития. */
    public static List<Posting> open(String cash, String fund, String main, BigDecimal amount) {
        return List.of(
                Posting.of("Внесение денег в кассу", Entry.debit(cash, amount)),
                Posting.of("Перевод денег с кассы на текущий счёт", Entry.credit(cash, amount), Entry.credit(main, amount)),
                Posting.of("Использование денег банком", Entry.debit(main, amount), Entry.credit(fund, amount)));
    }

    public static Posting accrual(String fund, String interest, BigDecimal amount) {
        return Posting.of("Начисление процентов по депозиту", Entry.debit(fund, amount), Entry.credit(interest, amount));
    }

    /** Выплата процентов: с процентного счёта в кассу и из кассы клиенту. */
    public static List<Posting> payout(String cash, String interest, BigDecimal amount) {
        return List.of(
                Posting.of("Перевод процентов в кассу", Entry.debit(interest, amount), Entry.debit(cash, amount)),
                Posting.of("Вывод процентов из кассы", Entry.credit(cash, amount)));
    }

    /** Окончание депозита: сумма возвращается из фонда на счёт вклада, затем через кассу клиенту. */
    public static List<Posting> close(String cash, String fund, String main, BigDecimal amount) {
        return List.of(
                Posting.of("Окончание депозита", Entry.debit(fund, amount), Entry.credit(main, amount)),
                Posting.of("Перевод депозита в кассу", Entry.debit(main, amount), Entry.debit(cash, amount)),
                Posting.of("Вывод денег из кассы", Entry.credit(cash, amount)));
    }

    private DepositPostings() {
    }
}
