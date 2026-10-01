package by.bsuir.bank.account.dto;

import by.bsuir.bank.account.domain.Account;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountView(
        String number,
        String chartCode,
        String chartName,
        String activity,
        String activityTitle,
        String currency,
        String name,
        Integer clientId,
        String contractRef,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal balance,
        LocalDate openedOn
) {

    public static AccountView from(Account a) {
        return new AccountView(a.getNumber(), a.getChart().getCode(), a.getChart().getName(),
                a.getChart().getActivity().name(), a.getChart().getActivity().getTitle(), a.getCurrency(),
                a.getName(), a.getOwnerCode() == 0 ? null : a.getOwnerCode(), a.getContractRef(),
                a.getDebit(), a.getCredit(), a.getBalance(), a.getOpenedOn());
    }
}
