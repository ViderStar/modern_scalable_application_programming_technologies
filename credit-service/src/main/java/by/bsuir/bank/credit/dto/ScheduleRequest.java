package by.bsuir.bank.credit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

import static by.bsuir.bank.common.api.CommonExceptionHandler.REQUIRED;

/** Параметры предварительного расчёта графика платежей. */
public record ScheduleRequest(
        @NotNull(message = REQUIRED) Long productId,
        @NotNull(message = REQUIRED) @Positive BigDecimal amount,
        @NotNull(message = REQUIRED) @Positive @Max(600) Integer termMonths
) {
}
