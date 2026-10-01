package by.bsuir.bank.client.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

public class DateTextValidator implements ConstraintValidator<DateText, String> {

    private static final LocalDate MIN = LocalDate.of(1900, 1, 1);

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;                     // обязательность проверяет @NotBlank
        }
        return Dates.parse(value)
                .filter(date -> !date.isBefore(MIN) && !date.isAfter(LocalDate.now()))
                .isPresent();
    }
}
