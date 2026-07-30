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
import tools.jackson.databind.ObjectMapper;

class OpenAiDealContentGeneratorTest {

    private MockRestServiceServer server;
    private OpenAiDealContentGenerator generator;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        generator = new OpenAiDealContentGenerator(
                builder, "https://api.openai.local/v1", "test-key", "gpt-5-nano",
                new FallbackDealContentGenerator(), new ObjectMapper());
    }

    private Deal sampleDeal() {
        return Deal.builder()
                .id(UUID.randomUUID())
                .store(Store.AMAZON)
                .title("Echo Dot 5ª Geração com Alexa, Cor Preta")
                .url("https://amazon.com.br/dp/ABC123")
                .price(BigDecimal.valueOf(219.00))
                .originalPrice(BigDecimal.valueOf(349.00))
                .source(DealSource.MANUAL)
                .status(DealStatus.PENDING)
                .createdAt(Instant.now())
                .build();
    }

    private void respondWithContent(String jsonContent) {
        server.expect(requestTo("https://api.openai.local/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("gpt-5-nano"))
                .andExpect(jsonPath("$.reasoning_effort").value("minimal"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andExpect(jsonPath("$.messages[1].content").exists())
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
                                + jsonEscaped(jsonContent) + "}}]}",
                        MediaType.APPLICATION_JSON));
    }

    private String jsonEscaped(String raw) {
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Test
    void retornaConteudoEstruturadoQuandoOpenaiRespondeComJsonValido() {
        respondWithContent(
                "{\"hook\":\"\\ud83d\\udd0a Som que enche a casa\","
                        + "\"productName\":\"Echo Dot 5ª Geração\","
                        + "\"specs\":[\"Com Alexa\",\"Cor Preta\"]}");

        DealContent content = generator.generateContent(sampleDeal());

        assertThat(content.hook()).isEqualTo("🔊 Som que enche a casa");
        assertThat(content.productName()).isEqualTo("Echo Dot 5ª Geração");
        assertThat(content.specs()).containsExactly("Com Alexa", "Cor Preta");
        server.verify();
    }

    @Test
    void usaTituloDoDealComoProductNameQuandoOpenaiOmite() {
        respondWithContent("{\"hook\":\"🔥 Oferta boa\",\"specs\":[]}");

        DealContent content = generator.generateContent(sampleDeal());

        assertThat(content.productName()).isEqualTo(sampleDeal().getTitle());
        assertThat(content.specs()).isEmpty();
    }

    @Test
    void caiNoFallbackQuandoHookEstaAusenteDoJson() {
        respondWithContent("{\"productName\":\"Echo Dot\",\"specs\":[]}");

        DealContent content = generator.generateContent(sampleDeal());

        assertThat(content.productName()).isEqualTo(sampleDeal().getTitle());
        assertThat(content.hook()).isEqualTo("🔥 Oferta imperdível!");
    }

    @Test
    void caiNoFallbackQuandoConteudoNaoEJsonValido() {
        respondWithContent("isso não é um JSON");

        DealContent content = generator.generateContent(sampleDeal());

        assertThat(content.hook()).isEqualTo("🔥 Oferta imperdível!");
        assertThat(content.productName()).isEqualTo(sampleDeal().getTitle());
    }

    @Test
    void caiNoFallbackQuandoOpenAiRespondeComErro() {
        server.expect(requestTo("https://api.openai.local/v1/chat/completions"))
                .andRespond(withServerError());

        DealContent content = generator.generateContent(sampleDeal());

        assertThat(content.hook()).isEqualTo("🔥 Oferta imperdível!");
        assertThat(content.productName()).isEqualTo(sampleDeal().getTitle());
    }

    @Test
    void caiNoFallbackQuandoRespostaNaoTemChoices() {
        server.expect(requestTo("https://api.openai.local/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        DealContent content = generator.generateContent(sampleDeal());

        assertThat(content.hook()).isEqualTo("🔥 Oferta imperdível!");
    }
}
