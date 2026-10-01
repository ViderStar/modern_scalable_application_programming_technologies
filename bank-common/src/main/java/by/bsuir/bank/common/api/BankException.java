package by.bsuir.bank.common.api;

import by.bsuir.bank.common.i18n.Messages;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** Нарушение бизнес-правила: превращается обработчиком в ответ с кодом состояния и телом ApiError. */
@Getter
public class BankException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fields;

    public BankException(HttpStatus status, String code, String message, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.code = code;
        this.fields = fields;
    }

    public BankException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public static BankException notFound(String message) {
        return new BankException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static BankException invalid(String message) {
        return new BankException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    public static BankException conflict(String code, String message) {
        return new BankException(HttpStatus.CONFLICT, code, message);
    }

    /** Ошибки, привязанные к полям формы. */
    public static BankException fields(Map<String, String> fields) {
        return new BankException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", Messages.get("error.checkFields"), fields);
    }
}
