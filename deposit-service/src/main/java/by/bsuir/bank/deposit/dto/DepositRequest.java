package by.bsuir.bank.deposit.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

import static by.bsuir.bank.common.api.CommonExceptionHandler.REQUIRED;

/** Данные формы заключения депозитного договора. */
public record DepositRequest(

        @NotBlank(message = REQUIRED)
        @Pattern(regexp = NUMBER_FORMAT, message = "Формат номера договора: Д-000001")
        String number,

        @NotNull(message = REQUIRED)
        Long productId,

        @NotBlank(message = REQUIRED)
        String currency,

        @NotNull(message = REQUIRED)
        Long clientId,

        @NotNull(message = REQUIRED)
        @Positive(message = "Сумма должна быть больше нуля")
        @Digits(integer = 12, fraction = 2, message = "Денежная сумма: не более 2 знаков после запятой")
        BigDecimal amount,

        @NotNull(message = REQUIRED)
        BigDecimal rate,

        @NotNull(message = REQUIRED)
        @Positive(message = "Срок должен быть больше нуля")
        Integer termMonths,

        @NotNull(message = REQUIRED)
        LocalDate startDate,

        @NotNull(message = REQUIRED)
        LocalDate endDate
) {

    public static final String NUMBER_FORMAT = "^Д-\\d{6}$";
}
