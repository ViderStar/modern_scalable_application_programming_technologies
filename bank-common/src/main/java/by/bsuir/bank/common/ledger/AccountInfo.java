package by.bsuir.bank.common.ledger;

import java.math.BigDecimal;

public record AccountInfo(String number, String chartCode, String chartName, String activity, String activityTitle,
                          String currency, String name, Integer clientId, String contractRef,
                          BigDecimal debit, BigDecimal credit, BigDecimal balance) {
}
