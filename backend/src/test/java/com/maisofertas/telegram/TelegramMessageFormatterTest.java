package com.maisofertas.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.maisofertas.ai.DealContent;
import com.maisofertas.deals.Deal;
import com.maisofertas.deals.DealSource;
import com.maisofertas.deals.DealStatus;
import com.maisofertas.deals.Store;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TelegramMessageFormatterTest {

    private final TelegramMessageFormatter formatter = new TelegramMessageFormatter();

    private Deal.DealBuilder baseDeal() {
        return Deal.builder()
                .id(UUID.randomUUID())
                .store(Store.AMAZON)
                .title("Echo Dot 5ª Geração com Alexa")
                .url("https://amazon.com.br/dp/ABC123?tag=maisoferta0e0-20")
                .source(DealSource.MANUAL)
                .status(DealStatus.PENDING)
                .createdAt(Instant.now());
    }

    @Test
    void montaMensagemComTodasAsSecoesSeparadasPorLinhaEmBranco() {
        Deal deal = baseDeal().price(BigDecimal.valueOf(219.00)).originalPrice(BigDecimal.valueOf(349.00)).build();
        DealContent content = new DealContent("🔊 Som que enche a casa", "Echo Dot 5ª Geração",
                List.of("Com Alexa", "Cor Preta"));

        String message = formatter.format(deal, content);

        assertThat(message).isEqualTo(
                "*🔊 Som que enche a casa*\n\n"
                        + "📦 *Echo Dot 5ª Geração*\n"
                        + "• Com Alexa\n"
                        + "• Cor Preta\n\n"
                        + "🔥 De ~R$ 349,00~ por *R$ 219,00* \\(37% OFF\\)\n\n"
                        + "🛒 [*Ver oferta na Amazon*](https://amazon.com.br/dp/ABC123?tag=maisoferta0e0-20)");
    }

    @Test
    void naoMostraRiscadoNemDescontoQuandoNaoHaPrecoAnterior() {
        Deal deal = baseDeal().price(BigDecimal.valueOf(219.00)).build();
        DealContent content = new DealContent("🔊 Som bom", "Echo Dot", List.of());

        String message = formatter.format(deal, content);

        assertThat(message).contains("🔥 Por *R$ 219,00*");
        assertThat(message).doesNotContain("~");
        assertThat(message).doesNotContain("OFF");
    }

    @Test
    void naoDeixaLinhaSobrandoQuandoNaoHaSpecs() {
        Deal deal = baseDeal().price(BigDecimal.TEN).build();
        DealContent content = new DealContent("🔥 Oferta imperdível!", "Produto genérico", List.of());

        String message = formatter.format(deal, content);

        assertThat(message).contains("📦 *Produto genérico*\n\n🔥 Por");
    }

    @Test
    void escapaCaracteresReservadosDoMarkdownV2NoTextoDinamico() {
        Deal deal = baseDeal().title("Fone (Preto) - Bluetooth 5.0!").price(BigDecimal.TEN).build();
        DealContent content = new DealContent("🎧 Ótimo p/ academia (1).",
                "Fone (Preto) - Bluetooth 5.0!", List.of("Bateria 20h."));

        String message = formatter.format(deal, content);

        assertThat(message).contains("🎧 Ótimo p/ academia \\(1\\)\\.");
        assertThat(message).contains("📦 *Fone \\(Preto\\) \\- Bluetooth 5\\.0\\!*");
        assertThat(message).contains("• Bateria 20h\\.");
    }

    @Test
    void usaONomeDaLojaCorrespondenteNoLinkDaOferta() {
        Deal deal = baseDeal().store(Store.SHOPEE).price(BigDecimal.TEN).build();
        DealContent content = new DealContent("🔥 Hook", "Produto", List.of());

        String message = formatter.format(deal, content);

        assertThat(message).contains("🛒 [*Ver oferta na Shopee*]");
    }

    @Test
    void escapaApenasFechaParenteseEBarraInvertidaDentroDoLink() {
        // MarkdownV2 só exige escapar ')' e '\' dentro do (url) de um link - '(' fica como está.
        Deal deal = baseDeal().url("https://amazon.com.br/dp/ABC?tag=x-20&note=(a)").price(BigDecimal.TEN).build();
        DealContent content = new DealContent("🔥 Hook", "Produto", List.of());

        String message = formatter.format(deal, content);

        assertThat(message).endsWith(
                "🛒 [*Ver oferta na Amazon*](https://amazon.com.br/dp/ABC?tag=x-20&note=(a\\))");
    }
}
