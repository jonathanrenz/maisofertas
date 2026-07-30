package com.maisofertas.ai;

import com.maisofertas.deals.Deal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Conteúdo determinístico, sem IA. Usado quando a chamada à OpenAI falha
 * (rede, chave inválida, resposta vazia ou JSON inválido) para que a
 * publicação nunca trave por causa da IA. Sem IA não há como extrair specs
 * do título com segurança, então a lista vem vazia em vez de arriscar
 * inventar characterísticas.
 */
@Component
public class FallbackDealContentGenerator implements DealContentGenerator {

    @Override
    public DealContent generateContent(Deal deal) {
        return new DealContent("🔥 Oferta imperdível!", deal.getTitle(), List.of());
    }
}
