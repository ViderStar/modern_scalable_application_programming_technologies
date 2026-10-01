package by.bsuir.bank.credit.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

import static by.bsuir.bank.common.api.CommonExceptionHandler.REQUIRED;

/** Выдача наличных с кредитного счёта через кассу. */
public record CashRequest(
        @NotNull(message = REQUIRED)
        @Positive(message = "{validation.amountPositive}")
        @Digits(integer = 12, fraction = 2, message = "{validation.amountFormat}")
        BigDecimal amount
) {
}
