package com.maisofertas.telegram;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
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

class TelegramBotClientTest {

    private MockRestServiceServer server;
    private TelegramBotClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TelegramBotClient(builder, "123:ABC-TOKEN", "-1001234567890");
    }

    private Deal.DealBuilder baseDeal() {
        return Deal.builder()
                .id(UUID.randomUUID())
                .store(Store.AMAZON)
                .title("Produto")
                .url("https://amazon.com.br/dp/ABC")
                .price(BigDecimal.TEN)
                .source(DealSource.MANUAL)
                .status(DealStatus.PENDING)
                .createdAt(Instant.now());
    }

    @Test
    void enviaSendPhotoQuandoDealTemImagem() {
        Deal deal = baseDeal().imageUrl("https://images.example/produto.jpg").build();

        server.expect(requestTo("https://api.telegram.org/bot123:ABC-TOKEN/sendPhoto"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.chat_id").value("-1001234567890"))
                .andExpect(jsonPath("$.photo").value("https://images.example/produto.jpg"))
                .andExpect(jsonPath("$.caption").value("Legenda de teste"))
                .andExpect(jsonPath("$.parse_mode").value("MarkdownV2"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        client.sendDeal(deal, "Legenda de teste");

        server.verify();
    }

    @Test
    void enviaSendMessageQuandoDealNaoTemImagem() {
        Deal deal = baseDeal().build();

        server.expect(requestTo("https://api.telegram.org/bot123:ABC-TOKEN/sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.chat_id").value("-1001234567890"))
                .andExpect(jsonPath("$.text").value("Legenda sem imagem"))
                .andExpect(jsonPath("$.parse_mode").value("MarkdownV2"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        client.sendDeal(deal, "Legenda sem imagem");

        server.verify();
    }
}
