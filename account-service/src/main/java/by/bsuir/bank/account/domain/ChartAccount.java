package by.bsuir.bank.account.domain;

import by.bsuir.bank.common.i18n.Messages;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/** Запись справочника «План счетов» — балансовый счёт. Наименование хранится на трёх языках. */
@Entity
@Table(name = "chart_of_accounts")
@Getter
@Setter
public class ChartAccount {

    @Id
    private String code;

    private String name;

    @Getter(AccessLevel.NONE)
    private String nameEn;

    @Getter(AccessLevel.NONE)
    private String nameBe;

    @Enumerated(EnumType.STRING)
    private Activity activity;

    public String getName() {
        return Messages.pick(name, nameEn, nameBe);
    }

    public String getActivityTitle() {
        return activity.getTitle();
    }
}
