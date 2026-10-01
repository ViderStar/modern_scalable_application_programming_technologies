package by.bsuir.bank.atm.bank;

/** Демонстрационная карта: номер, PIN-код (null — если перевыпущен), держатель и признак блокировки. */
public record DemoCard(String cardNumber, String pin, String holder, String contract, boolean blocked) {
}
