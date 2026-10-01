package by.bsuir.bank.atm.session;

import by.bsuir.bank.atm.bank.BankReply;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Печать чеков: текстовые строки по данным ответа банка. */
public final class Receipts {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String RULE = "------------------------------";

    public static List<String> print(String operation, BankReply reply, String card, String terminalId, Clock clock) {
        List<String> lines = new ArrayList<>();
        lines.add("БАНК «РЕШЕНИЕ»");
        lines.add("Банкомат " + terminalId);
        lines.add(TIME.format(LocalDateTime.now(clock)));
        lines.add("Карта **** **** **** " + card.substring(card.length() - 4));
        lines.add(RULE);
        switch (operation) {
            case "WITHDRAW" -> {
                lines.add("ВЫДАЧА НАЛИЧНЫХ");
                lines.add("Сумма: " + money(reply, "amount"));
                lines.add("Остаток: " + money(reply, "balance"));
            }
            case "BALANCE" -> {
                lines.add("ОСТАТОК КРЕДИТНОГО СЧЁТА");
                lines.add("Счёт: " + reply.get("account"));
                lines.add("Доступно: " + money(reply, "balance"));
            }
            case "DEPOSIT_BALANCE" -> {
                lines.add("ДЕПОЗИТНЫЕ СЧЕТА");
                lines.addAll(deposits(reply));
            }
            case "PAYMENT" -> {
                lines.add("ОПЛАТА УСЛУГ СВЯЗИ");
                lines.add("Оператор: " + reply.get("operator"));
                lines.add("Телефон: " + reply.get("phone"));
                lines.add("Сумма: " + money(reply, "amount"));
                lines.add("Остаток: " + money(reply, "balance"));
                lines.add("Операция № " + reply.get("reference"));
            }
            default -> lines.add(operation);
        }
        lines.add(RULE);
        lines.add("Банковский день: " + DAY.format(LocalDate.parse(String.valueOf(reply.get("bankDate")))));
        lines.add("СПАСИБО!");
        return lines;
    }

    /** Строки с остатками вкладов — для чека и для экрана. */
    @SuppressWarnings("unchecked")
    public static List<String> deposits(BankReply reply) {
        List<Map<String, Object>> deposits = (List<Map<String, Object>>) reply.get("deposits");
        if (deposits == null || deposits.isEmpty()) {
            return List.of("Действующих вкладов нет");
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
