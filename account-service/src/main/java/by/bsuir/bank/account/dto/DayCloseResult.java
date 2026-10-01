package by.bsuir.bank.account.dto;

import java.time.LocalDate;
import java.util.List;

/** Итог закрытия дня: новая дата банковского дня и протокол выполненных операций. */
public record DayCloseResult(LocalDate bankDate, List<String> events) {
}
