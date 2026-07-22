package com.maisofertas.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.maisofertas.deals.Deal;
import com.maisofertas.deals.DealSource;
import com.maisofertas.deals.DealStatus;
import com.maisofertas.deals.Store;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiCaptionGeneratorTest {

    private MockRestServiceServer server;
    private OpenAiCaptionGenerator generator;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        generator = new OpenAiCaptionGenerator(
                builder, "https://api.openai.local/v1", "test-key", "gpt-5-nano", new FallbackCaptionGenerator());
    }

    private Deal sampleDeal() {
        return Deal.builder()
                .id(UUID.randomUUID())
                .store(Store.AMAZON)
                .title("Livro de autoconfiança")
                .url("https://amazon.com.br/dp/ABC123")
                .price(BigDecimal.valueOf(39.90))
                .originalPrice(BigDecimal.valueOf(59.90))
                .source(DealSource.MANUAL)
                .status(DealStatus.PENDING)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void retornaLegendaDaOpenAiQuandoChamadaFunciona() {
        server.expect(requestTo("https://api.openai.local/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("gpt-5-nano"))
                .andExpect(jsonPath("$.messages[1].content").exists())
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
                                + "\"Melhore sua autoconfiança com esse livro aqui \\ud83d\\udcda\\u2728: por R$ 39.90!\"}}]}",
                        MediaType.APPLICATION_JSON));

        String caption = generator.generateCaption(sampleDeal());

        assertThat(caption).isEqualTo("Melhore sua autoconfiança com esse livro aqui 📚✨: por R$ 39.90!");
        server.verify();
    }

    @Test
    void caiNoFallbackQuandoOpenAiRespondeComErro() {
        server.expect(requestTo("https://api.openai.local/v1/chat/completions"))
                .andRespond(withServerError());

        String caption = generator.generateCaption(sampleDeal());

        assertThat(caption).contains("Livro de autoconfiança");
        assertThat(caption).contains("OFERTA");
    }

    @Test
    void caiNoFallbackQuandoRespostaNaoTemChoices() {
        server.expect(requestTo("https://api.openai.local/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        String caption = generator.generateCaption(sampleDeal());

        assertThat(caption).contains("Livro de autoconfiança");
        assertThat(caption).contains("OFERTA");
    }
}
