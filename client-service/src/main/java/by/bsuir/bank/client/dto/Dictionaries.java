package by.bsuir.bank.client.dto;

import by.bsuir.bank.client.domain.Citizenship;
import by.bsuir.bank.client.domain.City;
import by.bsuir.bank.client.domain.Disability;
import by.bsuir.bank.client.domain.MaritalStatus;

import java.util.List;

/** Содержимое всех справочников — для выпадающих списков формы. */
public record Dictionaries(
        List<City> cities,
        List<MaritalStatus> maritalStatuses,
        List<Citizenship> citizenships,
        List<Disability> disabilities
) {
}
