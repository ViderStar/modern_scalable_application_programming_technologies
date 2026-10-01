package by.bsuir.bank.common.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Язык запроса определяется заголовком Accept-Language: web-клиент подставляет в него язык,
 * выбранный пользователем, а сервисы передают его дальше при обращении друг к другу.
 */
@Configuration
public class I18nConfig {

    /** Тот же источник текстов используется Bean Validation для сообщений вида {ключ}. */
    @Bean
    public MessageSource messageSource() {
        return Messages.source();
    }

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(Messages.SUPPORTED);
        resolver.setDefaultLocale(Messages.DEFAULT);
        return resolver;
    }
}
