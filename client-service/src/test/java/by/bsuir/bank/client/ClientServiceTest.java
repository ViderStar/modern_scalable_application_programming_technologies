package by.bsuir.bank.client;

import by.bsuir.bank.client.domain.Citizenship;
import by.bsuir.bank.client.domain.City;
import by.bsuir.bank.client.domain.Client;
import by.bsuir.bank.client.domain.Disability;
import by.bsuir.bank.client.domain.MaritalStatus;
import by.bsuir.bank.client.dto.ClientResponse;
import by.bsuir.bank.client.repository.CitizenshipRepository;
import by.bsuir.bank.client.repository.CityRepository;
import by.bsuir.bank.client.repository.ClientRepository;
import by.bsuir.bank.client.repository.DisabilityRepository;
import by.bsuir.bank.client.repository.MaritalStatusRepository;
import by.bsuir.bank.client.service.ClientService;
import by.bsuir.bank.client.service.FieldErrorException;
import by.bsuir.bank.client.service.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Модульные тесты бизнес-правил сервиса на заглушках репозиториев (пп. 1–3 программы защиты). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientServiceTest {

    @Mock
    private ClientRepository clients;
    @Mock
    private CityRepository cities;
    @Mock
    private MaritalStatusRepository maritalStatuses;
    @Mock
    private CitizenshipRepository citizenships;
    @Mock
    private DisabilityRepository disabilities;

    @InjectMocks
    private ClientService service;

    @BeforeEach
    void dictionaries() {
        when(cities.findById(anyLong())).thenReturn(Optional.of(named(new City(), "Минск")));
        when(maritalStatuses.findById(anyLong())).thenReturn(Optional.of(named(new MaritalStatus(), "Холост")));
        when(citizenships.findById(anyLong())).thenReturn(Optional.of(named(new Citizenship(), "Республика Беларусь")));
        when(disabilities.findById(anyLong())).thenReturn(Optional.of(named(new Disability(), "Нет")));
        when(clients.saveAndFlush(any(Client.class))).thenAnswer(call -> {
            Client client = call.getArgument(0);
            client.setId(100L);
            return client;
        });
    }

    @Test
    void savesNewClient() {
        ClientResponse saved = service.create(TestClients.request(TestClients.valid()));

        assertThat(saved.id()).isEqualTo(100L);
        assertThat(saved.fullName()).isEqualTo("Иванов Иван Иванович");
        assertThat(saved.birthDate()).isEqualTo("17.05.1990");
        assertThat(saved.residenceCity()).isEqualTo("Минск");
    }

    @Test
    void emptyOptionalFieldsAreStoredAsNull() {
        var fields = TestClients.valid();
        fields.put("homePhone", "");
        fields.put("email", " ");

        ClientResponse saved = service.create(TestClients.request(fields));

        assertThat(saved.homePhone()).isNull();
        assertThat(saved.email()).isNull();
    }

    @Test
    void rejectsSecondClientWithSamePassport() {
        when(clients.findByPassportSeriesAndPassportNumber("MP", "7654321")).thenReturn(Optional.of(existing(7L)));

        assertThatThrownBy(() -> service.create(TestClients.request(TestClients.valid())))
                .isInstanceOfSatisfying(FieldErrorException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getFields()).containsOnlyKeys("passportSeries", "passportNumber");
                });
        verify(clients, never()).saveAndFlush(any());
    }

    @Test
    void rejectsSecondClientWithSameIdentificationNumber() {
        when(clients.findByIdentificationNumber("3170590A077PB4")).thenReturn(Optional.of(existing(7L)));

        assertThatThrownBy(() -> service.create(TestClients.request(TestClients.valid())))
                .isInstanceOfSatisfying(FieldErrorException.class,
                        e -> assertThat(e.getFields()).containsOnlyKeys("identificationNumber"));
        verify(clients, never()).saveAndFlush(any());
    }

    @Test
    void clientMayKeepOwnPassportOnUpdate() {
        Client self = existing(7L);
        when(clients.findById(7L)).thenReturn(Optional.of(self));
        when(clients.findByPassportSeriesAndPassportNumber("MP", "7654321")).thenReturn(Optional.of(self));
        when(clients.findByIdentificationNumber("3170590A077PB4")).thenReturn(Optional.of(self));

        service.update(7L, TestClients.request(TestClients.with("lastName", "Петров")));

        assertThat(self.getLastName()).isEqualTo("Петров");
        verify(clients).saveAndFlush(self);
    }

    @Test
    void passportCannotBeIssuedBeforeBirth() {
        var fields = TestClients.with("issueDate", "16.05.1990");

        assertThatThrownBy(() -> service.create(TestClients.request(fields)))
                .isInstanceOfSatisfying(FieldErrorException.class,
                        e -> assertThat(e.getFields()).containsOnlyKeys("issueDate"));
    }

    @Test
    void rejectsValueMissingInDictionary() {
        when(cities.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(TestClients.request(TestClients.with("residenceCityId", 99))))
                .isInstanceOfSatisfying(FieldErrorException.class,
                        e -> assertThat(e.getFields()).containsOnlyKeys("residenceCityId"));
    }

    @Test
    void deletingUnknownClientFails() {
        when(clients.existsById(5L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(5L)).isInstanceOf(NotFoundException.class);
        verify(clients, never()).deleteById(any());
    }

    private static Client existing(Long id) {
        Client client = new Client();
        client.setId(id);
        return client;
    }

    private static <T extends by.bsuir.bank.client.domain.DictionaryItem> T named(T item, String name) {
        item.setId(1L);
        item.setName(name);
        return item;
    }
}
