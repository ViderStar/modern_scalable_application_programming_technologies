package by.bsuir.bank.account.repository;

import by.bsuir.bank.account.domain.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrencyRepository extends JpaRepository<Currency, String> {
}
