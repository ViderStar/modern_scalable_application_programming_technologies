package by.bsuir.bank.credit.service;

import by.bsuir.bank.common.api.BankException;
import by.bsuir.bank.credit.domain.Card;
import by.bsuir.bank.credit.domain.CardNumbers;
import by.bsuir.bank.credit.domain.CreditContract;
import by.bsuir.bank.credit.dto.PinEnvelope;
import by.bsuir.bank.credit.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

/** Выпуск карт к кредитным счетам и проверка PIN-кодов. */
@Service
@RequiredArgsConstructor
public class CardService {

    private final CardRepository cards;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    /** Выпускает карту к договору. PIN-код генерируется случайно и возвращается единственный раз. */
    @Transactional
    public PinEnvelope issue(CreditContract contract) {
        Card card = new Card();
        card.setNumber(CardNumbers.generate(contract.getId()));
        card.setContract(contract);
        card.setHolder(contract.getClientName());
        return assignPin(card);
    }

    /** Перевыпуск PIN-кода: заодно снимает блокировку карты после трёх неверных попыток. */
    @Transactional
    public PinEnvelope reissuePin(Long contractId) {
        Card card = cards.findByContractId(contractId)
                .orElseThrow(() -> BankException.notFound("К договору не выпущена карта"));
        card.resetAttempts();
        return assignPin(card);
    }

    public boolean matches(Card card, String pin) {
        return pin != null && encoder.matches(pin, card.getPinHash());
    }

    private PinEnvelope assignPin(Card card) {
        String pin = String.format("%04d", random.nextInt(10_000));
        card.setPinHash(encoder.encode(pin));
        cards.save(card);
        return new PinEnvelope(card.getNumber(), pin);
    }
}
