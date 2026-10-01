package by.bsuir.bank.account.domain;

import by.bsuir.bank.common.i18n.Messages;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/** Справочник валют. Наименование хранится на трёх языках. */
@Entity
@Getter
@Setter
public class Currency {

    @Id
    private String code;

    private String name;

    @Getter(AccessLevel.NONE)
    private String nameEn;

    @Getter(AccessLevel.NONE)
    private String nameBe;

    public String getName() {
        return Messages.pick(name, nameEn, nameBe);
    }
}
