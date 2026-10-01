package by.bsuir.bank.credit.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Платёж за услуги мобильной связи, проведённый через банкомат. */
@Entity
@Getter
@Setter
public class MobilePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String cardNumber;
    private String operator;
    private String phone;
    private BigDecimal amount;
    private LocalDate bankDate;
    private String reference;
}
