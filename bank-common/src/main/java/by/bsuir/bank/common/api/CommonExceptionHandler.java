package by.bsuir.bank.common.api;

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

/** Общий обработчик ошибок REST-контроллеров. */
@RestControllerAdvice
public class CommonExceptionHandler {

    public static final String REQUIRED = "Обязательное поле";

    @ExceptionHandler(BankException.class)
    public ResponseEntity<ApiError> business(BankException e) {
        return ResponseEntity.status(e.getStatus()).body(new ApiError(e.getCode(), e.getMessage(), e.getFields()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalid(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            fields.merge(error.getField(), String.valueOf(error.getDefaultMessage()),
                    (old, fresh) -> REQUIRED.equals(fresh) ? fresh : old);
        }
        return new ApiError("INVALID_REQUEST", "Проверьте правильность заполнения полей", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError unreadable(HttpMessageNotReadableException e) {
        return new ApiError("INVALID_REQUEST", "Некорректный формат запроса", Map.of());
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
            body = new ApiError("DOWNSTREAM_ERROR", "Ошибка смежного сервиса", Map.of());
        }
        HttpStatusCode status = e.getStatusCode().is4xxClientError() ? e.getStatusCode() : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(new ApiError(body.code(), body.message(), Map.of()));
    }

    /** Смежный сервис не отвечает. */
    @ExceptionHandler(ResourceAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError unavailable(ResourceAccessException e) {
        return new ApiError("SERVICE_UNAVAILABLE", "Смежный сервис недоступен, повторите операцию позже", Map.of());
    }
}
