package com.maisofertas.deals;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.hibernate.validator.constraints.URL;

public record CreateDealRequest(
        @NotBlank String title,
        @NotBlank @URL String url,
        @URL String imageUrl,
        @NotNull @Positive BigDecimal price,
        @Positive BigDecimal originalPrice,
        Store store) {
}
