package by.bsuir.bank.credit.atm;

import java.util.Map;

/** Ответ банка банкомату: статус OK/ERROR, код и текст для экрана, данные для чека. */
public record AtmResponse(String status, String code, String message, Map<String, Object> data) {

    public static AtmResponse ok(String message, Map<String, Object> data) {
        return new AtmResponse("OK", "OK", message, data);
    }

    public static AtmResponse error(String code, String message) {
        return new AtmResponse("ERROR", code, message, Map.of());
    }

    public static AtmResponse error(String code, String message, Map<String, Object> data) {
        return new AtmResponse("ERROR", code, message, data);
    }
}
