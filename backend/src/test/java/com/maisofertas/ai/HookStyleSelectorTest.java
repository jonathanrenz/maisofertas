package com.maisofertas.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HookStyleSelectorTest {

    @Test
    void mesmoIdSempreEscolheOMesmoEstilo() {
        UUID dealId = UUID.randomUUID();

        String first = HookStyleSelector.pick(dealId);
        String second = HookStyleSelector.pick(dealId);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void nenhumEstiloEVazioOuNulo() {
        assertThat(HookStyleSelector.STYLES).isNotEmpty();
        assertThat(HookStyleSelector.STYLES).allSatisfy(style -> assertThat(style).isNotBlank());
    }

    @Test
    void distribuiEntreVariosEstilosEmVezDeSempreOMesmo() {
        Set<String> stylesUsados = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            stylesUsados.add(HookStyleSelector.pick(UUID.randomUUID()));
        }

        assertThat(stylesUsados)
                .as("500 ids aleatórios deveriam ter passado por todos os estilos disponíveis")
                .hasSize(HookStyleSelector.STYLES.size());
    }

    @Test
    void estiloEscolhidoSempreVemDaListaOficial() {
        for (int i = 0; i < 50; i++) {
            assertThat(HookStyleSelector.STYLES).contains(HookStyleSelector.pick(UUID.randomUUID()));
        }
    }
}
