package by.bsuir.bank.deposit.service;

import by.bsuir.bank.deposit.domain.DepositStatus;
import by.bsuir.bank.deposit.repository.DepositContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Участие сервиса депозитов в процедуре «Закрытие банковского дня»: каждый договор — в своей транзакции. */
@Component
@RequiredArgsConstructor
public class DayCloseProcessor {

    private final DepositContractRepository contracts;
    private final DepositService deposits;

    public List<String> process(LocalDate date) {
        List<String> events = new ArrayList<>();
        for (Long id : contracts.findIdsByStatus(DepositStatus.ACTIVE)) {
            events.addAll(deposits.processDay(id, date));
        }
        return events;
    }
}
