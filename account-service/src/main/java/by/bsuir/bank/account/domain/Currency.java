package by.bsuir.bank.account.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

/** Справочник валют. */
@Entity
@Getter
@Setter
public class Currency {

    @Id
    private String code;

    private String name;
}
