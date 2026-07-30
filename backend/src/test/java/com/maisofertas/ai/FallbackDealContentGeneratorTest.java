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

class FallbackDealContentGeneratorTest {

    private final FallbackDealContentGenerator generator = new FallbackDealContentGenerator();

    @Test
    void devolveHookGenericoNomeIgualAoTituloESemSpecsInventadas() {
        Deal deal = Deal.builder()
                .id(UUID.randomUUID())
                .store(Store.AMAZON)
                .title("Livro de autoconfiança")
                .url("https://amazon.com.br/dp/ABC123")
                .price(BigDecimal.valueOf(39.90))
                .originalPrice(BigDecimal.valueOf(59.90))
                .source(DealSource.MANUAL)
                .status(DealStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        DealContent content = generator.generateContent(deal);

        assertThat(content.hook()).isNotBlank();
        assertThat(content.productName()).isEqualTo("Livro de autoconfiança");
        assertThat(content.specs()).isEmpty();
    }
}
