package by.bsuir.bank.common.ledger;

import java.time.LocalDate;
import java.util.List;

public record DayCloseInfo(LocalDate bankDate, List<String> events) {
}
