package by.bsuir.bank.common.api;

import java.util.Map;

/** Единый формат ошибки всех сервисов: машинный код, сообщение и сообщения по полям запроса. */
public record ApiError(String code, String message, Map<String, String> fields) {
}
