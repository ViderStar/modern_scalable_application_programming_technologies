package by.bsuir.bank.account.repository;

import by.bsuir.bank.account.domain.ChartAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChartAccountRepository extends JpaRepository<ChartAccount, String> {
}
