package by.bsuir.bank.client;

import by.bsuir.bank.client.dto.ClientRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Модульные тесты серверной валидации формы: маски и обязательность полей (пп. 4–8 программы защиты). */
class ClientRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validFormHasNoViolations() {
        assertThat(invalidFields(TestClients.valid())).isEmpty();
    }

    @Test
    void optionalFieldsMayBeOmittedOrEmpty() {
        assertThat(invalidFields(TestClients.onlyRequired())).isEmpty();

        Map<String, Object> blank = TestClients.valid();
        blank.put("homePhone", "");
        blank.put("mobilePhone", " ");
        blank.put("email", "");
        blank.put("monthlyIncome", null);
        assertThat(invalidFields(blank)).isEmpty();
    }

    @ParameterizedTest(name = "без поля {0}")
    @MethodSource("requiredFields")
    void everyRequiredFieldIsChecked(String field) {
        assertThat(invalidFields(TestClients.with(field, null))).containsExactly(field);
    }

    static List<String> requiredFields() {
        return TestClients.REQUIRED_FIELDS;
    }

    @ParameterizedTest(name = "{0} = «{1}»")
    @CsvSource(delimiter = '|', ignoreLeadingAndTrailingWhitespace = false, value = {
            "lastName|1234",
            "lastName|Иванов2",
            "lastName|Иванов-",
            "firstName| ",
            "firstName|Иван!",
            "middleName|_",
            "sex|X",
            "passportSeries|mp",
            "passportSeries|М1",
            "passportSeries|MPP",
            "passportNumber|123456",
            "passportNumber|12345678",
            "passportNumber|12A4567",
            "identificationNumber|3170590A077PB",
            "identificationNumber|3170590a077pb4",
            "identificationNumber|31705900077PB4",
            "homePhone|2014567",
            "homePhone|201-45-6",
            "mobilePhone|+375 (17) 765-43-21",
            "mobilePhone|80297654321",
            "mobilePhone|+375 (29) 765-43-2",
            "email|ivanov@",
            "email|ivanov.example.by",
            "birthDate|29.02.2015",
            "issueDate|31.02.2015",
    })
    void maskedFieldsRejectWrongFormat(String field, String value) {
        assertThat(invalidFields(TestClients.with(field, value))).containsExactly(field);
    }

    @ParameterizedTest
    @CsvSource({"-1", "0.001", "1234567890123"})
    void monthlyIncomeIsMoney(String income) {
        assertThat(invalidFields(TestClients.with("monthlyIncome", income))).containsExactly("monthlyIncome");
    }

    @Test
    void compositeNamesAreAllowed() {
        Map<String, Object> client = TestClients.valid();
        client.put("lastName", "Петрова-Водкина");
        client.put("firstName", "Анна Мария");
        client.put("middleName", "  Ивановна  ");
        ClientRequest request = TestClients.request(client);
        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.middleName()).isEqualTo("Ивановна");
    }

    private Set<String> invalidFields(Map<String, Object> fields) {
        return validator.validate(TestClients.request(fields)).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
