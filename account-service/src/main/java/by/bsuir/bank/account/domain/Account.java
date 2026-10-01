package by.bsuir.bank.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Лицевой счёт: обороты по дебету и кредиту накапливаются, сальдо вычисляется по активности. */
@Entity
@Getter
@Setter
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String number;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "chart_code")
    private ChartAccount chart;

    @Column(name = "currency_code")
    private String currency;

    private String name;
    private int ownerCode;
    private int ownerSeq;
    private String contractRef;
    private BigDecimal debit = BigDecimal.ZERO;
    private BigDecimal credit = BigDecimal.ZERO;
    private LocalDate openedOn;

    public BigDecimal getBalance() {
        return chart.getActivity().balance(debit, credit);
    }

    public void apply(Side side, BigDecimal amount) {
        if (side == Side.DEBIT) {
            debit = debit.add(amount);
        } else {
            credit = credit.add(amount);
        }
    }
}
