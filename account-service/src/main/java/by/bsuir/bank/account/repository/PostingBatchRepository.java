package by.bsuir.bank.account.repository;

import by.bsuir.bank.account.domain.PostingBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostingBatchRepository extends JpaRepository<PostingBatch, String> {
}
