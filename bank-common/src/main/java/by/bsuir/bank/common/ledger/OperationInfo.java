package by.bsuir.bank.common.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record OperationInfo(Long id, LocalDate bankDate, String description, String contractRef,
                            List<EntryInfo> entries) {

    public record EntryInfo(String account, String accountName, String chartCode, Side side, BigDecimal amount) {
    }
}
