package by.bsuir.bank.credit.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

/** Оператор мобильной связи — получатель платежей из банкомата. Название — торговая марка, не переводится. */
@Entity
@Getter
@Setter
public class MobileOperator {

    @Id
    private String code;

    private String name;
}
