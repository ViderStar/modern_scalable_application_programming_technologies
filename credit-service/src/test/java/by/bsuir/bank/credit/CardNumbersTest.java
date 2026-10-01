package by.bsuir.bank.credit;

import by.bsuir.bank.credit.domain.Card;
import by.bsuir.bank.credit.domain.CardNumbers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Модульные тесты номера карты (алгоритм Луна) и счётчика неверных PIN-кодов. */
class CardNumbersTest {

    @ParameterizedTest
    @ValueSource(strings = {"4539148803436467", "4111111111111111", "5500000000000004"})
    void acceptsKnownValidNumbers(String number) {
        assertThat(CardNumbers.isValid(number)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"4539148803436468", "411111111111111", "41111111111111111", "4111-1111-1111-1111", ""})
    void rejectsWrongNumbers(String number) {
        assertThat(CardNumbers.isValid(number)).isFalse();
    }

    @Test
    void generatedNumbersAreValidAndUnique() {
        String first = CardNumbers.generate(1);
        String second = CardNumbers.generate(2);

        assertThat(first).hasSize(16).startsWith("911238").isNotEqualTo(second);
        assertThat(CardNumbers.isValid(first)).isTrue();
        assertThat(CardNumbers.isValid(second)).isTrue();
        assertThat(CardNumbers.isValid(null)).isFalse();
    }

    @Test
    void cardIsBlockedAfterThirdWrongPin() {
        Card card = new Card();

        assertThat(card.registerFailedAttempt()).isEqualTo(2);
        assertThat(card.registerFailedAttempt()).isEqualTo(1);
        assertThat(card.isBlocked()).isFalse();
        assertThat(card.registerFailedAttempt()).isZero();
        assertThat(card.isBlocked()).isTrue();

        card.resetAttempts();
        assertThat(card.isBlocked()).isFalse();
        assertThat(card.getFailedAttempts()).isZero();
    }
}
