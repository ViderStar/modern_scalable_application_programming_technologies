package by.bsuir.bank.client.dto;

import by.bsuir.bank.client.domain.Client;
import by.bsuir.bank.client.validation.Dates;

import java.math.BigDecimal;

public record ClientResponse(
        Long id,
        String fullName,
        String lastName,
        String firstName,
        String middleName,
        String birthDate,
        String sex,
        String passportSeries,
        String passportNumber,
        String issuedBy,
        String issueDate,
        String identificationNumber,
        String birthPlace,
        Long residenceCityId,
        String residenceCity,
        String residenceAddress,
        String homePhone,
        String mobilePhone,
        String email,
        Long registrationCityId,
        String registrationCity,
        Long maritalStatusId,
        String maritalStatus,
        Long citizenshipId,
        String citizenship,
        Long disabilityId,
        String disability,
        boolean pensioner,
        BigDecimal monthlyIncome
) {

    public static ClientResponse from(Client c) {
        return new ClientResponse(
                c.getId(), c.getFullName(), c.getLastName(), c.getFirstName(), c.getMiddleName(),
                Dates.format(c.getBirthDate()), c.getSex().name(),
                c.getPassportSeries(), c.getPassportNumber(), c.getIssuedBy(), Dates.format(c.getIssueDate()),
                c.getIdentificationNumber(), c.getBirthPlace(),
                c.getResidenceCity().getId(), c.getResidenceCity().getName(), c.getResidenceAddress(),
                c.getHomePhone(), c.getMobilePhone(), c.getEmail(),
                c.getRegistrationCity().getId(), c.getRegistrationCity().getName(),
                c.getMaritalStatus().getId(), c.getMaritalStatus().getName(),
                c.getCitizenship().getId(), c.getCitizenship().getName(),
                c.getDisability().getId(), c.getDisability().getName(),
                c.isPensioner(), c.getMonthlyIncome());
    }
}
