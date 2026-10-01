package by.bsuir.bank.atm.session;

import by.bsuir.bank.atm.bank.BankReply;
import by.bsuir.bank.common.i18n.Messages;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Печать чеков: текстовые строки по данным ответа банка на языке клиента. */
public final class Receipts {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String RULE = "------------------------------";

    public static List<String> print(String operation, BankReply reply, String card, String terminalId, Clock clock) {
        List<String> lines = new ArrayList<>();
        lines.add("BANKET");
        lines.add(Messages.get("receipt.atm", terminalId));
        lines.add(TIME.format(LocalDateTime.now(clock)));
        lines.add(Messages.get("receipt.card", card.substring(card.length() - 4)));
        lines.add(RULE);
        switch (operation) {
            case "WITHDRAW" -> {
                lines.add(Messages.get("receipt.withdraw"));
                lines.add(Messages.get("receipt.amount", money(reply, "amount")));
                lines.add(Messages.get("receipt.balance", money(reply, "balance")));
            }
            case "BALANCE" -> {
                lines.add(Messages.get("receipt.balanceTitle"));
                lines.add(Messages.get("receipt.account", reply.get("account")));
                lines.add(Messages.get("receipt.available", money(reply, "balance")));
            }
            case "DEPOSIT_BALANCE" -> {
                lines.add(Messages.get("receipt.depositsTitle"));
                lines.addAll(deposits(reply));
            }
            case "PAYMENT" -> {
                lines.add(Messages.get("receipt.paymentTitle"));
                lines.add(Messages.get("receipt.operator", reply.get("operator")));
                lines.add(Messages.get("receipt.phone", reply.get("phone")));
                lines.add(Messages.get("receipt.amount", money(reply, "amount")));
                lines.add(Messages.get("receipt.balance", money(reply, "balance")));
                lines.add(Messages.get("receipt.reference", reply.get("reference")));
            }
            default -> lines.add(operation);
        }
        lines.add(RULE);
        lines.add(Messages.get("receipt.bankDay", DAY.format(LocalDate.parse(String.valueOf(reply.get("bankDate"))))));
        lines.add(Messages.get("receipt.thanks"));
        return lines;
    }

    /** Строки с остатками вкладов — для чека и для экрана. */
    @SuppressWarnings("unchecked")
    public static List<String> deposits(BankReply reply) {
        List<Map<String, Object>> deposits = (List<Map<String, Object>>) reply.get("deposits");
        if (deposits == null || deposits.isEmpty()) {
            return List.of(Messages.get("receipt.noDeposits"));
        }
        return deposits.stream()
                .map(d -> d.get("number") + ": " + format(d.get("amount")) + " " + d.get("currency"))
                .toList();
    }

    public static String money(BankReply reply, String key) {
        return format(reply.get(key)) + " " + reply.get("currency");
    }

    private static String format(Object value) {
        return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private Receipts() {
    }
}
