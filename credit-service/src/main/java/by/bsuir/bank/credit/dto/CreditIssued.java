package by.bsuir.bank.credit.dto;

/** Результат заключения договора: сам договор и «ПИН-конверт» выпущенной карты (PIN показывается один раз). */
public record CreditIssued(CreditView contract, PinEnvelope card) {
}
