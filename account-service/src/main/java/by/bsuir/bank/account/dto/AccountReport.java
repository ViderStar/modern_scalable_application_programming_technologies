package by.bsuir.bank.account.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Отчёт о состоянии счетов на текущий банковский день с итогами оборотов по валютам. */
public record AccountReport(LocalDate bankDate, List<AccountView> accounts, List<Total> totals) {

    public record Total(String currency, BigDecimal debit, BigDecimal credit) {
    }
}
