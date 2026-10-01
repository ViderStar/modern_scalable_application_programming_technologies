package by.bsuir.bank.credit.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

import static by.bsuir.bank.common.api.CommonExceptionHandler.REQUIRED;

/** Данные формы заключения кредитного договора. */
public record CreditRequest(

        @NotBlank(message = REQUIRED)
        @Pattern(regexp = "^К-\\d{6}$", message = "{credit.numberFormat}")
        String number,

        @NotNull(message = REQUIRED)
        Long productId,

        @NotBlank(message = REQUIRED)
        String currency,

        @NotNull(message = REQUIRED)
        Long clientId,

        @NotNull(message = REQUIRED)
        @Positive(message = "{validation.amountPositive}")
        @Digits(integer = 12, fraction = 2, message = "{validation.amountFormat}")
        BigDecimal amount,

        @NotNull(message = REQUIRED)
        BigDecimal rate,

        @NotNull(message = REQUIRED)
        @Positive(message = "{validation.termPositive}")
        Integer termMonths,

        @NotNull(message = REQUIRED)
        LocalDate startDate,

        @NotNull(message = REQUIRED)
        LocalDate endDate
) {
}
