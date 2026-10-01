package by.bsuir.bank.credit.service;

import by.bsuir.bank.common.ledger.Entry;
import by.bsuir.bank.common.ledger.Posting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Проводки процесса «Кредитная программа» — в точности по упрощённой схеме из задания.
 * Касса, текущий (кредитный) и процентный счета клиента — активные; фонд развития — пассивный.
 */
public final class CreditPostings {

    /** Выдача кредита: банк выделяет сумму из фонда развития на текущий счёт клиента. */
    public static Posting issue(String fund, String main, BigDecimal amount) {
        return Posting.of("Выделение кредита банком", Entry.debit(fund, amount), Entry.debit(main, amount));
    }

    /** Снятие наличных: с текущего счёта в кассу и из кассы клиенту (в отделении или в банкомате). */
    public static List<Posting> cashOut(String cash, String main, BigDecimal amount) {
        return List.of(
                Posting.of("Перевод кредита в кассу", Entry.credit(main, amount), Entry.debit(cash, amount)),
                Posting.of("Получение кредита через кассу", Entry.credit(cash, amount)));
    }

    /** Очередной платёж по графику: уплата процентов и погашение части основного долга. */
    public static List<Posting> payment(String cash, String fund, String main, String interestAccount,
                                        BigDecimal interest, BigDecimal principal, boolean last) {
        List<Posting> postings = new ArrayList<>();
        if (interest.signum() > 0) {
            postings.add(Posting.of("Внесение процентов в кассу", Entry.debit(cash, interest)));
            postings.add(Posting.of("Перевод процентов из кассы", Entry.credit(cash, interest), Entry.debit(interestAccount, interest)));
            postings.add(Posting.of("Начисление процентов банком", Entry.credit(interestAccount, interest), Entry.credit(fund, interest)));
        }
        if (principal.signum() > 0) {
            postings.add(Posting.of("Внесение денег в кассу", Entry.debit(cash, principal)));
            postings.add(Posting.of("Погашение долга за кредит из кассы", Entry.credit(cash, principal), Entry.debit(main, principal)));
            postings.add(Posting.of(last ? "Окончание кредита" : "Возврат части кредита в фонд банка",
                    Entry.credit(main, principal), Entry.credit(fund, principal)));
        }
        return postings;
    }

    /** Оплата услуг связи с кредитного счёта на расчётный счёт оператора. */
    public static Posting mobilePayment(String main, String operatorAccount, String description, BigDecimal amount) {
        return Posting.of(description, Entry.credit(main, amount), Entry.credit(operatorAccount, amount));
    }

    private CreditPostings() {
    }
}
