package by.bsuir.bank.credit.domain;

/** Номер карты: 16 цифр, префикс банка, порядковый номер и контрольная цифра по алгоритму Луна. */
public final class CardNumbers {

    private static final String BIN = "911238";

    public static String generate(long sequence) {
        String body = BIN + String.format("%09d", sequence);
        return body + luhnDigit(body);
    }

    public static boolean isValid(String number) {
        return number != null && number.matches("\\d{16}")
                && luhnDigit(number.substring(0, 15)) == number.charAt(15) - '0';
    }

    /** Цифры на чётных с конца позициях удваиваются; контрольная цифра дополняет сумму до кратной 10. */
    static int luhnDigit(String body) {
        int sum = 0;
        boolean doubled = true;
        for (int i = body.length() - 1; i >= 0; i--) {
            int digit = body.charAt(i) - '0';
            if (doubled) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubled = !doubled;
        }
        return (10 - sum % 10) % 10;
    }

    private CardNumbers() {
    }
}
