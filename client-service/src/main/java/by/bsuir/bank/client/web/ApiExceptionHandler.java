package by.bsuir.bank.client.web;

import by.bsuir.bank.client.dto.ApiError;
import by.bsuir.bank.client.service.FieldErrorException;
import by.bsuir.bank.client.service.NotFoundException;
import by.bsuir.bank.common.i18n.Messages;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Set<String> REQUIRED_CODES = Set.of("NotNull", "NotBlank");

    /** Нарушения Bean Validation: по одному сообщению на поле, незаполненное обязательное поле в приоритете. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalid(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            if (REQUIRED_CODES.contains(error.getCode()) || !fields.containsKey(error.getField())) {
                fields.put(error.getField(), error.getDefaultMessage());
            }
        }
        return new ApiError(Messages.get("error.checkFields"), fields);
    }

    /** JSON не разобран: например, в денежное поле или в идентификатор справочника пришёл текст. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError unreadable(HttpMessageNotReadableException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (e.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            fields.put(mapping.getPath().get(0).getFieldName(), Messages.get("client.invalidValue"));
        }
        return new ApiError(Messages.get(fields.isEmpty() ? "error.badRequest" : "error.checkFields"), fields);
    }

    @ExceptionHandler(FieldErrorException.class)
    public ResponseEntity<ApiError> business(FieldErrorException e) {
        return ResponseEntity.status(e.getStatus()).body(new ApiError(e.getMessage(), e.getFields()));
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError notFound(NotFoundException e) {
        return new ApiError(e.getMessage(), Map.of());
    }

    /** Сработало ограничение уникальности в самой базе (гонка двух одновременных запросов). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError integrity(DataIntegrityViolationException e) {
        return new ApiError(Messages.get("client.integrity"), Map.of());
    }
}
