package by.bsuir.bank.deposit;

import by.bsuir.bank.deposit.service.InterestCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Модульные тесты расчёта процентов по вкладу. */
class InterestCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);

    @ParameterizedTest(name = "{0} под {1} % за {2} дн. = {3}")
    @CsvSource({
            "1000.00,  7.00,  31,   5.95",
            "1000.00,  7.00,   1,   0.19",
            "5000.00, 12.90, 365, 645.00",
            "5000.00, 12.90, 396, 699.78",
            " 100.00,  1.50,   0,   0.00",
    })
    void simpleInterestOnActualDays(String amount, String rate, int days, String expected) {
        BigDecimal accrued = InterestCalculator.accrued(new BigDecimal(amount), new BigDecimal(rate), START, START.plusDays(days));

        assertThat(accrued).isEqualByComparingTo(expected);
    }

    @Test
    void nothingIsAccruedBeforeStart() {
        assertThat(InterestCalculator.accrued(new BigDecimal("1000"), new BigDecimal("7"), START, START.minusDays(5)))
                .isEqualByComparingTo("0");
    }

    @Test
    void dailyIncrementsAddUpToTotalWithoutRoundingLoss() {
        BigDecimal amount = new BigDecimal("1234.56");
        BigDecimal rate = new BigDecimal("7.00");
        BigDecimal posted = BigDecimal.ZERO;
        for (int day = 1; day <= 92; day++) {
            BigDecimal target = InterestCalculator.accrued(amount, rate, START, START.plusDays(day));
            assertThat(target).isGreaterThanOrEqualTo(posted);
            posted = target;
        }
        assertThat(posted).isEqualByComparingTo(InterestCalculator.accrued(amount, rate, START, START.plusDays(92)));
        assertThat(posted).isEqualByComparingTo("21.78");
    }

    @ParameterizedTest(name = "с {0} по {1} — полных месяцев: {2}")
    @CsvSource({
            "2026-10-01, 2026-10-31, 0",
            "2026-10-01, 2026-11-01, 1",
            "2026-10-01, 2027-01-01, 3",
            "2026-01-31, 2026-02-27, 0",
            "2026-01-31, 2026-02-28, 1",
            "2026-10-01, 2026-10-01, 0",
    })
    void fullMonthsFollowCalendar(LocalDate start, LocalDate upTo, int months) {
        assertThat(InterestCalculator.fullMonths(start, upTo)).isEqualTo(months);
    }
}
