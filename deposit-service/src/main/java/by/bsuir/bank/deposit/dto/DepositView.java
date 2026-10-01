package by.bsuir.bank.deposit.dto;

import by.bsuir.bank.deposit.domain.DepositContract;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DepositView(
        Long id,
        String number,
        Long productId,
        String productName,
        String kind,
        String kindTitle,
        Long clientId,
        String clientName,
        String currency,
        BigDecimal amount,
        BigDecimal rate,
        int termMonths,
        LocalDate startDate,
        LocalDate endDate,
        String mainAccount,
        String interestAccount,
        String status,
        BigDecimal accrued,
        BigDecimal paid,
        LocalDate closedOn
) {

    public static DepositView from(DepositContract c) {
        return new DepositView(c.getId(), c.getNumber(), c.getProduct().getId(), c.getProduct().getName(),
                c.getProduct().getKind().name(), c.getProduct().getKind().getTitle(), c.getClientId(),
                c.getClientName(), c.getCurrency(), c.getAmount(), c.getRate(), c.getTermMonths(),
                c.getStartDate(), c.getEndDate(), c.getMainAccount(), c.getInterestAccount(),
                c.getStatus().name(), c.getAccrued(), c.getPaid(), c.getClosedOn());
    }
}
