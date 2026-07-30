package com.maisofertas.publish;

import com.maisofertas.ai.DealContent;
import com.maisofertas.ai.DealContentGenerator;
import com.maisofertas.deals.Deal;
import com.maisofertas.deals.DealService;
import com.maisofertas.telegram.TelegramBotClient;
import com.maisofertas.telegram.TelegramMessageFormatter;
import com.maisofertas.whatsapp.EvolutionApiClient;
import com.maisofertas.whatsapp.WhatsAppMessageFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A cada {@code app.publish.interval-ms}, pega os deals {@code PENDING},
 * gera o conteúdo (hook + nome + specs) e publica no Telegram e no WhatsApp,
 * cada canal formatado com a marcação nativa dele (preço/desconto/link são
 * sempre calculados de forma determinística, nunca pela IA - ver
 * {@link TelegramMessageFormatter} e {@link WhatsAppMessageFormatter}). Cada
 * canal é marcado como postado independentemente (idempotente): se o
 * Telegram já foi publicado numa rodada anterior mas o WhatsApp falhou, a
 * próxima rodada tenta de novo só o WhatsApp, sem duplicar o post no
 * Telegram. O deal só vira {@code POSTED} quando os dois canais tiverem
 * sucesso.
 */
@Component
public class PublishOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PublishOrchestrator.class);

    private final DealService dealService;
    private final DealContentGenerator contentGenerator;
    private final TelegramMessageFormatter telegramMessageFormatter;
    private final WhatsAppMessageFormatter whatsAppMessageFormatter;
    private final TelegramBotClient telegramBotClient;
    private final EvolutionApiClient evolutionApiClient;

    public PublishOrchestrator(
            DealService dealService,
            DealContentGenerator contentGenerator,
            TelegramMessageFormatter telegramMessageFormatter,
            WhatsAppMessageFormatter whatsAppMessageFormatter,
            TelegramBotClient telegramBotClient,
            EvolutionApiClient evolutionApiClient) {
        this.dealService = dealService;
        this.contentGenerator = contentGenerator;
        this.telegramMessageFormatter = telegramMessageFormatter;
        this.whatsAppMessageFormatter = whatsAppMessageFormatter;
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
        DealContent content = contentGenerator.generateContent(deal);

        if (deal.getPostedTelegramAt() == null) {
            try {
                telegramBotClient.sendDeal(deal, telegramMessageFormatter.format(deal, content));
                dealService.markTelegramPosted(deal.getId());
            } catch (Exception e) {
                log.error("Falha ao publicar deal {} no Telegram", deal.getId(), e);
            }
        }

        if (deal.getPostedWhatsappAt() == null) {
            try {
                evolutionApiClient.sendDeal(deal, whatsAppMessageFormatter.format(deal, content));
                dealService.markWhatsappPosted(deal.getId());
            } catch (Exception e) {
                log.error("Falha ao publicar deal {} no WhatsApp", deal.getId(), e);
            }
        }
    }
}
