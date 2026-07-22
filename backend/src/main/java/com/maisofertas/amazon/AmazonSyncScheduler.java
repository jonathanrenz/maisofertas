package com.maisofertas.amazon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fase 1: busca automática de ofertas na Amazon.
 *
 * <p>Desligado por padrão ({@code app.amazon.sync.enabled=false}) porque a
 * Creators API da Amazon só libera acesso com 10 vendas qualificadas nos
 * últimos 30 dias — a conta ainda não bateu esse número. Fica pronto pra
 * ligar (Creators API ou Keepa) sem tocar no resto do pipeline: qualquer
 * implementação aqui só precisa chamar {@code DealService.createDeal(...)}
 * com {@code DealSource.CREATORS_API} ou {@code DealSource.KEEPA}.
 */
@Component
@ConditionalOnProperty(name = "app.amazon.sync.enabled", havingValue = "true")
public class AmazonSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(AmazonSyncScheduler.class);

    @Scheduled(fixedDelayString = "${app.amazon.sync.interval-ms:1800000}")
    public void syncDeals() {
        log.warn("AmazonSyncScheduler está habilitado mas ainda não implementado — "
                + "plugue aqui a Creators API ou a Keepa quando estiver disponível.");
    }
}
