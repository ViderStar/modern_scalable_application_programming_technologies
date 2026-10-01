package by.bsuir.bank.deposit.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Депозитная программа — запись справочника «вид депозита». */
@Entity
@Getter
@Setter
public class DepositProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private DepositKind kind;

    private String currency;
    private BigDecimal rate;
    private BigDecimal minAmount;
    private int minTermMonths;
    private int maxTermMonths;
    private String description;

    public String getKindTitle() {
        return kind.getTitle();
    }
}
