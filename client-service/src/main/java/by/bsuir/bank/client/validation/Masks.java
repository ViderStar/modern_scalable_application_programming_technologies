package by.bsuir.bank.client.validation;

/** Маски полей клиента. Те же выражения продублированы в web-клиенте (static/app.js). */
public final class Masks {

    /** ФИО: только буквы, составные части через дефис, пробел или апостроф. */
    public static final String NAME = "^[А-Яа-яЁёA-Za-z]+(?:[-' ][А-Яа-яЁёA-Za-z]+)*$";

    /** Серия паспорта: две заглавные латинские буквы (MP, MC, AB, BM, KH, HB, KB, PP...). */
    public static final String PASSPORT_SERIES = "^[A-Z]{2}$";

    /** Номер паспорта: семь цифр. */
    public static final String PASSPORT_NUMBER = "^\\d{7}$";

    /** Идентификационный номер: 7 цифр, буква, 3 цифры, 2 буквы, цифра — 3140301A001PB5. */
    public static final String IDENTIFICATION_NUMBER = "^\\d{7}[A-Z]\\d{3}[A-Z]{2}\\d$";

    /** Домашний телефон: 293-88-44. Пустая строка допустима — поле необязательное. */
    public static final String HOME_PHONE = "^$|^\\d{3}-\\d{2}-\\d{2}$";

    /** Мобильный телефон: +375 (29) 314-15-92, коды операторов 25, 29, 33, 44. */
    public static final String MOBILE_PHONE = "^$|^\\+375 \\((25|29|33|44)\\) \\d{3}-\\d{2}-\\d{2}$";

    public static final String EMAIL = "^$|^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    private Masks() {
    }
}
