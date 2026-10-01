package by.bsuir.bank.common.client;

/** Сведения о клиенте, которые нужны договорам: остальные поля ответа сервиса «Клиенты» игнорируются. */
public record ClientInfo(Long id, String fullName, String passportSeries, String passportNumber,
                         String identificationNumber) {
}
