package com.maisofertas.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.maisofertas.deals.Deal;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Gera o conteúdo estruturado (hook + nome do produto + specs) via API da
 * OpenAI (modelo econômico, configurável em {@code app.ai.openai.model}),
 * forçando {@code response_format=json_object} para nunca depender de
 * parsing frágil de texto livre. Qualquer falha (rede, chave ausente/inválida,
 * resposta vazia ou JSON inesperado) cai automaticamente no
 * {@link FallbackDealContentGenerator} determinístico — publicar uma oferta
 * nunca deve travar por causa da IA.
 */
@Service
@Primary
public class OpenAiDealContentGenerator implements DealContentGenerator {

    private static final Logger log = LoggerFactory.getLogger(OpenAiDealContentGenerator.class);

    private final RestClient restClient;
    private final String model;
    private final String promptTemplate;
    private final FallbackDealContentGenerator fallback;
    private final ObjectMapper objectMapper;

    public OpenAiDealContentGenerator(
            RestClient.Builder builder,
            @Value("${app.ai.openai.base-url}") String baseUrl,
            @Value("${app.ai.openai.api-key}") String apiKey,
            @Value("${app.ai.openai.model}") String model,
            FallbackDealContentGenerator fallback,
            ObjectMapper objectMapper) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.model = model;
        this.fallback = fallback;
        this.objectMapper = objectMapper;
        this.promptTemplate = loadPromptTemplate();
    }

    @Override
    public DealContent generateContent(Deal deal) {
        try {
            String userPrompt = promptTemplate.formatted(deal.getTitle(), deal.getStore());

            // reasoning_effort=minimal: sem isso o gpt-5-nano gasta ~2500 reasoning
            // tokens invisíveis (cobrados como output) numa tarefa de texto curto e
            // simples como essa; com "minimal" cai pra ~50-70 tokens, mesma qualidade
            // visível, ~38x mais barato.
            ChatRequest request = new ChatRequest(model, "minimal", new ResponseFormat("json_object"), List.of(
                    new ChatMessage("system",
                            "Você escreve conteúdo estruturado de ofertas para grupos de WhatsApp e canais "
                                    + "de Telegram, em português do Brasil. Responda sempre com um JSON válido, "
                                    + "nunca invente características do produto."),
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
            return parse(content, deal);
        } catch (Exception e) {
            log.warn("Falha ao gerar conteúdo via OpenAI para deal {}, usando fallback determinístico",
                    deal.getId(), e);
            return fallback.generateContent(deal);
        }
    }

    private DealContent parse(String json, Deal deal) throws IOException {
        Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
        });

        String hook = asTrimmedString(parsed.get("hook"));
        if (hook == null || hook.isBlank()) {
            throw new IllegalStateException("JSON da OpenAI sem 'hook' válido: " + json);
        }

        String productName = asTrimmedString(parsed.get("productName"));
        if (productName == null || productName.isBlank()) {
            productName = deal.getTitle();
        }

        return new DealContent(hook, productName, asStringList(parsed.get("specs")));
    }

    private List<String> asStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(item -> item instanceof String s && !s.isBlank())
                .map(item -> ((String) item).trim())
                .toList();
    }

    private String asTrimmedString(Object value) {
        return value instanceof String s ? s.trim() : null;
    }

    private String loadPromptTemplate() {
        try (InputStream is = new ClassPathResource("prompts/deal-content-prompt.txt").getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Não consegui carregar prompts/deal-content-prompt.txt", e);
        }
    }

    record ChatMessage(String role, String content) {
    }

    record ResponseFormat(String type) {
    }

    record ChatRequest(String model, @JsonProperty("reasoning_effort") String reasoningEffort,
            @JsonProperty("response_format") ResponseFormat responseFormat,
            List<ChatMessage> messages) {
    }

    record ChatResponse(List<Choice> choices) {
    }

    record Choice(ChatMessage message) {
    }
}
