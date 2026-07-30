package com.maisofertas.canopy;

import java.math.BigDecimal;

/**
 * Oferta já normalizada a partir da resposta da Canopy API — o formato bruto
 * (envelope GraphQL-like com preços aninhados) fica isolado dentro de
 * {@link CanopyClient}.
 */
public record CanopyDeal(
        String asin,
        String title,
        String url,
        String imageUrl,
        BigDecimal price,
        BigDecimal originalPrice,
        int percentOff) {
}
