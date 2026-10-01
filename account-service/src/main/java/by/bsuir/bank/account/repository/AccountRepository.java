package by.bsuir.bank.account.repository;

import by.bsuir.bank.account.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByNumber(String number);

    /** Счёт блокируется на время проводки: параллельные операции не теряют обновления оборотов. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.number = :number")
    Optional<Account> findByNumberForUpdate(String number);

    Optional<Account> findByContractRefAndChartCode(String contractRef, String chartCode);

    /** Собственный счёт банка (касса, фонд развития) в заданной валюте. */
    Optional<Account> findFirstByChartCodeAndCurrencyAndOwnerCodeAndContractRefIsNull(
            String chartCode, String currency, int ownerCode);

    List<Account> findAllByOrderByChartCodeAscNumberAsc();

    List<Account> findByContractRefOrderByNumber(String contractRef);

    List<Account> findByOwnerCodeOrderByNumber(int ownerCode);

    @Query("select coalesce(max(a.ownerSeq), 0) from Account a where a.ownerCode = :ownerCode")
    int maxOwnerSeq(int ownerCode);
}
