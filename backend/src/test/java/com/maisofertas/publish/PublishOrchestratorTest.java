package com.maisofertas.publish;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maisofertas.ai.CaptionGenerator;
import com.maisofertas.deals.Deal;
import com.maisofertas.deals.DealService;
import com.maisofertas.deals.DealSource;
import com.maisofertas.deals.DealStatus;
import com.maisofertas.deals.Store;
import com.maisofertas.telegram.TelegramBotClient;
import com.maisofertas.whatsapp.EvolutionApiClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublishOrchestratorTest {

    private final DealService dealService = mock(DealService.class);
    private final CaptionGenerator captionGenerator = mock(CaptionGenerator.class);
    private final TelegramBotClient telegramBotClient = mock(TelegramBotClient.class);
    private final EvolutionApiClient evolutionApiClient = mock(EvolutionApiClient.class);

    private final PublishOrchestrator orchestrator =
            new PublishOrchestrator(dealService, captionGenerator, telegramBotClient, evolutionApiClient);

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
        when(captionGenerator.generateCaption(any())).thenReturn("Legenda gerada");
    }

    @Test
    void publicaNosDoisCanaisQuandoNenhumFoiPostadoAinda() {
        Deal deal = baseDeal().build();

        orchestrator.publishOne(deal);

        verify(telegramBotClient).sendDeal(deal, "Legenda gerada");
        verify(evolutionApiClient).sendDeal(deal, "Legenda gerada");
        verify(dealService).markTelegramPosted(deal.getId());
        verify(dealService).markWhatsappPosted(deal.getId());
    }

    @Test
    void naoReenviaCanalQueJaFoiPostado() {
        Deal deal = baseDeal().postedTelegramAt(Instant.now()).build();

        orchestrator.publishOne(deal);

        verify(telegramBotClient, never()).sendDeal(any(), any());
        verify(dealService, never()).markTelegramPosted(any());
        verify(evolutionApiClient, times(1)).sendDeal(eq(deal), eq("Legenda gerada"));
        verify(dealService).markWhatsappPosted(deal.getId());
    }

    @Test
    void naoPropagaExcecaoQuandoUmCanalFalha() {
        Deal deal = baseDeal().build();
        doThrow(new RuntimeException("falha de rede")).when(telegramBotClient).sendDeal(any(), any());

        orchestrator.publishOne(deal);

        verify(dealService, never()).markTelegramPosted(any());
        verify(evolutionApiClient).sendDeal(deal, "Legenda gerada");
        verify(dealService).markWhatsappPosted(deal.getId());
    }
}
