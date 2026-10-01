package by.bsuir.bank.deposit.dto;

import by.bsuir.bank.common.ledger.AccountInfo;
import by.bsuir.bank.common.ledger.OperationInfo;

import java.util.List;

/** Договор вместе с состоянием его счетов и проводками из главной книги. */
public record DepositDetails(DepositView contract, List<AccountInfo> accounts, List<OperationInfo> operations) {
}
