package by.bsuir.bank.credit.dto;

import by.bsuir.bank.credit.domain.Card;
import by.bsuir.bank.credit.domain.CreditContract;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreditView(
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
        BigDecimal principalPaid,
        BigDecimal interestPaid,
        BigDecimal debt,
        LocalDate closedOn,
        String cardNumber,
        boolean cardBlocked
) {

    public static CreditView from(CreditContract c, Card card) {
        return new CreditView(c.getId(), c.getNumber(), c.getProduct().getId(), c.getProduct().getName(),
                c.getProduct().getKind().name(), c.getProduct().getKind().getTitle(), c.getClientId(),
                c.getClientName(), c.getCurrency(), c.getAmount(), c.getRate(), c.getTermMonths(),
                c.getStartDate(), c.getEndDate(), c.getMainAccount(), c.getInterestAccount(), c.getStatus().name(),
                c.getPrincipalPaid(), c.getInterestPaid(), c.getAmount().subtract(c.getPrincipalPaid()),
                c.getClosedOn(), card == null ? null : card.getNumber(), card != null && card.isBlocked());
    }
}
