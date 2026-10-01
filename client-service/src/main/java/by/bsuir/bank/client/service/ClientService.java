package by.bsuir.bank.client.service;

import by.bsuir.bank.client.domain.Client;
import by.bsuir.bank.client.domain.Sex;
import by.bsuir.bank.client.dto.ClientRequest;
import by.bsuir.bank.client.dto.ClientResponse;
import by.bsuir.bank.client.repository.CitizenshipRepository;
import by.bsuir.bank.client.repository.CityRepository;
import by.bsuir.bank.client.repository.ClientRepository;
import by.bsuir.bank.client.repository.DisabilityRepository;
import by.bsuir.bank.client.repository.MaritalStatusRepository;
import by.bsuir.bank.client.validation.Dates;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClientService {

    static final String DUPLICATE_PASSPORT = "Клиент с таким паспортом уже зарегистрирован";
    static final String DUPLICATE_IDENTIFICATION = "Клиент с таким идентификационным номером уже зарегистрирован";

    private final ClientRepository clients;
    private final CityRepository cities;
    private final MaritalStatusRepository maritalStatuses;
    private final CitizenshipRepository citizenships;
    private final DisabilityRepository disabilities;

    @Transactional(readOnly = true)
    public List<ClientResponse> findAll() {
        return clients.findAllByOrderByLastNameAscFirstNameAscMiddleNameAsc().stream()
                .map(ClientResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse get(Long id) {
        return clients.findWithDictionariesById(id)
                .map(ClientResponse::from)
                .orElseThrow(() -> notFound(id));
    }

    @Transactional
    public ClientResponse create(ClientRequest request) {
        return save(new Client(), request);
    }

    @Transactional
    public ClientResponse update(Long id, ClientRequest request) {
        return save(clients.findById(id).orElseThrow(() -> notFound(id)), request);
    }

    @Transactional
    public void delete(Long id) {
        if (!clients.existsById(id)) {
            throw notFound(id);
        }
        clients.deleteById(id);
        clients.flush();
    }

    private ClientResponse save(Client client, ClientRequest request) {
        LocalDate birthDate = Dates.parse(request.birthDate()).orElseThrow();
        LocalDate issueDate = Dates.parse(request.issueDate()).orElseThrow();
        if (issueDate.isBefore(birthDate)) {
            throw new FieldErrorException(HttpStatus.BAD_REQUEST, "Проверьте правильность заполнения полей",
                    Map.of("issueDate", "Паспорт не может быть выдан раньше даты рождения"));
        }
        checkUnique(request, client.getId());

        client.setLastName(request.lastName());
        client.setFirstName(request.firstName());
        client.setMiddleName(request.middleName());
        client.setBirthDate(birthDate);
        client.setSex(Sex.valueOf(request.sex()));
        client.setPassportSeries(request.passportSeries());
        client.setPassportNumber(request.passportNumber());
        client.setIssuedBy(request.issuedBy());
        client.setIssueDate(issueDate);
        client.setIdentificationNumber(request.identificationNumber());
        client.setBirthPlace(request.birthPlace());
        client.setResidenceCity(reference(cities, request.residenceCityId(), "residenceCityId"));
        client.setResidenceAddress(request.residenceAddress());
        client.setHomePhone(blankToNull(request.homePhone()));
        client.setMobilePhone(blankToNull(request.mobilePhone()));
        client.setEmail(blankToNull(request.email()));
        client.setRegistrationCity(reference(cities, request.registrationCityId(), "registrationCityId"));
        client.setMaritalStatus(reference(maritalStatuses, request.maritalStatusId(), "maritalStatusId"));
        client.setCitizenship(reference(citizenships, request.citizenshipId(), "citizenshipId"));
        client.setDisability(reference(disabilities, request.disabilityId(), "disabilityId"));
        client.setPensioner(request.pensioner());
        client.setMonthlyIncome(request.monthlyIncome());

        return ClientResponse.from(clients.saveAndFlush(client));
    }

    /** Паспорт и идентификационный номер уникальны; при редактировании собственная запись клиента не в счёт. */
    private void checkUnique(ClientRequest request, Long selfId) {
        Map<String, String> errors = new LinkedHashMap<>();
        clients.findByPassportSeriesAndPassportNumber(request.passportSeries(), request.passportNumber())
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    errors.put("passportSeries", DUPLICATE_PASSPORT);
                    errors.put("passportNumber", DUPLICATE_PASSPORT);
                });
        clients.findByIdentificationNumber(request.identificationNumber())
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> errors.put("identificationNumber", DUPLICATE_IDENTIFICATION));
        if (!errors.isEmpty()) {
            throw new FieldErrorException(HttpStatus.CONFLICT, "Такой клиент уже есть в базе", errors);
        }
    }

    private static <T> T reference(JpaRepository<T, Long> dictionary, Long id, String field) {
        return dictionary.findById(id).orElseThrow(() -> new FieldErrorException(HttpStatus.BAD_REQUEST,
                "Проверьте правильность заполнения полей", Map.of(field, "Значение отсутствует в справочнике")));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static NotFoundException notFound(Long id) {
        return new NotFoundException("Клиент с идентификатором " + id + " не найден");
    }
}
