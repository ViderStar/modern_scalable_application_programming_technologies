package by.bsuir.bank.account.repository;

import by.bsuir.bank.account.domain.Operation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OperationRepository extends JpaRepository<Operation, Long> {

    List<Operation> findByBatchKeyOrderById(String batchKey);

    @Query("""
            select distinct o from Operation o join o.entries e
            where (:contractRef is null or o.contractRef = :contractRef)
              and (:account is null or e.account.number = :account)
            order by o.id desc""")
    List<Operation> search(String contractRef, String account, Pageable page);
}
