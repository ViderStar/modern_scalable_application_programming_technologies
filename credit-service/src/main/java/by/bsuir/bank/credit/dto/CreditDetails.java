package by.bsuir.bank.credit.dto;

import by.bsuir.bank.common.ledger.AccountInfo;
import by.bsuir.bank.common.ledger.OperationInfo;

import java.util.List;

/** Договор с графиком платежей, состоянием счетов и проводками из главной книги. */
public record CreditDetails(CreditView contract, ScheduleView schedule, List<AccountInfo> accounts,
                            List<OperationInfo> operations) {
}
