package by.bsuir.bank.credit.service;

import by.bsuir.bank.common.api.BankException;
import by.bsuir.bank.common.ledger.LedgerApi;
import by.bsuir.bank.credit.domain.Card;
import by.bsuir.bank.credit.domain.CreditProduct;
import by.bsuir.bank.credit.dto.CreditIssued;
import by.bsuir.bank.credit.dto.CreditRequest;
import by.bsuir.bank.credit.repository.CardRepository;
import by.bsuir.bank.credit.repository.CreditContractRepository;
import by.bsuir.bank.credit.repository.CreditProductRepository;
import by.bsuir.bank.credit.service.DemoProperties.DemoCard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Демонстрационные карты: чтобы банкомат можно было опробовать сразу после запуска, сервис сам
 * заключает несколько кредитных договоров и назначает их картам PIN-коды из настроек.
 * В обычном режиме PIN-код случаен и известен только клиенту; демо-режим отключается
 * параметром bank.demo.enabled=false.
 */
@Component
@EnableConfigurationProperties(DemoProperties.class)
@RequiredArgsConstructor
@Slf4j
public class DemoCards {

    /** Номера демо-договоров начинаются с этой границы и не влияют на нумерацию обычных договоров. */
    public static final String NUMBER_FROM = "К-900000";

    private static final int ATTEMPTS = 60;
    private static final long PAUSE_MS = 3000;

    private final DemoProperties properties;
    private final CreditService credits;
    private final CardService cardService;
    private final CreditContractRepository contracts;
    private final CreditProductRepository products;
    private final CardRepository cards;
    private final LedgerApi ledger;

    /** Сведения о демо-карте для подсказки в банкомате. pin == null, если PIN-код был перевыпущен. */
    public record DemoCardView(String cardNumber, String pin, String holder, String contract, boolean blocked) {
    }

    /**
     * Договоры заключаются после старта в фоновом потоке: сервисам клиентов и счетов нужно время
     * на запуск, поэтому попытки повторяются, пока смежные сервисы не ответят.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seedInBackground() {
        if (!properties.enabled()) {
            return;
        }
        Thread thread = new Thread(() -> {
            for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
                try {
                    seed();
                    log.info("Демо-карты готовы: {}", list().stream().map(DemoCardView::cardNumber).toList());
                    return;
                } catch (RestClientException e) {
                    sleep();
                }
            }
            log.warn("Демо-карты не выпущены: сервисы клиентов и счетов недоступны");
        }, "demo-cards");
        thread.setDaemon(true);
        thread.start();
    }

    /** Заключает недостающие демо-договоры; повторный вызов ничего не меняет. */
    public void seed() {
        LocalDate today = ledger.bankDay().date();
        for (DemoCard demo : properties.cards()) {
            if (contracts.existsByNumber(demo.contract())) {
                continue;
            }
            CreditProduct product = products.findById(demo.productId()).orElseThrow();
            try {
                CreditIssued issued = credits.open(new CreditRequest(demo.contract(), product.getId(),
                        product.getCurrency(), demo.clientId(), demo.amount(), product.getRate(),
                        demo.termMonths(), today, today.plusMonths(demo.termMonths())));
                cardService.assignPin(issued.contract().id(), demo.pin());
            } catch (BankException e) {
                log.warn("Демо-договор {} не заключён: {}", demo.contract(), e.getFields().isEmpty() ? e.getMessage() : e.getFields());
            }
        }
    }

    public List<DemoCardView> list() {
        List<DemoCardView> result = new ArrayList<>();
        for (DemoCard demo : properties.cards()) {
            contracts.findByNumber(demo.contract())
                    .flatMap(contract -> cards.findByContractId(contract.getId()))
                    .ifPresent(card -> result.add(view(card, demo)));
        }
        return result;
    }

    private DemoCardView view(Card card, DemoCard demo) {
        boolean pinValid = cardService.matches(card, demo.pin());
        return new DemoCardView(card.getNumber(), pinValid ? demo.pin() : null, card.getHolder(),
                demo.contract(), card.isBlocked());
    }

    private static void sleep() {
        try {
            Thread.sleep(PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
