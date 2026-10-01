package by.bsuir.bank.common.i18n;

import by.bsuir.bank.common.HttpApis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Модульные тесты локализации: выбор языка, подстановка аргументов, коды хранимых текстов, передача языка. */
class MessagesTest {

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
            "ru, Обязательное поле, 'Касса банка, BYN'",
            "en, Required field, 'Bank cash desk, BYN'",
            "be, Абавязковае поле, 'Каса банка, BYN'",
            "en-GB, Required field, 'Bank cash desk, BYN'",
            "de, Обязательное поле, 'Касса банка, BYN'",
    })
    @DisplayName("Текст выбирается по языку запроса, неподдерживаемый язык заменяется русским")
    void messageByLocale(String language, String required, String cash) {
        LocaleContextHolder.setLocale(Locale.forLanguageTag(language));

        assertThat(Messages.get("validation.required")).isEqualTo(required);
        assertThat(Messages.get("account.cash", "BYN")).isEqualTo(cash);
    }

    @Test
    @DisplayName("Вне запроса используется русский язык, неизвестный ключ возвращается как есть")
    void defaults() {
        assertThat(Messages.locale()).isEqualTo(Messages.RU);
        assertThat(Messages.get("validation.required")).isEqualTo("Обязательное поле");
        assertThat(Messages.get("no.such.key")).isEqualTo("no.such.key");
    }

    @Test
    @DisplayName("Числа подставляются без переформатирования")
    void argumentsAreNotReformatted() {
        assertThat(Messages.get("posting.atm.payment", "МТС", "0297654321"))
                .isEqualTo("Оплата услуг связи МТС, номер 0297654321");
        assertThat(Messages.get("account.cash", 1234567.5)).isEqualTo("Касса банка, 1234567.5");
    }

    @Test
    @DisplayName("Значение справочника выбирается из трёх столбцов; пустой перевод заменяется русским")
    void pick() {
        LocaleContextHolder.setLocale(Messages.BE);
        assertThat(Messages.pick("Минск", "Minsk", "Мінск")).isEqualTo("Мінск");
        assertThat(Messages.pick("Минск", "Minsk", null)).isEqualTo("Минск");

        LocaleContextHolder.setLocale(Messages.EN);
        assertThat(Messages.pick("Минск", "Minsk", "Мінск")).isEqualTo("Minsk");
    }

    @Test
    @DisplayName("Хранимый код раскрывается на языке читающего, обычный текст не меняется")
    void localizedCodes() {
        String posting = Localized.code("posting.deposit.accrual");
        String payment = Localized.code("posting.atm.payment", "A1", "0291112233");
        assertThat(posting).isEqualTo("@posting.deposit.accrual");
        assertThat(payment).isEqualTo("@posting.atm.payment|A1|0291112233");

        assertThat(Localized.render(posting)).isEqualTo("Начисление процентов по депозиту");
        assertThat(Localized.render("Иванов Иван Иванович")).isEqualTo("Иванов Иван Иванович");
        assertThat(Localized.render(null)).isNull();

        LocaleContextHolder.setLocale(Messages.EN);
        assertThat(Localized.render(posting)).isEqualTo("Deposit interest accrued");
        assertThat(Localized.render(payment)).isEqualTo("Mobile service payment A1, number 0291112233");

        LocaleContextHolder.setLocale(Messages.BE);
        assertThat(Localized.render(posting)).isEqualTo("Налічэнне працэнтаў па дэпазіце");
    }

    @Test
    @DisplayName("Язык пользователя передаётся смежному сервису в заголовке Accept-Language")
    void languageIsPropagated() {
        RestClient.Builder builder = HttpApis.withLanguage(RestClient.builder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ledger/api/bank-day"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT_LANGUAGE, "be"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        LocaleContextHolder.setLocale(Messages.BE);

        builder.build().get().uri("http://ledger/api/bank-day").retrieve().toBodilessEntity();

        server.verify();
    }
}
