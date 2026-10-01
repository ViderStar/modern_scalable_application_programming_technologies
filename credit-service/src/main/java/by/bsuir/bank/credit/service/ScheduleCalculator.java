package by.bsuir.bank.credit.service;

import by.bsuir.bank.credit.domain.CreditKind;
import by.bsuir.bank.credit.dto.ScheduleRow;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Расчёт графика платежей по кредиту. Месячная ставка i = годовая / 12 / 100. */
public final class ScheduleCalculator {

    private static final MathContext PRECISION = MathContext.DECIMAL64;
    private static final BigDecimal MONTHS_PERCENT = BigDecimal.valueOf(1200);

    public static List<ScheduleRow> build(CreditKind kind, BigDecimal amount, BigDecimal rate, int months, LocalDate start) {
        return kind == CreditKind.ANNUITY ? annuity(amount, rate, months, start) : interestOnly(amount, rate, months, start);
    }

    /** Аннуитетный платёж: A = S * i / (1 - (1 + i)^-n). */
    public static BigDecimal annuityPayment(BigDecimal amount, BigDecimal rate, int months) {
        BigDecimal i = rate.divide(MONTHS_PERCENT, PRECISION);
        if (i.signum() == 0) {
            return amount.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        }
        BigDecimal growth = BigDecimal.ONE.add(i).pow(months, PRECISION);
        return amount.multiply(i).multiply(growth)
                .divide(growth.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
    }

    /**
     * Аннуитет: платёж постоянен, проценты начисляются на остаток долга, остальное гасит долг.
     * Последний платёж закрывает остаток целиком, вбирая накопленную погрешность округления.
     */
    private static List<ScheduleRow> annuity(BigDecimal amount, BigDecimal rate, int months, LocalDate start) {
        BigDecimal payment = annuityPayment(amount, rate, months);
        BigDecimal balance = amount;
        List<ScheduleRow> rows = new ArrayList<>();
        for (int k = 1; k <= months; k++) {
            BigDecimal interest = monthlyInterest(balance, rate);
            BigDecimal principal = k == months ? balance : payment.subtract(interest).min(balance);
            balance = balance.subtract(principal);
            rows.add(new ScheduleRow(k, start.plusMonths(k), principal, interest, balance));
        }
        return rows;
    }

    /** Ежемесячно уплачиваются только проценты на всю сумму, основной долг возвращается последним платежом. */
    private static List<ScheduleRow> interestOnly(BigDecimal amount, BigDecimal rate, int months, LocalDate start) {
        BigDecimal interest = monthlyInterest(amount, rate);
        List<ScheduleRow> rows = new ArrayList<>();
        for (int k = 1; k <= months; k++) {
            boolean last = k == months;
            rows.add(new ScheduleRow(k, start.plusMonths(k), last ? amount : BigDecimal.ZERO.setScale(2), interest,
                    last ? BigDecimal.ZERO.setScale(2) : amount));
        }
        return rows;
    }

    private static BigDecimal monthlyInterest(BigDecimal balance, BigDecimal rate) {
        return balance.multiply(rate).divide(MONTHS_PERCENT, 2, RoundingMode.HALF_UP);
    }

    private ScheduleCalculator() {
    }
}
