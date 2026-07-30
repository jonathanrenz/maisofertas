package com.maisofertas.publish;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maisofertas.ai.DealContent;
import com.maisofertas.ai.DealContentGenerator;
import com.maisofertas.deals.Deal;
import com.maisofertas.deals.DealService;
import com.maisofertas.deals.DealSource;
import com.maisofertas.deals.DealStatus;
import com.maisofertas.deals.Store;
import com.maisofertas.telegram.TelegramBotClient;
import com.maisofertas.telegram.TelegramMessageFormatter;
import com.maisofertas.whatsapp.EvolutionApiClient;
import com.maisofertas.whatsapp.WhatsAppMessageFormatter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublishOrchestratorTest {

    private final DealService dealService = mock(DealService.class);
    private final DealContentGenerator contentGenerator = mock(DealContentGenerator.class);
    private final TelegramMessageFormatter telegramMessageFormatter = mock(TelegramMessageFormatter.class);
    private final WhatsAppMessageFormatter whatsAppMessageFormatter = mock(WhatsAppMessageFormatter.class);
    private final TelegramBotClient telegramBotClient = mock(TelegramBotClient.class);
    private final EvolutionApiClient evolutionApiClient = mock(EvolutionApiClient.class);

    private final PublishOrchestrator orchestrator = new PublishOrchestrator(
            dealService, contentGenerator, telegramMessageFormatter, whatsAppMessageFormatter,
            telegramBotClient, evolutionApiClient);

    private final DealContent content = new DealContent("🔥 Hook", "Produto", List.of());

    private Deal.DealBuilder baseDeal() {
        return Deal.builder()
                .id(UUID.randomUUID())
                .store(Store.AMAZON)
                .title("Produto")
                .url("https://amazon.com.br/dp/ABC")
                .price(BigDecimal.TEN)
                .source(DealSource.MANUAL)
                .status(DealStatus.PENDING)
                .createdAt(Instant.now());
    }

    @BeforeEach
    void setUp() {
        when(contentGenerator.generateContent(any())).thenReturn(content);
        when(telegramMessageFormatter.format(any(), eq(content))).thenReturn("Mensagem Telegram");
        when(whatsAppMessageFormatter.format(any(), eq(content))).thenReturn("Mensagem WhatsApp");
    }

    @Test
    void publicaNosDoisCanaisQuandoNenhumFoiPostadoAinda() {
        Deal deal = baseDeal().build();

        orchestrator.publishOne(deal);

        verify(telegramBotClient).sendDeal(deal, "Mensagem Telegram");
        verify(evolutionApiClient).sendDeal(deal, "Mensagem WhatsApp");
        verify(dealService).markTelegramPosted(deal.getId());
        verify(dealService).markWhatsappPosted(deal.getId());
    }

    @Test
    void naoReenviaCanalQueJaFoiPostado() {
        Deal deal = baseDeal().postedTelegramAt(Instant.now()).build();

        orchestrator.publishOne(deal);

        verify(telegramBotClient, never()).sendDeal(any(), any());
        verify(dealService, never()).markTelegramPosted(any());
        verify(evolutionApiClient, times(1)).sendDeal(eq(deal), eq("Mensagem WhatsApp"));
        verify(dealService).markWhatsappPosted(deal.getId());
    }

    @Test
    void naoPropagaExcecaoQuandoUmCanalFalha() {
        Deal deal = baseDeal().build();
        doThrow(new RuntimeException("falha de rede")).when(telegramBotClient).sendDeal(any(), any());

        orchestrator.publishOne(deal);

        verify(dealService, never()).markTelegramPosted(any());
        verify(evolutionApiClient).sendDeal(deal, "Mensagem WhatsApp");
        verify(dealService).markWhatsappPosted(deal.getId());
    }
}
