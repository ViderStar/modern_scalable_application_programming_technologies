package by.bsuir.bank.deposit.repository;

import by.bsuir.bank.deposit.domain.DepositContract;
import by.bsuir.bank.deposit.domain.DepositStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DepositContractRepository extends JpaRepository<DepositContract, Long> {

    boolean existsByNumber(String number);

    List<DepositContract> findAllByOrderByIdDesc();

    List<DepositContract> findByClientIdOrderById(Long clientId);

    @Query("select c.id from DepositContract c where c.status = :status order by c.id")
    List<Long> findIdsByStatus(DepositStatus status);

    @Query("select max(c.number) from DepositContract c")
    String maxNumber();
}
