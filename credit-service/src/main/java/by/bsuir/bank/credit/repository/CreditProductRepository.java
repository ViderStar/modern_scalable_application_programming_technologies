package by.bsuir.bank.credit.repository;

import by.bsuir.bank.credit.domain.CreditProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditProductRepository extends JpaRepository<CreditProduct, Long> {
}
