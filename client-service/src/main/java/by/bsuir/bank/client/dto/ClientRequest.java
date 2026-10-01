package by.bsuir.bank.client.dto;

import by.bsuir.bank.client.validation.DateText;
import by.bsuir.bank.client.validation.Masks;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Данные формы клиента. Серверная валидация (вторая линия после проверки в браузере):
 * обязательность — @NotBlank/@NotNull, маски — @Pattern, даты — @DateText.
 * Сообщения заданы ключами {…}: текст берётся из messages*.properties на языке запроса.
 */
public record ClientRequest(

        @NotBlank(message = REQUIRED) @Size(max = 60, message = TOO_LONG)
        @Pattern(regexp = Masks.NAME, message = LETTERS_ONLY)
        String lastName,

        @NotBlank(message = REQUIRED) @Size(max = 60, message = TOO_LONG)
        @Pattern(regexp = Masks.NAME, message = LETTERS_ONLY)
        String firstName,

        @NotBlank(message = REQUIRED) @Size(max = 60, message = TOO_LONG)
        @Pattern(regexp = Masks.NAME, message = LETTERS_ONLY)
        String middleName,

        @NotBlank(message = REQUIRED) @DateText
        String birthDate,

        @NotBlank(message = REQUIRED) @Pattern(regexp = "^[MF]$", message = "{validation.sex}")
        String sex,

        @NotBlank(message = REQUIRED)
        @Pattern(regexp = Masks.PASSPORT_SERIES, message = "{validation.passportSeries}")
        String passportSeries,

        @NotBlank(message = REQUIRED)
        @Pattern(regexp = Masks.PASSPORT_NUMBER, message = "{validation.passportNumber}")
        String passportNumber,

        @NotBlank(message = REQUIRED) @Size(max = 200, message = TOO_LONG)
        String issuedBy,

        @NotBlank(message = REQUIRED) @DateText
        String issueDate,

        @NotBlank(message = REQUIRED)
        @Pattern(regexp = Masks.IDENTIFICATION_NUMBER, message = "{validation.identificationNumber}")
        String identificationNumber,

        @NotBlank(message = REQUIRED) @Size(max = 200, message = TOO_LONG)
        String birthPlace,

        @NotNull(message = REQUIRED)
        Long residenceCityId,

        @NotBlank(message = REQUIRED) @Size(max = 200, message = TOO_LONG)
        String residenceAddress,

        @Pattern(regexp = Masks.HOME_PHONE, message = "{validation.homePhone}")
        String homePhone,

        @Pattern(regexp = Masks.MOBILE_PHONE, message = "{validation.mobilePhone}")
        String mobilePhone,

        @Size(max = 100, message = TOO_LONG) @Pattern(regexp = Masks.EMAIL, message = "{validation.email}")
        String email,

        @NotNull(message = REQUIRED)
        Long registrationCityId,

        @NotNull(message = REQUIRED)
        Long maritalStatusId,

        @NotNull(message = REQUIRED)
        Long citizenshipId,

        @NotNull(message = REQUIRED)
        Long disabilityId,

        @NotNull(message = REQUIRED)
        Boolean pensioner,

        @DecimalMin(value = "0.00", message = "{validation.incomeNegative}")
        @Digits(integer = 12, fraction = 2, message = "{validation.incomeFormat}")
        BigDecimal monthlyIncome
) {

    public static final String REQUIRED = "{validation.required}";
    public static final String TOO_LONG = "{validation.tooLong}";
    public static final String LETTERS_ONLY = "{validation.lettersOnly}";

    /** Пробелы по краям отбрасываются до проверок: «пробел вместо имени» превращается в пустую строку. */
    public ClientRequest {
        lastName = trim(lastName);
        firstName = trim(firstName);
        middleName = trim(middleName);
        birthDate = trim(birthDate);
        passportSeries = trim(passportSeries);
        passportNumber = trim(passportNumber);
        issuedBy = trim(issuedBy);
        issueDate = trim(issueDate);
        identificationNumber = trim(identificationNumber);
        birthPlace = trim(birthPlace);
        residenceAddress = trim(residenceAddress);
        homePhone = trim(homePhone);
        mobilePhone = trim(mobilePhone);
        email = trim(email);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
