package by.bsuir.bank.client.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** Ошибка бизнес-правила, привязанная к полям формы (дубликат паспорта, отсутствие записи справочника...). */
@Getter
public class FieldErrorException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, String> fields;

    public FieldErrorException(HttpStatus status, String message, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.fields = fields;
    }
}
