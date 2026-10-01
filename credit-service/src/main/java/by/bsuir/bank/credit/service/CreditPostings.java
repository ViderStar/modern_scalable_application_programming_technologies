package by.bsuir.bank.credit.service;

import by.bsuir.bank.common.ledger.Entry;
import by.bsuir.bank.common.ledger.Posting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static by.bsuir.bank.common.i18n.Localized.code;

/**
 * Проводки процесса «Кредитная программа» — в точности по упрощённой схеме из задания.
 * Касса, текущий (кредитный) и процентный счета клиента — активные; фонд развития — пассивный.
 * Название операции передаётся в главную книгу кодом: текст подставляется при чтении на языке пользователя.
 */
public final class CreditPostings {

    /** Выдача кредита: банк выделяет сумму из фонда развития на текущий счёт клиента. */
    public static Posting issue(String fund, String main, BigDecimal amount) {
        // выделение кредита банком
        return Posting.of(code("posting.credit.issue"), Entry.debit(fund, amount), Entry.debit(main, amount));
    }

    /** Снятие наличных: с текущего счёта в кассу и из кассы клиенту (в отделении или в банкомате). */
    public static List<Posting> cashOut(String cash, String main, BigDecimal amount) {
        return List.of(
                // перевод кредита в кассу
                Posting.of(code("posting.credit.toCash"), Entry.credit(main, amount), Entry.debit(cash, amount)),
                // получение кредита через кассу
                Posting.of(code("posting.credit.cashOut"), Entry.credit(cash, amount)));
    }

    /** Очередной платёж по графику: уплата процентов и погашение части основного долга. */
    public static List<Posting> payment(String cash, String fund, String main, String interestAccount,
                                        BigDecimal interest, BigDecimal principal, boolean last) {
        List<Posting> postings = new ArrayList<>();
        if (interest.signum() > 0) {
            // внесение процентов в кассу
            postings.add(Posting.of(code("posting.credit.interestIn"), Entry.debit(cash, interest)));
            // перевод процентов из кассы
            postings.add(Posting.of(code("posting.credit.interestFromCash"),
                    Entry.credit(cash, interest), Entry.debit(interestAccount, interest)));
            // начисление процентов банком
            postings.add(Posting.of(code("posting.credit.interestCharge"),
                    Entry.credit(interestAccount, interest), Entry.credit(fund, interest)));
        }
        if (principal.signum() > 0) {
            // внесение денег в кассу
            postings.add(Posting.of(code("posting.credit.cashIn"), Entry.debit(cash, principal)));
            // погашение долга за кредит из кассы
            postings.add(Posting.of(code("posting.credit.repay"), Entry.credit(cash, principal), Entry.debit(main, principal)));
            // окончание кредита — для последнего платежа, иначе возврат части кредита в фонд банка
            postings.add(Posting.of(code(last ? "posting.credit.maturity" : "posting.credit.partReturn"),
                    Entry.credit(main, principal), Entry.credit(fund, principal)));
        }
        return postings;
    }

    /** Оплата услуг связи с кредитного счёта на расчётный счёт оператора. */
    public static Posting mobilePayment(String main, String operatorAccount, String operator, String phone,
                                        BigDecimal amount) {
        return Posting.of(code("posting.atm.payment", operator, phone),
                Entry.credit(main, amount), Entry.credit(operatorAccount, amount));
    }

    private CreditPostings() {
    }
}
