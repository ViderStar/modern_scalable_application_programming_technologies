package by.bsuir.bank.client.dto;

import java.util.Map;

/** Единый формат ошибки: общее сообщение и сообщения по полям формы. */
public record ApiError(String message, Map<String, String> fields) {
}
