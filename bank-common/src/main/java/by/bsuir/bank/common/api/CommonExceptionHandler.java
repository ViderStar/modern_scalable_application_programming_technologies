package by.bsuir.bank.common.api;

import by.bsuir.bank.common.i18n.Messages;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Общий обработчик ошибок REST-контроллеров. */
@RestControllerAdvice
public class CommonExceptionHandler {

    /** Ключ сообщения об обязательном поле: в аннотациях Bean Validation он раскрывается на языке запроса. */
    public static final String REQUIRED = "{validation.required}";

    private static final Set<String> REQUIRED_CODES = Set.of("NotNull", "NotBlank", "NotEmpty");

    @ExceptionHandler(BankException.class)
    public ResponseEntity<ApiError> business(BankException e) {
        return ResponseEntity.status(e.getStatus()).body(new ApiError(e.getCode(), e.getMessage(), e.getFields()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalid(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        // по одному сообщению на поле; незаполненное обязательное поле важнее нарушения формата
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            if (REQUIRED_CODES.contains(error.getCode()) || !fields.containsKey(error.getField())) {
                fields.put(error.getField(), String.valueOf(error.getDefaultMessage()));
            }
        }
        return new ApiError("INVALID_REQUEST", Messages.get("error.checkFields"), fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError unreadable(HttpMessageNotReadableException e) {
        return new ApiError("INVALID_REQUEST", Messages.get("error.badRequest"), Map.of());
    }

    /** Смежный сервис ответил ошибкой: его сообщение передаётся дальше, 5xx превращается в 502. */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ApiError> downstream(RestClientResponseException e) {
        ApiError body = null;
        try {
            body = e.getResponseBodyAs(ApiError.class);
        } catch (RuntimeException ignored) {
            // тело ответа не в формате ApiError
        }
        if (body == null || body.message() == null) {
            body = new ApiError("DOWNSTREAM_ERROR", Messages.get("error.downstream"), Map.of());
        }
        HttpStatusCode status = e.getStatusCode().is4xxClientError() ? e.getStatusCode() : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(new ApiError(body.code(), body.message(), Map.of()));
    }

    /** Смежный сервис не отвечает. */
    @ExceptionHandler(ResourceAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError unavailable(ResourceAccessException e) {
        return new ApiError("SERVICE_UNAVAILABLE", Messages.get("error.unavailable"), Map.of());
    }
}
