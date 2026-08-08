package com.maisofertas.telegram;

import com.maisofertas.ai.DealContent;
import com.maisofertas.deals.Deal;
import com.maisofertas.deals.PriceFormatter;
import java.math.BigDecimal;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Monta a mensagem final enviada ao Telegram a partir do {@link DealContent}
 * (hook + nome + specs, vindo da IA ou do fallback) e do {@link Deal} (preço,
 * desconto, link - sempre calculados aqui, nunca pela IA). Usa MarkdownV2
 * (não o "Markdown" legado) porque é o único parse mode do Bot API com
 * suporte real a texto riscado (~texto~) para o preço "de".
 *
 * <p>MarkdownV2 exige escapar todo caractere reservado que apareça como texto
 * literal (fora de um span de formatação), senão a Bot API rejeita a
 * mensagem inteira com 400 "can't parse entities" - por isso todo texto
 * dinâmico (hook, nome, specs, valores em R$) passa por {@link #escape}.
 */
@Component
public class TelegramMessageFormatter {

    private static final Pattern RESERVED = Pattern.compile("([_*\\[\\]()~`>#+\\-=|{}.!\\\\])");

    public String format(Deal deal, DealContent content) {
        StringBuilder sb = new StringBuilder();

        sb.append('*').append(escape(content.hook())).append("*\n\n");

        sb.append("📦 *").append(escape(content.productName())).append('*');
        for (String spec : content.specs()) {
            sb.append("\n• ").append(escape(spec));
        }
        sb.append("\n\n");

        sb.append(priceLine(deal)).append("\n\n");

        sb.append("🛒 [*Ver oferta na ").append(escape(deal.getStore().displayName())).append("*](")
                .append(escapeUrl(deal.getUrl())).append(')');

        return sb.toString();
    }

    private String priceLine(Deal deal) {
        BigDecimal price = deal.getPrice();
        BigDecimal original = deal.getOriginalPrice();

        if (original != null && original.compareTo(price) > 0) {
            int discount = PriceFormatter.discountPercent(original, price);
            return "🔥 De ~R$ " + escape(PriceFormatter.money(original)) + "~ por *R$ "
                    + escape(PriceFormatter.money(price)) + "* \\(" + discount + "% OFF\\)";
        }
        return "🔥 Por *R$ " + escape(PriceFormatter.money(price)) + "*";
    }

    static String escape(String text) {
        return text == null ? "" : RESERVED.matcher(text).replaceAll("\\\\$1");
    }

    /**
     * Dentro do {@code (url)} de um link MarkdownV2 só {@code )} e {@code \}
     * precisam de escape (diferente do resto da mensagem) - ver
     * <a href="https://core.telegram.org/bots/api#markdownv2-style">docs</a>.
     */
    static String escapeUrl(String url) {
        return url == null ? "" : url.replace("\\", "\\\\").replace(")", "\\)");
    }
}
