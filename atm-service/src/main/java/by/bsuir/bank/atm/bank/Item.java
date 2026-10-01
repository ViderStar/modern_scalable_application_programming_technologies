package by.bsuir.bank.atm.bank;

/** Элемент внутреннего списка банкомата: имя и значение. */
public record Item(String name, String value) {

    public static final String CARD = "CARD";
    public static final String PIN = "PIN";
    public static final String OPERATION = "OPERATION";
    public static final String AMOUNT = "AMOUNT";
    public static final String OPERATOR = "OPERATOR";
    public static final String PHONE = "PHONE";

    /** Для показа на экране и в журнале обмена PIN-код заменяется звёздочками. */
    public Item masked() {
        return PIN.equals(name) ? new Item(name, "****") : this;
    }
}
