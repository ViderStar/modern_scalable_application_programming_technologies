package by.bsuir.bank.client;

import by.bsuir.bank.client.validation.DateTextValidator;
import by.bsuir.bank.client.validation.Dates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Модульные тесты проверки дат: п. 6 программы защиты. */
class DateTextValidatorTest {

    private final DateTextValidator validator = new DateTextValidator();

    @ParameterizedTest
    @ValueSource(strings = {"14.03.2001", "29.02.2016", "01.01.1900", "31.12.1999"})
    void acceptsExistingDates(String text) {
        assertThat(validator.isValid(text, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "не дата", "31.02.2015", "29.02.2015", "32.01.2000", "00.10.2000", "15.13.2000",
            "2015-02-28", "1.2.2015", "28.02.15", "31.12.1899"})
    void rejectsTextThatIsNotDate(String text) {
        assertThat(validator.isValid(text, null)).isFalse();
    }

    @Test
    void rejectsFutureDate() {
        String tomorrow = Dates.format(LocalDate.now().plusDays(1));
        assertThat(validator.isValid(tomorrow, null)).isFalse();
        assertThat(validator.isValid(Dates.format(LocalDate.now()), null)).isTrue();
    }

    @Test
    void emptyValueIsLeftToNotBlank() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid(" ", null)).isTrue();
    }
}
