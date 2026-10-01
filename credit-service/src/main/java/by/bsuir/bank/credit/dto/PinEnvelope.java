package by.bsuir.bank.credit.dto;

/** «ПИН-конверт»: номер карты и PIN-код в открытом виде. В базе остаётся только хеш PIN-кода. */
public record PinEnvelope(String cardNumber, String pin) {
}
