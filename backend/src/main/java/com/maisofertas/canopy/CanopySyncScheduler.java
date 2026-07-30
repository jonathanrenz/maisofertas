package com.maisofertas.canopy;

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
 * Fase 1: busca automática de ofertas Amazon via Canopy API. Desligado por
 * padrão ({@code app.canopy.sync.enabled=false}) até a API key estar
 * configurada.
 *
 * <p>Plano pay-as-you-go da Canopy: 100 requests/mês grátis, $0.01 por
 * request extra. Com {@code pages-per-run=1} e intervalo de 1h (24
 * rodadas/dia), o consumo é ~720 requests/mês (~US$6,20/mês em requests
 * extras). Cada aumento de frequência ou de pages-per-run multiplica o
 * consumo mensal linearmente — recalcule antes de mudar.
 */
@Component
@ConditionalOnProperty(name = "app.canopy.sync.enabled", havingValue = "true")
public class CanopySyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(CanopySyncScheduler.class);

    private final CanopyClient canopyClient;
    private final DealService dealService;
    private final int pagesPerRun;
    private final int minDiscountPercent;

    public CanopySyncScheduler(
            CanopyClient canopyClient,
            DealService dealService,
            @Value("${app.canopy.sync.pages-per-run:1}") int pagesPerRun,
            @Value("${app.canopy.min-discount-percent:20}") int minDiscountPercent) {
        this.canopyClient = canopyClient;
        this.dealService = dealService;
        this.pagesPerRun = pagesPerRun;
        this.minDiscountPercent = minDiscountPercent;
    }

    @Scheduled(fixedDelayString = "${app.canopy.sync.interval-ms:28800000}")
    public void syncDeals() {
        int created = 0;
        int duplicates = 0;
        int ignored = 0;

        for (int page = 1; page <= pagesPerRun; page++) {
            List<CanopyDeal> deals;
            try {
                deals = canopyClient.fetchDeals(page);
            } catch (Exception e) {
                log.warn("Falha ao buscar página {} de ofertas na Canopy API, parando essa rodada", page, e);
                break;
            }

            for (CanopyDeal deal : deals) {
                if (!isEligible(deal)) {
                    ignored++;
                    continue;
                }
                try {
                    dealService.createDeal(toRequest(deal), DealSource.CANOPY);
                    created++;
                } catch (DuplicateDealException e) {
                    duplicates++;
                }
            }
        }

        log.info("Sync Canopy concluído: {} criados, {} duplicados, {} ignorados (dado incompleto ou "
                + "desconto abaixo de {}%)", created, duplicates, ignored, minDiscountPercent);
    }

    private boolean isEligible(CanopyDeal deal) {
        return deal.title() != null && !deal.title().isBlank()
                && deal.url() != null && !deal.url().isBlank()
                && deal.price() != null && deal.price().compareTo(BigDecimal.ZERO) > 0
                && deal.percentOff() >= minDiscountPercent;
    }

    private CreateDealRequest toRequest(CanopyDeal deal) {
        return new CreateDealRequest(
                deal.title(),
                deal.url(),
                deal.imageUrl(),
                deal.price(),
                deal.originalPrice(),
                Store.AMAZON);
    }
}
