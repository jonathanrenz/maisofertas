package com.maisofertas.whatsapp;

import com.maisofertas.deals.Deal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Publica ofertas no grupo de WhatsApp via instância self-hosted da
 * <a href="https://doc.evolution-api.com">Evolution API</a>. Envia a imagem
 * por URL (endpoint {@code sendMedia}) quando o deal tiver {@code imageUrl},
 * ou texto puro ({@code sendText}) caso contrário.
 */
@Component
public class EvolutionApiClient {

    private final RestClient restClient;
    private final String instance;
    private final String groupJid;

    public EvolutionApiClient(
            RestClient.Builder builder,
            @Value("${app.evolution.base-url}") String baseUrl,
            @Value("${app.evolution.instance}") String instance,
            @Value("${app.evolution.api-key}") String apiKey,
            @Value("${app.evolution.group-jid}") String groupJid) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("apikey", apiKey)
                .build();
        this.instance = instance;
        this.groupJid = groupJid;
    }

    public void sendDeal(Deal deal, String caption) {
        if (deal.getImageUrl() != null && !deal.getImageUrl().isBlank()) {
            sendMedia(deal, caption);
        } else {
            sendText(caption);
        }
    }

    private void sendMedia(Deal deal, String caption) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("number", groupJid);
        body.put("mediatype", "image");
        body.put("media", deal.getImageUrl());
        body.put("fileName", "oferta.jpg");
        body.put("caption", caption);

        restClient.post()
                .uri("/message/sendMedia/{instance}", instance)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private void sendText(String caption) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("number", groupJid);
        body.put("text", caption);

        restClient.post()
                .uri("/message/sendText/{instance}", instance)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
