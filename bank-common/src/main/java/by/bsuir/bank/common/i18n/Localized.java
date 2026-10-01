package by.bsuir.bank.common.i18n;

import java.util.Arrays;

/**
 * Тексты, которые система сама записывает в базу (названия проводок, наименования счетов банка),
 * хранятся не на конкретном языке, а кодом вида «@ключ|аргумент|аргумент» и превращаются
 * в текст при чтении — на языке того, кто читает.
 */
public final class Localized {

    private static final String MARK = "@";
    private static final String SEPARATOR = "|";

    public static String code(String key, Object... args) {
        StringBuilder code = new StringBuilder(MARK).append(key);
        for (Object arg : args) {
            code.append(SEPARATOR).append(arg);
        }
        return code.toString();
    }

    /** Обычный текст (например, ФИО клиента в названии счёта) возвращается как есть. */
    public static String render(String text) {
        if (text == null || !text.startsWith(MARK)) {
            return text;
        }
        String[] parts = text.substring(MARK.length()).split("\\" + SEPARATOR);
        return Messages.get(parts[0], (Object[]) Arrays.copyOfRange(parts, 1, parts.length));
    }

    private Localized() {
    }
}
