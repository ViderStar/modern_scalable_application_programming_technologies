package by.bsuir.bank.account.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Пакет операций, который проводится атомарно: либо все проводки, либо ни одной.
 * batchKey — ключ идемпотентности; bankDate — дата проводок (по умолчанию текущий банковский день).
 */
public record BatchRequest(
        @NotBlank @Size(max = 80) String batchKey,
        @Size(max = 40) String contractRef,
        LocalDate bankDate,
        @NotEmpty @Valid List<OperationRequest> operations
) {
}
