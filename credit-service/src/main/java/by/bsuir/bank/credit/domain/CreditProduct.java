package by.bsuir.bank.credit.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Кредитная программа — запись справочника «вид кредита». */
@Entity
@Getter
@Setter
public class CreditProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private CreditKind kind;

    private String currency;
    private BigDecimal rate;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private int minTermMonths;
    private int maxTermMonths;
    private String description;

    public String getKindTitle() {
        return kind.getTitle();
    }
}
