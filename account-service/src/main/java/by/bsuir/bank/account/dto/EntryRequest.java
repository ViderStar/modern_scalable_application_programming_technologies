package by.bsuir.bank.account.dto;

import by.bsuir.bank.account.domain.Side;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record EntryRequest(
        @NotBlank String account,
        @NotNull Side side,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount
) {
}
