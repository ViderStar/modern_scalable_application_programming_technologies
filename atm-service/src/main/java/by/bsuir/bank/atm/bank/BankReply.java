package by.bsuir.bank.atm.bank;

import java.util.Map;

/** Ответ банка на транзакцию: статус OK/ERROR, код, текст для экрана и данные операции. */
public record BankReply(String status, String code, String message, Map<String, Object> data) {

    public boolean ok() {
        return "OK".equals(status);
    }

    public Object get(String key) {
        return data == null ? null : data.get(key);
    }
}
