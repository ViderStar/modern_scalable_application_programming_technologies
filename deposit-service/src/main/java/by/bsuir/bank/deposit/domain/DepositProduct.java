package by.bsuir.bank.deposit.domain;

import by.bsuir.bank.common.i18n.Messages;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
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

    @Getter(AccessLevel.NONE)
    private String nameEn;

    @Getter(AccessLevel.NONE)
    private String nameBe;

    @Enumerated(EnumType.STRING)
    private DepositKind kind;

    private String currency;
    private BigDecimal rate;
    private BigDecimal minAmount;
    private int minTermMonths;
    private int maxTermMonths;
    private String description;

    @Getter(AccessLevel.NONE)
    private String descriptionEn;

    @Getter(AccessLevel.NONE)
    private String descriptionBe;

    /** Название и описание программы отдаются на языке запроса. */
    public String getName() {
        return Messages.pick(name, nameEn, nameBe);
    }

    public String getDescription() {
        return Messages.pick(description, descriptionEn, descriptionBe);
    }

    public String getKindTitle() {
        return kind.getTitle();
    }
}
