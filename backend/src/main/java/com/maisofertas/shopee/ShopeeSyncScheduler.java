package com.maisofertas.shopee;

import com.maisofertas.deals.CreateDealRequest;
import com.maisofertas.deals.DealService;
import com.maisofertas.deals.DealSource;
import com.maisofertas.deals.DuplicateDealException;
import com.maisofertas.deals.Store;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Busca automática de ofertas Shopee via Affiliate Open API, no mesmo molde do
 * {@link com.maisofertas.canopy.CanopySyncScheduler} pra Amazon. Desligado por padrão
 * ({@code app.shopee.sync.enabled=false}) até {@code SHOPEE_APP_ID}/{@code SHOPEE_SECRET}
 * estarem configurados e o mapeamento de campos ter sido validado contra a API real (ver aviso
 * em {@link ShopeeClient}).
 */
@Component
@ConditionalOnProperty(name = "app.shopee.sync.enabled", havingValue = "true")
public class ShopeeSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(ShopeeSyncScheduler.class);

    private final ShopeeClient shopeeClient;
    private final DealService dealService;
    private final int pagesPerRun;
    private final int limitPerPage;
    private final int minDiscountPercent;

    public ShopeeSyncScheduler(
            ShopeeClient shopeeClient,
            DealService dealService,
            @Value("${app.shopee.sync.pages-per-run:1}") int pagesPerRun,
            @Value("${app.shopee.sync.limit-per-page:20}") int limitPerPage,
            @Value("${app.shopee.min-discount-percent:20}") int minDiscountPercent) {
        this.shopeeClient = shopeeClient;
        this.dealService = dealService;
        this.pagesPerRun = pagesPerRun;
        this.limitPerPage = limitPerPage;
        this.minDiscountPercent = minDiscountPercent;
    }

    @Scheduled(fixedDelayString = "${app.shopee.sync.interval-ms:28800000}")
    public void syncDeals() {
        int created = 0;
        int duplicates = 0;
        int ignored = 0;

        for (int page = 1; page <= pagesPerRun; page++) {
            List<ShopeeDeal> deals;
            try {
                deals = shopeeClient.fetchDeals(page, limitPerPage);
            } catch (Exception e) {
                log.warn("Falha ao buscar página {} de ofertas na Shopee Affiliate API, parando essa rodada", page,
                        e);
                break;
            }

            for (ShopeeDeal deal : deals) {
                if (!isEligible(deal)) {
                    ignored++;
                    continue;
                }
                try {
                    dealService.createDeal(toRequest(deal), DealSource.SHOPEE);
                    created++;
                } catch (DuplicateDealException e) {
                    duplicates++;
                }
            }
        }

        log.info("Sync Shopee concluído: {} criados, {} duplicados, {} ignorados (dado incompleto ou "
                + "desconto abaixo de {}%)", created, duplicates, ignored, minDiscountPercent);
    }

    private boolean isEligible(ShopeeDeal deal) {
        return deal.title() != null && !deal.title().isBlank()
                && deal.url() != null && !deal.url().isBlank()
                && deal.price() != null && deal.price().compareTo(BigDecimal.ZERO) > 0
                && deal.percentOff() >= minDiscountPercent;
    }

    private CreateDealRequest toRequest(ShopeeDeal deal) {
        return new CreateDealRequest(
                deal.title(),
                deal.url(),
                deal.imageUrl(),
                deal.price(),
                deal.originalPrice(),
                Store.SHOPEE);
    }
}
