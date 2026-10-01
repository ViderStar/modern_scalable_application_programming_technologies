package by.bsuir.bank.atm.session;

import by.bsuir.bank.atm.AtmProperties;
import by.bsuir.bank.atm.bank.BankGateway;
import by.bsuir.bank.common.api.BankException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Сеансы работы с банкоматом: каждый открытый web-интерфейс — отдельный сеанс со своим автоматом. */
@Service
@RequiredArgsConstructor
public class AtmService {

    private static final Duration IDLE_LIMIT = Duration.ofMinutes(15);

    private final BankGateway bank;
    private final AtmProperties properties;
    private final Clock clock;
    private final Map<String, AtmSession> sessions = new ConcurrentHashMap<>();

    public record SessionView(String id, Screen screen) {
    }

    public SessionView open() {
        Instant deadline = clock.instant().minus(IDLE_LIMIT);
        sessions.values().removeIf(session -> session.touched().isBefore(deadline));

        String id = UUID.randomUUID().toString();
        AtmSession session = new AtmSession(bank, properties.terminalId(), clock);
        sessions.put(id, session);
        return new SessionView(id, session.screen());
    }

    public AtmSession get(String id) {
        AtmSession session = sessions.get(id);
        if (session == null) {
            throw BankException.notFound("Сеанс банкомата не найден или истёк");
        }
        return session;
    }
}
