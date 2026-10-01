package by.bsuir.bank.account.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Банковская операция (проводка): одна строка схемы «Депозитная/Кредитная программа». */
@Entity
@Getter
@Setter
public class Operation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String batchKey;
    private LocalDate bankDate;
    private String description;
    private String contractRef;
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "operation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OperationEntry> entries = new ArrayList<>();

    public void addEntry(Account account, Side side, java.math.BigDecimal amount) {
        OperationEntry entry = new OperationEntry();
        entry.setOperation(this);
        entry.setAccount(account);
        entry.setSide(side);
        entry.setAmount(amount);
        entries.add(entry);
    }
}
