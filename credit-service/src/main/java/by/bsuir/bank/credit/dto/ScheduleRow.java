package by.bsuir.bank.credit.dto;

import by.bsuir.bank.credit.domain.PaymentItem;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Строка графика платежей. */
public record ScheduleRow(int seq, LocalDate dueDate, BigDecimal principal, BigDecimal interest,
                          BigDecimal total, BigDecimal balanceAfter, LocalDate paidOn) {

    public ScheduleRow(int seq, LocalDate dueDate, BigDecimal principal, BigDecimal interest, BigDecimal balanceAfter) {
        this(seq, dueDate, principal, interest, principal.add(interest), balanceAfter, null);
    }

    public static ScheduleRow from(PaymentItem item) {
        return new ScheduleRow(item.getSeq(), item.getDueDate(), item.getPrincipal(), item.getInterest(),
                item.getPrincipal().add(item.getInterest()), item.getBalanceAfter(), item.getPaidOn());
    }
}
