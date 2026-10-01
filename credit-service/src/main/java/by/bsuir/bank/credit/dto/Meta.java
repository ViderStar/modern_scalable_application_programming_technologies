package by.bsuir.bank.credit.dto;

import by.bsuir.bank.common.ledger.CurrencyInfo;

import java.time.LocalDate;
import java.util.List;

/** Данные для формы договора: банковский день, справочник валют, следующий свободный номер договора. */
public record Meta(LocalDate bankDate, List<CurrencyInfo> currencies, String nextNumber) {
}
