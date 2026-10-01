package by.bsuir.bank.account.dto;

import by.bsuir.bank.account.domain.Operation;
import by.bsuir.bank.account.domain.Side;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record OperationView(
        Long id,
        LocalDate bankDate,
        String description,
        String contractRef,
        List<EntryView> entries
) {

    public record EntryView(String account, String accountName, String chartCode, Side side, BigDecimal amount) {
    }

    public static OperationView from(Operation o) {
        return new OperationView(o.getId(), o.getBankDate(), o.getDescription(), o.getContractRef(),
                o.getEntries().stream()
                        .map(e -> new EntryView(e.getAccount().getNumber(), e.getAccount().getName(),
                                e.getAccount().getChart().getCode(), e.getSide(), e.getAmount()))
                        .toList());
    }
}
