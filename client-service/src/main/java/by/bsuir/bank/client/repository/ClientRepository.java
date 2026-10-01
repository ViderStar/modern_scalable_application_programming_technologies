package by.bsuir.bank.client.repository;

import by.bsuir.bank.client.domain.Client;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    /** Список клиентов, отсортированный по фамилии (затем по имени и отчеству). */
    @EntityGraph(attributePaths = {"residenceCity", "registrationCity", "maritalStatus", "citizenship", "disability"})
    List<Client> findAllByOrderByLastNameAscFirstNameAscMiddleNameAsc();

    @EntityGraph(attributePaths = {"residenceCity", "registrationCity", "maritalStatus", "citizenship", "disability"})
    Optional<Client> findWithDictionariesById(Long id);

    Optional<Client> findByPassportSeriesAndPassportNumber(String series, String number);

    Optional<Client> findByIdentificationNumber(String identificationNumber);
}
