package by.bsuir.bank.common.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Тексты на языке пользователя: русском, английском или белорусском.
 * Словари — messages*.properties сервиса и common-messages*.properties общей библиотеки;
 * файл без суффикса содержит русские тексты и служит языком по умолчанию.
 */
public final class Messages {

    public static final Locale RU = Locale.forLanguageTag("ru");
    public static final Locale EN = Locale.forLanguageTag("en");
    public static final Locale BE = Locale.forLanguageTag("be");
    public static final Locale DEFAULT = RU;
    public static final List<Locale> SUPPORTED = List.of(RU, EN, BE);

    private static final ResourceBundleMessageSource SOURCE = new ResourceBundleMessageSource();

    static {
        SOURCE.setBasenames("messages", "common-messages");
        SOURCE.setDefaultEncoding("UTF-8");
        SOURCE.setFallbackToSystemLocale(false);
        // вне HTTP-запроса (старт приложения, фоновые задачи) используется русский язык
        LocaleContextHolder.setDefaultLocale(DEFAULT);
    }

    public static MessageSource source() {
        return SOURCE;
    }

    /** Язык текущего запроса; неподдерживаемый язык заменяется русским. */
    public static Locale locale() {
        String language = LocaleContextHolder.getLocale().getLanguage();
        return SUPPORTED.stream().filter(locale -> locale.getLanguage().equals(language)).findFirst().orElse(DEFAULT);
    }

    /** Текст по ключу. Аргументы подставляются как строки, чтобы числа и суммы не переформатировались. */
    public static String get(String code, Object... args) {
        Object[] strings = Arrays.stream(args).map(String::valueOf).toArray();
        try {
            return SOURCE.getMessage(code, strings, locale());
        } catch (NoSuchMessageException e) {
            return code;
        }
    }

    /** Выбор из трёх вариантов значения справочника; непереведённое значение заменяется русским. */
    public static String pick(String ru, String en, String be) {
        String language = locale().getLanguage();
        String value = "en".equals(language) ? en : "be".equals(language) ? be : ru;
        return value == null || value.isBlank() ? ru : value;
    }

    private Messages() {
    }
}
