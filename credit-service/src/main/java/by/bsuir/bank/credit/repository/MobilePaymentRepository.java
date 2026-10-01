package by.bsuir.bank.credit.repository;

import by.bsuir.bank.credit.domain.MobilePayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MobilePaymentRepository extends JpaRepository<MobilePayment, Long> {
}
