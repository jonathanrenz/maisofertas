package com.maisofertas.deals;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealRepository extends JpaRepository<Deal, UUID> {

    boolean existsByUrlAndCreatedAtAfter(String url, Instant since);

    List<Deal> findByStatus(DealStatus status);
}
