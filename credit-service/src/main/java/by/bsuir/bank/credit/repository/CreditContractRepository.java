package by.bsuir.bank.credit.repository;

import by.bsuir.bank.credit.domain.ContractStatus;
import by.bsuir.bank.credit.domain.CreditContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CreditContractRepository extends JpaRepository<CreditContract, Long> {

    boolean existsByNumber(String number);

    List<CreditContract> findAllByOrderByIdDesc();

    @Query("select c.id from CreditContract c where c.status = :status order by c.id")
    List<Long> findIdsByStatus(ContractStatus status);

    @Query("select max(c.number) from CreditContract c")
    String maxNumber();
}
