package by.bsuir.bank.client.validation;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;

/** Даты передаются между клиентом и сервисом текстом в формате ДД.ММ.ГГГГ. */
public final class Dates {

    /** STRICT отвергает 31.02.2015 и 29.02.2015 — без него парсер молча «исправил» бы дату на 28.02. */
    public static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT);

    public static Optional<LocalDate> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(text.trim(), FORMAT));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    public static String format(LocalDate date) {
        return date == null ? null : FORMAT.format(date);
    }

    private Dates() {
    }
}
