package by.bsuir.bank.client.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Строка содержит существующую календарную дату ДД.ММ.ГГГГ в диапазоне от 01.01.1900 до сегодняшнего дня. */
@Documented
@Constraint(validatedBy = DateTextValidator.class)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface DateText {

    String message() default "Укажите существующую дату ДД.ММ.ГГГГ не позднее сегодняшней";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
