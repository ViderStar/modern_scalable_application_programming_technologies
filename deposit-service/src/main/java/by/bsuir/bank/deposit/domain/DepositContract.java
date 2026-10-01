package by.bsuir.bank.deposit.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Депозитный договор с физическим лицом. */
@Entity
@Getter
@Setter
public class DepositContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String number;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private DepositProduct product;

    private Long clientId;
    private String clientName;
    private String currency;
    private BigDecimal amount;
    private BigDecimal rate;
    private int termMonths;
    private LocalDate startDate;
    private LocalDate endDate;
    private String mainAccount;
    private String interestAccount;

    @Enumerated(EnumType.STRING)
    private DepositStatus status;

    private BigDecimal accrued = BigDecimal.ZERO;
    private BigDecimal paid = BigDecimal.ZERO;
    private int paidMonths;
    private LocalDate closedOn;

    public boolean isActive() {
        return status == DepositStatus.ACTIVE;
    }
}
