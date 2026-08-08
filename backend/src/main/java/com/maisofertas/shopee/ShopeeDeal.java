package com.maisofertas.shopee;

import java.math.BigDecimal;

/**
 * Oferta já normalizada a partir da resposta da Shopee Affiliate Open API
 * (GraphQL {@code productOfferV2}) - o formato bruto e o cálculo de
 * {@code originalPrice} a partir de {@code priceDiscountRate} ficam isolados
 * dentro de {@link ShopeeClient}.
 */
public record ShopeeDeal(
        Long itemId,
        String title,
        String url,
        String imageUrl,
        BigDecimal price,
        BigDecimal originalPrice,
        int percentOff) {
}
