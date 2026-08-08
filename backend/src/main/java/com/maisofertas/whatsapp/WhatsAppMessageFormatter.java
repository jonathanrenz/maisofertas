package com.maisofertas.whatsapp;

import com.maisofertas.ai.DealContent;
import com.maisofertas.deals.Deal;
import com.maisofertas.deals.PriceFormatter;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Monta a mensagem final enviada ao WhatsApp a partir do mesmo
 * {@link DealContent} usado pelo Telegram, mas com a marcação nativa do
 * WhatsApp ({@code *negrito*}, {@code ~riscado~}), que é diferente do
 * MarkdownV2 do Telegram e não tem (nem precisa de) mecanismo de escape: o
 * WhatsApp só nunca ativa dois {@code *}/{@code ~} desbalanceados, então em
 * vez de escapar caracteres reservados como no Telegram, {@link #sanitize}
 * remove qualquer {@code * _ ~ ` } que porventura venha no texto gerado pela
 * IA para não quebrar a formatação do resto da mensagem.
 */
@Component
public class WhatsAppMessageFormatter {

    public String format(Deal deal, DealContent content) {
        StringBuilder sb = new StringBuilder();

        sb.append('*').append(sanitize(content.hook())).append("*\n\n");

        sb.append("📦 *").append(sanitize(content.productName())).append('*');
        for (String spec : content.specs()) {
            sb.append("\n• ").append(sanitize(spec));
        }
        sb.append("\n\n");

        sb.append(priceLine(deal)).append("\n\n");

        sb.append("🛒 *Ver oferta na ").append(sanitize(deal.getStore().displayName())).append(":*\n")
                .append(deal.getUrl());

        return sb.toString();
    }

    private String priceLine(Deal deal) {
        BigDecimal price = deal.getPrice();
        BigDecimal original = deal.getOriginalPrice();

        if (original != null && original.compareTo(price) > 0) {
            int discount = PriceFormatter.discountPercent(original, price);
            return "🔥 De ~R$ " + PriceFormatter.money(original) + "~ por *R$ "
                    + PriceFormatter.money(price) + "* (" + discount + "% OFF)";
        }
        return "🔥 Por *R$ " + PriceFormatter.money(price) + "*";
    }

    static String sanitize(String text) {
        return text == null ? "" : text.replaceAll("[*_~`]", "");
    }
}
