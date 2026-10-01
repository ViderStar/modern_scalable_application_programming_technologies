package by.bsuir.bank.account.service;

import by.bsuir.bank.account.BankProperties;
import by.bsuir.bank.account.domain.BankDay;
import by.bsuir.bank.account.dto.DayCloseResult;
import by.bsuir.bank.account.repository.BankDayRepository;
import by.bsuir.bank.common.api.BankException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Банковский день и процедура его закрытия. Сам сервис счетов не знает правил депозитов и кредитов:
 * он объявляет сервисам-участникам дату нового дня, а те начисляют проценты и проводят платежи
 * через REST этого же сервиса. Дата сдвигается только после успешной обработки всеми участниками.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankDayService {

    private static final int MAX_DAYS = 3660;
    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final BankDayRepository days;
    private final BankProperties properties;
    private final DayCloseNotifier notifier;
    private final ReentrantLock closing = new ReentrantLock();

    public LocalDate current() {
        return days.findById(BankDay.ID).orElseThrow().getBankDate();
    }

    public DayCloseResult close(int count) {
        if (count < 1 || count > MAX_DAYS) {
            throw BankException.invalid("Количество дней должно быть от 1 до " + MAX_DAYS);
        }
        if (!closing.tryLock()) {
            throw new BankException(HttpStatus.CONFLICT, "DAY_CLOSE_IN_PROGRESS", "Закрытие дня уже выполняется");
        }
        try {
            List<String> events = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                LocalDate next = current().plusDays(1);
                for (BankProperties.Participant participant : properties.dayClose().participants()) {
                    events.addAll(notify(participant, next));
                }
                BankDay day = days.findById(BankDay.ID).orElseThrow();
                day.setBankDate(next);
                days.saveAndFlush(day);
            }
            return new DayCloseResult(current(), events);
        } finally {
            closing.unlock();
        }
    }

    /**
     * Недоступный участник пропускается: его обработка устойчива к пропуску дней и наверстает их
     * при следующем закрытии. Ошибка обработки, напротив, останавливает закрытие — дата не меняется.
     */
    private List<String> notify(BankProperties.Participant participant, LocalDate date) {
        try {
            return notifier.dayOpened(participant.url(), date);
        } catch (ResourceAccessException e) {
            log.warn("Сервис «{}» недоступен, день {} обработан без него", participant.name(), date);
            return List.of(RU_DATE.format(date) + ": сервис «" + participant.name() + "» недоступен, обработка отложена");
        } catch (RestClientException e) {
            throw new BankException(HttpStatus.BAD_GATEWAY, "DAY_CLOSE_FAILED",
                    "Сервис «" + participant.name() + "» не обработал день " + RU_DATE.format(date) + ", день не закрыт");
        }
    }
}
