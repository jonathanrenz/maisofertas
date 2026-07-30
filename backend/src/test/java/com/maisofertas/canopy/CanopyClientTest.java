package com.maisofertas.canopy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Fixture da resposta baseada numa chamada real de teste em
 * {@code GET /api/amazon/deals?domain=BR} (plano free da Canopy), reduzida a
 * 2 resultados pra cobrir o caso completo e o caso com desconto/preço
 * original ausentes.
 */
class CanopyClientTest {

    private static final String RESPONSE_BODY = """
            {
              "data": {
                "amazonDeals": {
                  "productResults": {
                    "pageInfo": {"currentPage":1,"totalPages":17,"hasNextPage":true,"hasPrevPage":false,"totalResults":500},
                    "results": [
                      {
                        "title": "Whisky Buchanan's Deluxe 12 Anos 1L",
                        "url": "https://www.amazon.com.br/dp/B00MTFIL0U",
                        "asin": "B00MTFIL0U",
                        "price": {"symbol":"R$","value":134.9,"currency":"BRL","display":"R$134.9"},
                        "mainImageUrl": "https://m.media-amazon.com/images/I/518U8vf4L.jpg",
                        "dealId": "a32678fd",
                        "dealListPrice": {"symbol":"R$","value":199.9,"currency":"BRL","display":"R$199.9"},
                        "dealPercentOff": 32,
                        "dealType": "BEST_DEAL"
                      },
                      {
                        "title": "Produto sem desconto calculado",
                        "url": "https://www.amazon.com.br/dp/B0XXXXXXX1",
                        "asin": "B0XXXXXXX1",
                        "price": {"symbol":"R$","value":50.0,"currency":"BRL","display":"R$50.0"},
                        "mainImageUrl": "https://m.media-amazon.com/images/I/example.jpg",
                        "dealListPrice": null,
                        "dealPercentOff": null
                      }
                    ]
                  }
                }
              }
            }
            """;

    private MockRestServiceServer server;
    private CanopyClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CanopyClient(builder, "https://rest.canopyapi.co", "test-key", "BR");
    }

    @Test
    void buscaOfertasEMapeiaCamposDaRespostaReal() {
        server.expect(requestTo("https://rest.canopyapi.co/api/amazon/deals?domain=BR&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("API-KEY", "test-key"))
                .andRespond(withSuccess(RESPONSE_BODY, MediaType.APPLICATION_JSON));

        List<CanopyDeal> deals = client.fetchDeals(1);

        server.verify();
        assertThat(deals).hasSize(2);

        CanopyDeal first = deals.get(0);
        assertThat(first.asin()).isEqualTo("B00MTFIL0U");
        assertThat(first.title()).isEqualTo("Whisky Buchanan's Deluxe 12 Anos 1L");
        assertThat(first.url()).isEqualTo("https://www.amazon.com.br/dp/B00MTFIL0U");
        assertThat(first.imageUrl()).isEqualTo("https://m.media-amazon.com/images/I/518U8vf4L.jpg");
        assertThat(first.price()).isEqualByComparingTo(BigDecimal.valueOf(134.9));
        assertThat(first.originalPrice()).isEqualByComparingTo(BigDecimal.valueOf(199.9));
        assertThat(first.percentOff()).isEqualTo(32);

        CanopyDeal second = deals.get(1);
        assertThat(second.originalPrice()).isNull();
        assertThat(second.percentOff()).isZero();
    }

    @Test
    void retornaListaVaziaQuandoRespostaNaoTemDados() {
        server.expect(requestTo("https://rest.canopyapi.co/api/amazon/deals?domain=BR&page=2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"data\":null}", MediaType.APPLICATION_JSON));

        List<CanopyDeal> deals = client.fetchDeals(2);

        assertThat(deals).isEmpty();
    }
}
