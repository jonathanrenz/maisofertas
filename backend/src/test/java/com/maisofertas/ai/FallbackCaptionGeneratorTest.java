package com.maisofertas.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.maisofertas.deals.Deal;
import com.maisofertas.deals.DealSource;
import com.maisofertas.deals.DealStatus;
import com.maisofertas.deals.Store;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FallbackCaptionGeneratorTest {

    private final FallbackCaptionGenerator generator = new FallbackCaptionGenerator();

    private Deal.DealBuilder baseDeal() {
        return Deal.builder()
                .id(UUID.randomUUID())
                .store(Store.AMAZON)
                .title("Livro de autoconfiança")
                .url("https://amazon.com.br/dp/ABC123")
                .source(DealSource.MANUAL)
                .status(DealStatus.PENDING)
                .createdAt(Instant.now());
    }

    @Test
    void incluiTituloPrecoEDescontoQuandoHaPrecoAnterior() {
        Deal deal = baseDeal()
                .price(BigDecimal.valueOf(39.90))
                .originalPrice(BigDecimal.valueOf(59.90))
                .build();

        String caption = generator.generateCaption(deal);

        assertThat(caption).contains("Livro de autoconfiança");
        assertThat(caption).contains("39.90");
        assertThat(caption).contains("59.90");
        assertThat(caption).contains("% OFF");
        assertThat(caption).contains(deal.getUrl());
    }

    @Test
    void naoMostraDescontoQuandoNaoHaPrecoAnterior() {
        Deal deal = baseDeal().price(BigDecimal.valueOf(19.90)).build();

        String caption = generator.generateCaption(deal);

        assertThat(caption).doesNotContain("OFF");
        assertThat(caption).contains("19.90");
    }

    @Test
    void calculaPercentualDeDescontoCorretamente() {
        assertThat(FallbackCaptionGenerator.discountPercent(BigDecimal.valueOf(100), BigDecimal.valueOf(75)))
                .isEqualTo(25);
        assertThat(FallbackCaptionGenerator.discountPercent(BigDecimal.valueOf(59.90), BigDecimal.valueOf(39.90)))
                .isEqualTo(33);
    }
}
