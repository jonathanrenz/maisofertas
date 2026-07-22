package com.maisofertas.deals;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DealService {

    private final DealRepository dealRepository;
    private final int dedupWindowDays;

    public DealService(
            DealRepository dealRepository,
            @Value("${app.deals.dedup-window-days:7}") int dedupWindowDays) {
        this.dealRepository = dealRepository;
        this.dedupWindowDays = dedupWindowDays;
    }

    public boolean isDuplicate(String url) {
        Instant since = Instant.now().minus(dedupWindowDays, ChronoUnit.DAYS);
        return dealRepository.existsByUrlAndCreatedAtAfter(url, since);
    }

    public Deal createDeal(CreateDealRequest request, DealSource source) {
        if (isDuplicate(request.url())) {
            throw new DuplicateDealException(request.url());
        }
        Deal deal = Deal.builder()
                .id(UUID.randomUUID())
                .store(request.store() != null ? request.store() : Store.AMAZON)
                .title(request.title())
                .url(request.url())
                .imageUrl(request.imageUrl())
                .price(request.price())
                .originalPrice(request.originalPrice())
                .source(source)
                .status(DealStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        return dealRepository.save(deal);
    }

    public List<Deal> findPending() {
        return dealRepository.findByStatus(DealStatus.PENDING);
    }

    @Transactional
    public void markTelegramPosted(UUID dealId) {
        dealRepository.findById(dealId).ifPresent(deal -> {
            deal.setPostedTelegramAt(Instant.now());
            promoteIfFullyPosted(deal);
            dealRepository.save(deal);
        });
    }

    @Transactional
    public void markWhatsappPosted(UUID dealId) {
        dealRepository.findById(dealId).ifPresent(deal -> {
            deal.setPostedWhatsappAt(Instant.now());
            promoteIfFullyPosted(deal);
            dealRepository.save(deal);
        });
    }

    private void promoteIfFullyPosted(Deal deal) {
        if (deal.getPostedTelegramAt() != null && deal.getPostedWhatsappAt() != null) {
            deal.setStatus(DealStatus.POSTED);
        }
    }
}
