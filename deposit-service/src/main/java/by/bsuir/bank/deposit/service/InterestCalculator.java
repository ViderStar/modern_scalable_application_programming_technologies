package by.bsuir.bank.deposit.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Расчёт процентов по вкладу: простые проценты, фактическое число дней, 365 дней в году. */
public final class InterestCalculator {

    private static final BigDecimal YEAR_BASIS = BigDecimal.valueOf(100L * 365);

    /**
     * Проценты, накопленные с даты начала по дату расчёта: S * r * дни / (100 * 365).
     * Считается накопленная сумма целиком, а не прирост за день, — так копейки округления не теряются.
     */
    public static BigDecimal accrued(BigDecimal amount, BigDecimal rate, LocalDate start, LocalDate upTo) {
        long days = Math.max(0, ChronoUnit.DAYS.between(start, upTo));
        return amount.multiply(rate).multiply(BigDecimal.valueOf(days)).divide(YEAR_BASIS, 2, RoundingMode.HALF_UP);
    }

    /** Число полных месяцев договора, истёкших к дате расчёта. */
    public static int fullMonths(LocalDate start, LocalDate upTo) {
        int months = 0;
        while (!start.plusMonths(months + 1L).isAfter(upTo)) {
            months++;
        }
        return months;
    }

    private InterestCalculator() {
    }
}
