package by.bsuir.bank.account.repository;

import by.bsuir.bank.account.domain.BankDay;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankDayRepository extends JpaRepository<BankDay, Integer> {
}
