package by.bsuir.bank.account.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** Текущий открытый банковский день — единственная строка таблицы. */
@Entity
@Getter
@Setter
public class BankDay {

    public static final int ID = 1;

    @Id
    private Integer id;

    private LocalDate bankDate;
}
