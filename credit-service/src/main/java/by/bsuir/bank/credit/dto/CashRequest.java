package by.bsuir.bank.credit.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

import static by.bsuir.bank.common.api.CommonExceptionHandler.REQUIRED;

/** Выдача наличных с кредитного счёта через кассу. */
public record CashRequest(
        @NotNull(message = REQUIRED)
        @Positive(message = "Сумма должна быть больше нуля")
        @Digits(integer = 12, fraction = 2, message = "Денежная сумма: не более 2 знаков после запятой")
        BigDecimal amount
) {
}
