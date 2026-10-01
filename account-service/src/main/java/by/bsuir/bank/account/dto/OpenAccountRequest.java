package by.bsuir.bank.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Открытие лицевого счёта. clientId не указывается для собственных счетов банка и счетов организаций. */
public record OpenAccountRequest(
        @NotBlank String chartCode,
        @NotBlank String currency,
        @Positive Integer clientId,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 40) String contractRef
) {
}
