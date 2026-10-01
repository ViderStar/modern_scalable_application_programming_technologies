package by.bsuir.bank.common;

import by.bsuir.bank.common.i18n.Messages;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.util.List;

/** Создание REST-клиента смежного сервиса по интерфейсу с аннотациями @HttpExchange. */
public final class HttpApis {

    public static <T> T proxy(RestClient.Builder builder, String baseUrl, Class<T> api) {
        RestClient client = withLanguage(builder).baseUrl(baseUrl).build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client)).build().createClient(api);
    }

    /** Язык пользователя передаётся смежному сервису, чтобы его сообщения пришли на том же языке. */
    public static RestClient.Builder withLanguage(RestClient.Builder builder) {
        return builder.requestInterceptor((request, body, execution) -> {
            request.getHeaders().setAcceptLanguageAsLocales(List.of(Messages.locale()));
            return execution.execute(request, body);
        });
    }

    private HttpApis() {
    }
}
