package by.bsuir.bank.credit.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.Setter;

/** Банковская карта, привязанная к кредитному счёту договора. */
@Entity
@Getter
@Setter
public class Card {

    public static final int MAX_PIN_ATTEMPTS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String number;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    private CreditContract contract;

    private String holder;
    private String pinHash;
    private int failedAttempts;
    private boolean blocked;

    /** Неверный PIN-код: после третьей ошибки подряд карта блокируется. Возвращает число оставшихся попыток. */
    public int registerFailedAttempt() {
        failedAttempts++;
        if (failedAttempts >= MAX_PIN_ATTEMPTS) {
            blocked = true;
        }
        return Math.max(0, MAX_PIN_ATTEMPTS - failedAttempts);
    }

    public void resetAttempts() {
        failedAttempts = 0;
        blocked = false;
    }
}
