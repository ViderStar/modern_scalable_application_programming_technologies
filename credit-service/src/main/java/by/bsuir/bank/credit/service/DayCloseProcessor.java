package by.bsuir.bank.credit.service;

import by.bsuir.bank.credit.domain.ContractStatus;
import by.bsuir.bank.credit.repository.CreditContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Участие сервиса кредитов в процедуре «Закрытие банковского дня»: каждый договор — в своей транзакции. */
@Component
@RequiredArgsConstructor
public class DayCloseProcessor {

    private final CreditContractRepository contracts;
    private final CreditService credits;

    public List<String> process(LocalDate date) {
        List<String> events = new ArrayList<>();
        for (Long id : contracts.findIdsByStatus(ContractStatus.ACTIVE)) {
            events.addAll(credits.processDay(id, date));
        }
        return events;
    }
}
