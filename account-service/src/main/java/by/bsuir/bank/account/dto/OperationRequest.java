package by.bsuir.bank.account.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OperationRequest(
        @NotBlank @Size(max = 200) String description,
        @NotEmpty @Valid List<EntryRequest> entries
) {
}
