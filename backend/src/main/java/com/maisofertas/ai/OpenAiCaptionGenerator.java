package com.maisofertas.ai;

import com.maisofertas.deals.Deal;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Gera a legenda via API da OpenAI (modelo econômico, configurável em
 * {@code app.ai.openai.model}). Qualquer falha (rede, chave ausente/invalida,
 * resposta vazia) cai automaticamente no {@link FallbackCaptionGenerator}
 * determinístico — publicar uma oferta nunca deve travar por causa da IA.
 */
@Service
@Primary
public class OpenAiCaptionGenerator implements CaptionGenerator {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCaptionGenerator.class);

    private final RestClient restClient;
    private final String model;
    private final String promptTemplate;
    private final FallbackCaptionGenerator fallback;

    public OpenAiCaptionGenerator(
            RestClient.Builder builder,
            @Value("${app.ai.openai.base-url}") String baseUrl,
            @Value("${app.ai.openai.api-key}") String apiKey,
            @Value("${app.ai.openai.model}") String model,
            FallbackCaptionGenerator fallback) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.model = model;
        this.fallback = fallback;
        this.promptTemplate = loadPromptTemplate();
    }

    @Override
    public String generateCaption(Deal deal) {
        try {
            String userPrompt = promptTemplate.formatted(
                    deal.getTitle(),
                    deal.getStore(),
                    deal.getPrice(),
                    deal.getOriginalPrice() != null ? "R$ " + deal.getOriginalPrice() : "sem preço anterior informado");

            ChatRequest request = new ChatRequest(model, List.of(
                    new ChatMessage("system",
                            "Você escreve legendas curtas e empolgantes de ofertas para grupos de WhatsApp "
                                    + "e canais de Telegram, em português do Brasil, sempre com emojis relevantes."),
                    new ChatMessage("user", userPrompt)));

            ChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);

            String content = response != null && !response.choices().isEmpty()
                    ? response.choices().get(0).message().content()
                    : null;

            if (content == null || content.isBlank()) {
                throw new IllegalStateException("Resposta vazia da OpenAI");
            }
            return content.trim() + "\n\n👉 " + deal.getUrl();
        } catch (Exception e) {
            log.warn("Falha ao gerar legenda via OpenAI para deal {}, usando fallback determinístico",
                    deal.getId(), e);
            return fallback.generateCaption(deal);
        }
    }

    private String loadPromptTemplate() {
        try (InputStream is = new ClassPathResource("prompts/caption-prompt.txt").getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Não consegui carregar prompts/caption-prompt.txt", e);
        }
    }

    record ChatMessage(String role, String content) {
    }

    record ChatRequest(String model, List<ChatMessage> messages) {
    }

    record ChatResponse(List<Choice> choices) {
    }

    record Choice(ChatMessage message) {
    }
}
