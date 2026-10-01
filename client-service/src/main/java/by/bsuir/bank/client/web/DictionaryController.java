package by.bsuir.bank.client.web;

import by.bsuir.bank.client.dto.Dictionaries;
import by.bsuir.bank.client.repository.CitizenshipRepository;
import by.bsuir.bank.client.repository.CityRepository;
import by.bsuir.bank.client.repository.DisabilityRepository;
import by.bsuir.bank.client.repository.MaritalStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
        return new Dictionaries(cities.findAll(Sort.by("name")), maritalStatuses.findAll(byId),
                citizenships.findAll(byId), disabilities.findAll(byId));
    }
}
