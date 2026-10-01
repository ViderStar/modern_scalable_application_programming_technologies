package by.bsuir.bank.deposit.service;

import by.bsuir.bank.common.ledger.Entry;
import by.bsuir.bank.common.ledger.Posting;

import static by.bsuir.bank.common.i18n.Localized.code;

import java.math.BigDecimal;
import java.util.List;

/**
 * Проводки процесса «Депозитная программа» — в точности по упрощённой схеме из задания.
 * Касса — активный счёт; фонд развития, счёт вклада и процентный счёт — пассивные.
 * Название операции передаётся в главную книгу кодом: текст подставляется при чтении на языке пользователя.
 */
public final class DepositPostings {

    /** Заключение договора: деньги вносятся в кассу, зачисляются на счёт вклада и передаются в фонд развития. */
    public static List<Posting> open(String cash, String fund, String main, BigDecimal amount) {
        return List.of(
                // внесение денег в кассу
                Posting.of(code("posting.deposit.cashIn"), Entry.debit(cash, amount)),
                // перевод денег с кассы на текущий счёт
                Posting.of(code("posting.deposit.toAccount"), Entry.credit(cash, amount), Entry.credit(main, amount)),
                // использование денег банком
                Posting.of(code("posting.deposit.toFund"), Entry.debit(main, amount), Entry.credit(fund, amount)));
    }

    public static Posting accrual(String fund, String interest, BigDecimal amount) {
        // начисление процентов по депозиту
        return Posting.of(code("posting.deposit.accrual"), Entry.debit(fund, amount), Entry.credit(interest, amount));
    }

    /** Выплата процентов: с процентного счёта в кассу и из кассы клиенту. */
    public static List<Posting> payout(String cash, String interest, BigDecimal amount) {
        return List.of(
                // перевод процентов в кассу
                Posting.of(code("posting.deposit.interestToCash"), Entry.debit(interest, amount), Entry.debit(cash, amount)),
                // вывод процентов из кассы
                Posting.of(code("posting.deposit.interestOut"), Entry.credit(cash, amount)));
    }

    /** Окончание депозита: сумма возвращается из фонда на счёт вклада, затем через кассу клиенту. */
    public static List<Posting> close(String cash, String fund, String main, BigDecimal amount) {
        return List.of(
                // окончание депозита
                Posting.of(code("posting.deposit.maturity"), Entry.debit(fund, amount), Entry.credit(main, amount)),
                // перевод депозита в кассу
                Posting.of(code("posting.deposit.toCash"), Entry.debit(main, amount), Entry.debit(cash, amount)),
                // вывод денег из кассы
                Posting.of(code("posting.deposit.cashOut"), Entry.credit(cash, amount)));
    }

    private DepositPostings() {
    }
}
