package by.bsuir.bank.client.domain;

import by.bsuir.bank.common.i18n.Messages;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/** Общая структура справочников: идентификатор и наименование на трёх языках. */
@MappedSuperclass
@Getter
@Setter
public abstract class DictionaryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Getter(AccessLevel.NONE)
    private String nameEn;

    @Getter(AccessLevel.NONE)
    private String nameBe;

    /** Наименование на языке запроса: именно оно попадает в ответы REST. */
    public String getName() {
        return Messages.pick(name, nameEn, nameBe);
    }
}
