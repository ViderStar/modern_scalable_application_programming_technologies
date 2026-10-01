package by.bsuir.bank.common.ledger;

import java.time.LocalDate;
import java.util.List;

/** Пакет операций, проводимый главной книгой атомарно; batchKey — ключ идемпотентности. */
public record Batch(String batchKey, String contractRef, LocalDate bankDate, List<Posting> operations) {

    public static Batch of(String batchKey, String contractRef, LocalDate bankDate, Posting... operations) {
        return new Batch(batchKey, contractRef, bankDate, List.of(operations));
    }
}
