package com.maisofertas.publish;

import com.maisofertas.ai.CaptionGenerator;
import com.maisofertas.deals.Deal;
import com.maisofertas.deals.DealService;
import com.maisofertas.telegram.TelegramBotClient;
import com.maisofertas.whatsapp.EvolutionApiClient;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A cada {@code app.publish.interval-ms}, pega os deals {@code PENDING},
 * gera a legenda e publica no Telegram e no WhatsApp. Cada canal é marcado
 * como postado independentemente (idempotente): se o Telegram já foi
 * publicado numa rodada anterior mas o WhatsApp falhou, a próxima rodada
 * tenta de novo só o WhatsApp, sem duplicar o post no Telegram. O deal só
 * vira {@code POSTED} quando os dois canais tiverem sucesso.
 */
@Component
public class PublishOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PublishOrchestrator.class);

    private final DealService dealService;
    private final CaptionGenerator captionGenerator;
    private final TelegramBotClient telegramBotClient;
    private final EvolutionApiClient evolutionApiClient;

    public PublishOrchestrator(
            DealService dealService,
            CaptionGenerator captionGenerator,
            TelegramBotClient telegramBotClient,
            EvolutionApiClient evolutionApiClient) {
        this.dealService = dealService;
        this.captionGenerator = captionGenerator;
        this.telegramBotClient = telegramBotClient;
        this.evolutionApiClient = evolutionApiClient;
    }

    @Scheduled(fixedDelayString = "${app.publish.interval-ms:300000}")
    public void publishPending() {
        List<Deal> pending = dealService.findPending();
        for (Deal deal : pending) {
            publishOne(deal);
        }
    }

    void publishOne(Deal deal) {
        String caption = captionGenerator.generateCaption(deal);

        if (deal.getPostedTelegramAt() == null) {
            try {
                telegramBotClient.sendDeal(deal, caption);
                dealService.markTelegramPosted(deal.getId());
            } catch (Exception e) {
                log.error("Falha ao publicar deal {} no Telegram", deal.getId(), e);
            }
        }

        if (deal.getPostedWhatsappAt() == null) {
            try {
                evolutionApiClient.sendDeal(deal, caption);
                dealService.markWhatsappPosted(deal.getId());
            } catch (Exception e) {
                log.error("Falha ao publicar deal {} no WhatsApp", deal.getId(), e);
            }
        }
    }
}
