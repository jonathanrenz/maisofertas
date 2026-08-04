package com.maisofertas.ai;

import java.util.List;
import java.util.UUID;

/**
 * Sorteia (de forma determinística, pelo hash do id do deal) um tom de
 * abertura diferente para cada oferta antes de mandar pro prompt da IA.
 *
 * <p>Sem isso, dar um único exemplo fixo no prompt faz o modelo (gpt-5-nano
 * com {@code reasoning_effort=minimal}, pouco espaço pra "criatividade")
 * copiar a estrutura gramatical do exemplo pra toda oferta, só trocando o
 * produto - foi o que gerou hooks quase idênticos tipo "Melhore sua
 * circulação de ar com esse ventilador" / "Melhore sua casa com esse
 * aspirador". Forçar a variedade aqui, no código, é mais confiável do que
 * pedir "seja criativo" pro modelo.
 */
final class HookStyleSelector {

    static final List<String> STYLES = List.of(
            "pergunta curta que desperta curiosidade sobre o benefício (tom de abertura como "
                    + "'Já pensou em...' ou 'Sabia que dá pra...' - crie uma pergunta nova pro produto, "
                    + "não repita esses exemplos)",
            "urgência ou escassez, sem inventar prazo ou quantidade específica (tom de abertura como "
                    + "'Corre que...' ou 'Não vacila e...' - crie uma frase nova, não repita esses exemplos)",
            "benefício direto e visual, descrevendo o resultado prático que o produto entrega, "
                    + "sem clichê de 'melhore sua vida/casa/rotina'",
            "tom descontraído e bem-humorado, como uma dica passada entre amigos",
            "exclamação de quem achou algo bom e quer avisar todo mundo (tom de abertura como "
                    + "'Achei isso e...' ou 'Olha só o que apareceu...' - crie uma frase nova, não repita "
                    + "esses exemplos)",
            "comparação do tipo 'antes era chato resolver isso, agora é rápido', sem exagerar no drama");

    private HookStyleSelector() {
    }

    static String pick(UUID dealId) {
        int index = Math.floorMod(dealId.hashCode(), STYLES.size());
        return STYLES.get(index);
    }
}
