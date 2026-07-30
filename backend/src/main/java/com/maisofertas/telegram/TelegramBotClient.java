package com.maisofertas.telegram;

import com.maisofertas.deals.Deal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Publica ofertas no canal do Telegram via Bot API (chamada REST direta, sem
 * SDK de terceiros). Envia foto com legenda ({@code sendPhoto}) quando o deal
 * tiver {@code imageUrl}, ou texto puro ({@code sendMessage}) caso contrário.
 */
@Component
public class TelegramBotClient {

    private final RestClient restClient;
    private final String chatId;

    public TelegramBotClient(
            RestClient.Builder builder,
            @Value("${app.telegram.bot-token}") String botToken,
            @Value("${app.telegram.chat-id}") String chatId) {
        this.restClient = builder
                .baseUrl("https://api.telegram.org/bot" + botToken)
                .build();
        this.chatId = chatId;
    }

    public void sendDeal(Deal deal, String caption) {
        if (deal.getImageUrl() != null && !deal.getImageUrl().isBlank()) {
            sendPhoto(deal, caption);
        } else {
            sendMessage(caption);
        }
    }

    private void sendPhoto(Deal deal, String caption) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("photo", deal.getImageUrl());
        body.put("caption", caption);
        body.put("parse_mode", "MarkdownV2");

        restClient.post()
                .uri("/sendPhoto")
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private void sendMessage(String caption) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", caption);
        body.put("parse_mode", "MarkdownV2");

        restClient.post()
                .uri("/sendMessage")
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
