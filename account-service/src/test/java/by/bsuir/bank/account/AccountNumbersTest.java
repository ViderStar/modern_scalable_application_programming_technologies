package by.bsuir.bank.account;

import by.bsuir.bank.account.domain.AccountNumbers;
import by.bsuir.bank.account.domain.Activity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Модульные тесты правил формирования номера счёта и расчёта сальдо. */
class AccountNumbersTest {

    @ParameterizedTest
    @CsvSource({
            "1010, 0, 1, 1010000000019",
            "7327, 0, 2, 7327000000029",
            "3014, 4, 1, 301400004001",
            "2400, 12345, 17, 240012345017",
    })
    void numberConsistsOfBalanceAccountClientCodeSequenceAndKey(String chart, int client, int seq, String prefix) {
        String number = AccountNumbers.generate(chart, client, seq);

        assertThat(number).hasSize(13).containsOnlyDigits().startsWith(prefix.substring(0, 12));
        assertThat(number.substring(0, 4)).isEqualTo(chart);
        assertThat(Integer.parseInt(number.substring(4, 9))).isEqualTo(client);
        assertThat(Integer.parseInt(number.substring(9, 12))).isEqualTo(seq);
        assertThat(AccountNumbers.isValid(number)).isTrue();
    }

    @Test
    void checkDigitDetectsSingleDigitError() {
        String number = AccountNumbers.generate("3014", 4, 1);
        for (int position = 0; position < 12; position++) {
            char wrong = number.charAt(position) == '9' ? '0' : (char) (number.charAt(position) + 1);
            String corrupted = number.substring(0, position) + wrong + number.substring(position + 1);
            assertThat(AccountNumbers.isValid(corrupted)).as("ошибка в разряде %d", position + 1).isFalse();
        }
    }

    @Test
    void rejectsMalformedNumbers() {
        assertThat(AccountNumbers.isValid(null)).isFalse();
        assertThat(AccountNumbers.isValid("30140000400")).isFalse();
        assertThat(AccountNumbers.isValid("30140000400A1")).isFalse();
        assertThatThrownBy(() -> AccountNumbers.generate("3014", 100_000, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccountNumbers.generate("3014", 1, 1000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccountNumbers.generate("30", 1, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void balanceDependsOnActivity() {
        BigDecimal debit = new BigDecimal("700.00");
        BigDecimal credit = new BigDecimal("1000.00");

        assertThat(Activity.ACTIVE.balance(debit, credit)).isEqualByComparingTo("-300.00");
        assertThat(Activity.PASSIVE.balance(debit, credit)).isEqualByComparingTo("300.00");
        assertThat(Activity.ACTIVE_PASSIVE.balance(debit, credit)).isEqualByComparingTo("-300.00");
    }
}
