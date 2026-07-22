package com.maisofertas.ai;

import com.maisofertas.deals.Deal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Legenda determinística, sem IA. Usada quando a chamada à OpenAI falha (rede,
 * chave inválida, resposta vazia) para que a publicação nunca trave por causa
 * da IA.
 */
@Component
public class FallbackCaptionGenerator implements CaptionGenerator {

    @Override
    public String generateCaption(Deal deal) {
        StringBuilder sb = new StringBuilder();
        sb.append("🔥 OFERTA: ").append(deal.getTitle()).append("\n\n");

        BigDecimal original = deal.getOriginalPrice();
        BigDecimal price = deal.getPrice();
        if (original != null && original.compareTo(price) > 0) {
            sb.append("De ~R$ ").append(money(original)).append("~ por R$ ").append(money(price))
                    .append(" (").append(discountPercent(original, price)).append("% OFF) 📉\n\n");
        } else {
            sb.append("Por R$ ").append(money(price)).append(" 💰\n\n");
        }

        sb.append("👉 ").append(deal.getUrl());
        return sb.toString();
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toString();
    }

    static int discountPercent(BigDecimal original, BigDecimal price) {
        if (original == null || original.signum() == 0) {
            return 0;
        }
        BigDecimal diff = original.subtract(price);
        return diff.multiply(BigDecimal.valueOf(100))
                .divide(original, 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
