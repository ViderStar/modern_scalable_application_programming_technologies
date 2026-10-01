package by.bsuir.bank.account.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Запись справочника «План счетов» — балансовый счёт. */
@Entity
@Table(name = "chart_of_accounts")
@Getter
@Setter
public class ChartAccount {

    @Id
    private String code;

    private String name;

    @Enumerated(EnumType.STRING)
    private Activity activity;
}
