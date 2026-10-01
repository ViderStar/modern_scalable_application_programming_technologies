package by.bsuir.bank.account.domain;

/**
 * Номер счёта из 13 цифр: 1–4 — балансовый счёт из плана счетов, 5–9 — код клиента,
 * 10–12 — порядковый номер счёта этого клиента, 13 — контрольный ключ.
 */
public final class AccountNumbers {

    private static final int[] WEIGHTS = {7, 1, 3};

    public static String generate(String chartCode, int ownerCode, int ownerSeq) {
        if (!chartCode.matches("\\d{4}") || ownerCode < 0 || ownerCode > 99_999 || ownerSeq < 1 || ownerSeq > 999) {
            throw new IllegalArgumentException("Номер счёта не помещается в 13 разрядов");
        }
        String body = chartCode + String.format("%05d%03d", ownerCode, ownerSeq);
        return body + checkDigit(body);
    }

    /** Контрольный ключ: взвешенная сумма первых 12 цифр (веса 7, 1, 3), умноженная на 3, по модулю 10. */
    static int checkDigit(String body) {
        int sum = 0;
        for (int i = 0; i < body.length(); i++) {
            sum += (body.charAt(i) - '0') * WEIGHTS[i % WEIGHTS.length];
        }
        return sum * 3 % 10;
    }

    public static boolean isValid(String number) {
        return number != null && number.matches("\\d{13}")
                && checkDigit(number.substring(0, 12)) == number.charAt(12) - '0';
    }

    private AccountNumbers() {
    }
}
