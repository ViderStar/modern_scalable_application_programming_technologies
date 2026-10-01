package by.bsuir.bank.deposit.repository;

import by.bsuir.bank.deposit.domain.DepositProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepositProductRepository extends JpaRepository<DepositProduct, Long> {
}
