package com.maisofertas.deals;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PriceFormatterTest {

    @Test
    void formataValorNoPadraoBrasileiroComMilharEDecimal() {
        assertThat(PriceFormatter.money(BigDecimal.valueOf(2000))).isEqualTo("2.000,00");
        assertThat(PriceFormatter.money(BigDecimal.valueOf(1499.00))).isEqualTo("1.499,00");
        assertThat(PriceFormatter.money(BigDecimal.valueOf(19.9))).isEqualTo("19,90");
    }

    @Test
    void arredondaParaDuasCasasDecimais() {
        assertThat(PriceFormatter.money(BigDecimal.valueOf(19.999))).isEqualTo("20,00");
    }

    @Test
    void calculaPercentualDeDescontoCorretamente() {
        assertThat(PriceFormatter.discountPercent(BigDecimal.valueOf(100), BigDecimal.valueOf(75)))
                .isEqualTo(25);
        assertThat(PriceFormatter.discountPercent(BigDecimal.valueOf(59.90), BigDecimal.valueOf(39.90)))
                .isEqualTo(33);
    }

    @Test
    void devolveZeroQuandoNaoHaPrecoOriginalOuEleEZero() {
        assertThat(PriceFormatter.discountPercent(null, BigDecimal.TEN)).isZero();
        assertThat(PriceFormatter.discountPercent(BigDecimal.ZERO, BigDecimal.TEN)).isZero();
    }
}
