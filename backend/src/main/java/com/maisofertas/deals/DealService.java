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
    private final String amazonAssociateTag;

    public DealService(
            DealRepository dealRepository,
            @Value("${app.deals.dedup-window-days:7}") int dedupWindowDays,
            @Value("${app.amazon.associate-tag:}") String amazonAssociateTag) {
        this.dealRepository = dealRepository;
        this.dedupWindowDays = dedupWindowDays;
        this.amazonAssociateTag = amazonAssociateTag;
    }

    public boolean isDuplicate(String url) {
        Instant since = Instant.now().minus(dedupWindowDays, ChronoUnit.DAYS);
        return dealRepository.existsByUrlAndCreatedAtAfter(url, since);
    }

    public Deal createDeal(CreateDealRequest request, DealSource source) {
        Store store = request.store() != null ? request.store() : Store.AMAZON;
        String url = applyAffiliateTag(request.url(), store);
        if (isDuplicate(url)) {
            throw new DuplicateDealException(url);
        }
        Deal deal = Deal.builder()
                .id(UUID.randomUUID())
                .store(store)
                .title(request.title())
                .url(url)
                .imageUrl(request.imageUrl())
                .price(request.price())
                .originalPrice(request.originalPrice())
                .source(source)
                .status(DealStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        return dealRepository.save(deal);
    }

    /**
     * Garante o {@code tag=} de afiliado da Amazon no link, sem depender de quem
     * cria o deal lembrar de colar manualmente. Não mexe em URLs que já têm um
     * {@code tag=} explícito (ex: link gerado via SiteStripe com outra tag).
     */
    private String applyAffiliateTag(String url, Store store) {
        if (store != Store.AMAZON || amazonAssociateTag == null || amazonAssociateTag.isBlank()) {
            return url;
        }
        if (url.contains("tag=")) {
            return url;
        }
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "tag=" + amazonAssociateTag;
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
