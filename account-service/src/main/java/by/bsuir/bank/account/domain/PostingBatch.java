package by.bsuir.bank.account.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Ключ идемпотентности пакета проводок. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class PostingBatch {

    @Id
    private String batchKey;

    private LocalDateTime createdAt;

    public PostingBatch(String batchKey) {
        this.batchKey = batchKey;
        this.createdAt = LocalDateTime.now();
    }
}
