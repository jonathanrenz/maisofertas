package com.maisofertas.whatsapp;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
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

class EvolutionApiClientTest {

    private MockRestServiceServer server;
    private EvolutionApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new EvolutionApiClient(builder, "http://evolution.local", "maisofertas", "secret-key", "1234@g.us");
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
    void enviaSendMediaQuandoDealTemImagem() {
        Deal deal = baseDeal().imageUrl("https://images.example/produto.jpg").build();

        server.expect(requestTo("http://evolution.local/message/sendMedia/maisofertas"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", "secret-key"))
                .andExpect(jsonPath("$.number").value("1234@g.us"))
                .andExpect(jsonPath("$.mediatype").value("image"))
                .andExpect(jsonPath("$.media").value("https://images.example/produto.jpg"))
                .andExpect(jsonPath("$.caption").value("Legenda de teste"))
                .andRespond(withSuccess("{\"status\":\"success\"}", MediaType.APPLICATION_JSON));

        client.sendDeal(deal, "Legenda de teste");

        server.verify();
    }

    @Test
    void enviaSendTextQuandoDealNaoTemImagem() {
        Deal deal = baseDeal().build();

        server.expect(requestTo("http://evolution.local/message/sendText/maisofertas"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.number").value("1234@g.us"))
                .andExpect(jsonPath("$.text").value("Legenda sem imagem"))
                .andRespond(withSuccess("{\"status\":\"success\"}", MediaType.APPLICATION_JSON));

        client.sendDeal(deal, "Legenda sem imagem");

        server.verify();
    }
}
