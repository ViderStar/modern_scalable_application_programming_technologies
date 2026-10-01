package by.bsuir.bank.account.service;

import by.bsuir.bank.account.BankProperties;
import by.bsuir.bank.account.domain.Side;
import by.bsuir.bank.account.dto.AccountView;
import by.bsuir.bank.account.dto.BatchRequest;
import by.bsuir.bank.account.dto.EntryRequest;
import by.bsuir.bank.account.dto.OpenAccountRequest;
import by.bsuir.bank.account.dto.OperationRequest;
import by.bsuir.bank.account.repository.CurrencyRepository;
import by.bsuir.bank.common.i18n.Localized;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** При старте открывает собственные счета банка в каждой валюте: кассу и фонд развития со стартовым капиталом. */
@Component
@RequiredArgsConstructor
public class LedgerInitializer implements ApplicationRunner {

    public static final String CASH = "1010";
    public static final String FUND = "7327";

    private final LedgerService ledger;
    private final CurrencyRepository currencies;
    private final BankProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        currencies.findAll().forEach(currency -> {
            String code = currency.getCode();
            ledger.open(new OpenAccountRequest(CASH, code, null, Localized.code("account.cash", code), null));
            AccountView fund = ledger.open(new OpenAccountRequest(FUND, code, null, Localized.code("account.fund", code), null));

            BigDecimal capital = properties.fund().capital().get(code);
            if (capital != null && capital.signum() > 0) {
                ledger.post(new BatchRequest("FUND-CAPITAL-" + code, null, null, List.of(
                        new OperationRequest(Localized.code("posting.fund.capital"),
                                List.of(new EntryRequest(fund.number(), Side.CREDIT, capital))))));
            }
        });
    }
}
