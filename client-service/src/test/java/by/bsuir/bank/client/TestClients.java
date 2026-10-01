package by.bsuir.bank.client;

import by.bsuir.bank.client.dto.ClientRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Тестовые данные: корректно заполненная форма клиента, которую тесты портят по одному полю. */
public final class TestClients {

    public static final List<String> REQUIRED_FIELDS = List.of(
            "lastName", "firstName", "middleName", "birthDate", "sex", "passportSeries", "passportNumber",
            "issuedBy", "issueDate", "identificationNumber", "birthPlace", "residenceCityId", "residenceAddress",
            "registrationCityId", "maritalStatusId", "citizenshipId", "disabilityId", "pensioner");

    public static final List<String> OPTIONAL_FIELDS = List.of("homePhone", "mobilePhone", "email", "monthlyIncome");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Новый клиент, которого нет в начальном заполнении базы. */
    public static Map<String, Object> valid() {
        Map<String, Object> client = new LinkedHashMap<>();
        client.put("lastName", "Иванов");
        client.put("firstName", "Иван");
        client.put("middleName", "Иванович");
        client.put("birthDate", "17.05.1990");
        client.put("sex", "M");
        client.put("passportSeries", "MP");
        client.put("passportNumber", "7654321");
        client.put("issuedBy", "Фрунзенское РУВД г. Минска");
        client.put("issueDate", "20.06.2018");
        client.put("identificationNumber", "3170590A077PB4");
        client.put("birthPlace", "г. Минск");
        client.put("residenceCityId", 1);
        client.put("residenceAddress", "ул. Притыцкого, д. 29, кв. 3");
        client.put("homePhone", "201-45-67");
        client.put("mobilePhone", "+375 (29) 765-43-21");
        client.put("email", "ivanov@example.by");
        client.put("registrationCityId", 1);
        client.put("maritalStatusId", 1);
        client.put("citizenshipId", 1);
        client.put("disabilityId", 1);
        client.put("pensioner", false);
        client.put("monthlyIncome", 2500.50);
        return client;
    }

    public static Map<String, Object> with(String field, Object value) {
        Map<String, Object> client = valid();
        client.put(field, value);
        return client;
    }

    public static Map<String, Object> onlyRequired() {
        Map<String, Object> client = valid();
        OPTIONAL_FIELDS.forEach(client::remove);
        return client;
    }

    public static ClientRequest request(Map<String, Object> fields) {
        return MAPPER.convertValue(fields, ClientRequest.class);
    }

    public static String json(Map<String, Object> fields) throws Exception {
        return MAPPER.writeValueAsString(fields);
    }

    private TestClients() {
    }
}
