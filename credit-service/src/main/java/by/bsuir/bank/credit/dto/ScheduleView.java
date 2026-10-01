package by.bsuir.bank.credit.dto;

import java.math.BigDecimal;
import java.util.List;

/** График платежей с итогами: сумма процентов (переплата) и полная сумма выплат. */
public record ScheduleView(List<ScheduleRow> rows, BigDecimal totalInterest, BigDecimal totalPayment) {

    public static ScheduleView of(List<ScheduleRow> rows) {
        BigDecimal interest = rows.stream().map(ScheduleRow::interest).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = rows.stream().map(ScheduleRow::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ScheduleView(rows, interest, total);
    }
}
