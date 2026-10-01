package by.bsuir.bank.account.dto;

import by.bsuir.bank.account.domain.Operation;
import by.bsuir.bank.account.domain.Side;
import by.bsuir.bank.common.i18n.Localized;

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
        // название операции и наименование счёта банка хранятся кодом и раскрываются на языке запроса
        return new OperationView(o.getId(), o.getBankDate(), Localized.render(o.getDescription()), o.getContractRef(),
                o.getEntries().stream()
                        .map(e -> new EntryView(e.getAccount().getNumber(), Localized.render(e.getAccount().getName()),
                                e.getAccount().getChart().getCode(), e.getSide(), e.getAmount()))
                        .toList());
    }
}
