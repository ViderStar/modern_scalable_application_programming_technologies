package by.bsuir.bank.credit;

import by.bsuir.bank.credit.domain.CreditKind;
import by.bsuir.bank.credit.dto.ScheduleRow;
import by.bsuir.bank.credit.dto.ScheduleView;
import by.bsuir.bank.credit.service.ScheduleCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Модульные тесты расчёта графиков платежей. */
class ScheduleCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);

    @ParameterizedTest(name = "{0} под {1} % на {2} мес. — платёж {3}")
    @CsvSource({
            "10000.00, 17.65,  6, 1753.51",
            "10000.00, 17.65, 12,  915.14",
            "50000.00, 17.65, 60, 1260.17",
            " 1200.00,  0.00, 12,  100.00",
    })
    void annuityPaymentFormula(String amount, String rate, int months, String payment) {
        assertThat(ScheduleCalculator.annuityPayment(new BigDecimal(amount), new BigDecimal(rate), months))
                .isEqualByComparingTo(payment);
    }

    @Test
    void annuityScheduleRepaysWholeDebtWithEqualPayments() {
        List<ScheduleRow> rows = build(CreditKind.ANNUITY, "10000.00", 6);

        assertThat(rows).hasSize(6);
        assertThat(rows).extracting(ScheduleRow::dueDate).containsExactly(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 1), LocalDate.of(2027, 4, 1));
        assertThat(rows.get(0).interest()).isEqualByComparingTo("147.08");      // 10000 * 17,65 / 1200
        assertThat(rows.get(0).principal()).isEqualByComparingTo("1606.43");
        assertThat(rows.get(0).balanceAfter()).isEqualByComparingTo("8393.57");
        assertThat(rows.subList(0, 5)).allSatisfy(row -> assertThat(row.total()).isEqualByComparingTo("1753.51"));
        assertThat(rows.get(5).balanceAfter()).isEqualByComparingTo("0");
        assertThat(sum(rows, true)).isEqualByComparingTo("10000.00");

        // проценты убывают, доля основного долга растёт
        for (int i = 1; i < rows.size(); i++) {
            assertThat(rows.get(i).interest()).isLessThan(rows.get(i - 1).interest());
            assertThat(rows.get(i).principal()).isGreaterThan(rows.get(i - 1).principal());
        }
    }

    @Test
    void lastAnnuityPaymentAbsorbsRoundingError() {
        List<ScheduleRow> rows = build(CreditKind.ANNUITY, "9999.99", 36);

        assertThat(sum(rows, true)).isEqualByComparingTo("9999.99");
        assertThat(rows.get(35).balanceAfter()).isEqualByComparingTo("0");
        assertThat(rows.get(35).total().subtract(rows.get(0).total()).abs()).isLessThan(new BigDecimal("1.00"));
    }

    @Test
    void interestOnlySchedulePaysDebtAtTheEnd() {
        List<ScheduleRow> rows = build(CreditKind.INTEREST_ONLY, "6000.00", 4);

        assertThat(rows).hasSize(4);
        assertThat(rows).allSatisfy(row -> assertThat(row.interest()).isEqualByComparingTo("88.25"));   // 6000 * 17,65 / 1200
        assertThat(rows.subList(0, 3)).allSatisfy(row -> {
            assertThat(row.principal()).isEqualByComparingTo("0");
            assertThat(row.balanceAfter()).isEqualByComparingTo("6000.00");
        });
        assertThat(rows.get(3).principal()).isEqualByComparingTo("6000.00");
        assertThat(rows.get(3).total()).isEqualByComparingTo("6088.25");
        assertThat(rows.get(3).balanceAfter()).isEqualByComparingTo("0");
    }

    @Test
    void totalsShowOverpayment() {
        ScheduleView view = ScheduleView.of(build(CreditKind.ANNUITY, "10000.00", 6));

        assertThat(view.totalInterest()).isEqualByComparingTo("521.06");
        assertThat(view.totalPayment()).isEqualByComparingTo("10521.06");
    }

    private static List<ScheduleRow> build(CreditKind kind, String amount, int months) {
        return ScheduleCalculator.build(kind, new BigDecimal(amount), new BigDecimal("17.65"), months, START);
    }

    private static BigDecimal sum(List<ScheduleRow> rows, boolean principal) {
        return rows.stream().map(principal ? ScheduleRow::principal : ScheduleRow::interest)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
