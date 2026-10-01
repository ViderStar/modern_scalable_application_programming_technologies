package by.bsuir.bank.credit.repository;

import by.bsuir.bank.credit.domain.Card;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {

    Optional<Card> findByNumber(String number);

    Optional<Card> findByContractId(Long contractId);
}
