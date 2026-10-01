package by.bsuir.bank.common;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/** Создание REST-клиента смежного сервиса по интерфейсу с аннотациями @HttpExchange. */
public final class HttpApis {

    public static <T> T proxy(RestClient.Builder builder, String baseUrl, Class<T> api) {
        RestClient client = builder.baseUrl(baseUrl).build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client)).build().createClient(api);
    }

    private HttpApis() {
    }
}
