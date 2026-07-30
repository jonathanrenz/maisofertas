package com.maisofertas.deals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formatação de preço e desconto compartilhada pelos formatters de Telegram e
 * WhatsApp, para que os dois canais mostrem exatamente o mesmo número (ex:
 * {@code R$ 1.499,00}, separador de milhar "." e decimal "," como no Brasil).
 */
public final class PriceFormatter {

    private PriceFormatter() {
    }

    public static String money(BigDecimal value) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR"));
        DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
        return format.format(value.setScale(2, RoundingMode.HALF_UP));
    }

    public static int discountPercent(BigDecimal original, BigDecimal price) {
        if (original == null || original.signum() == 0) {
            return 0;
        }
        BigDecimal diff = original.subtract(price);
        return diff.multiply(BigDecimal.valueOf(100))
                .divide(original, 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
