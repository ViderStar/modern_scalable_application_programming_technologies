package by.bsuir.bank.client.repository;

import by.bsuir.bank.client.domain.Citizenship;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CitizenshipRepository extends JpaRepository<Citizenship, Long> {
}
