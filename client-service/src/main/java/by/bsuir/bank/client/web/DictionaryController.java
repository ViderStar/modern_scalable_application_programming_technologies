package by.bsuir.bank.client.web;

import by.bsuir.bank.client.domain.City;
import by.bsuir.bank.client.dto.Dictionaries;
import by.bsuir.bank.client.repository.CitizenshipRepository;
import by.bsuir.bank.client.repository.CityRepository;
import by.bsuir.bank.client.repository.DisabilityRepository;
import by.bsuir.bank.client.repository.MaritalStatusRepository;
import by.bsuir.bank.common.i18n.Messages;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class DictionaryController {

    private final CityRepository cities;
    private final MaritalStatusRepository maritalStatuses;
    private final CitizenshipRepository citizenships;
    private final DisabilityRepository disabilities;

    @GetMapping("/api/dictionaries")
    public Dictionaries dictionaries() {
        Sort byId = Sort.by("id");
        // города сортируются по наименованию на языке запроса, а не по столбцу базы
        Collator collator = Collator.getInstance(Messages.locale());
        List<City> sorted = cities.findAll().stream()
                .sorted(Comparator.comparing(City::getName, collator))
                .toList();
        return new Dictionaries(sorted, maritalStatuses.findAll(byId),
                citizenships.findAll(byId), disabilities.findAll(byId));
    }
}
