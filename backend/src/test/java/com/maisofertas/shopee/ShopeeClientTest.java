package com.maisofertas.shopee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
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
import tools.jackson.databind.ObjectMapper;

/**
 * Fixture montada a partir da documentação pública da Shopee Affiliate Open API (não de uma
 * chamada real - ver aviso em {@link ShopeeClient}). Cobre o parsing do envelope GraphQL, o
 * cálculo de preço original a partir de {@code priceDiscountRate} e o header de autenticação.
 */
class ShopeeClientTest {

    private static final String RESPONSE_BODY = """
            {
              "data": {
                "productOfferV2": {
                  "nodes": [
                    {
                      "itemId": 123456789,
                      "productName": "Fone de Ouvido Bluetooth TWS",
                      "productLink": "https://shopee.com.br/product/1/123456789",
                      "offerLink": "https://s.shopee.com.br/abc123XYZ",
                      "imageUrl": "https://cf.shopee.com.br/file/exemplo.jpg",
                      "priceMin": "65.00",
                      "priceDiscountRate": "35"
                    },
                    {
                      "itemId": 987654321,
                      "productName": "Produto sem desconto calculado",
                      "productLink": "https://shopee.com.br/product/1/987654321",
                      "offerLink": null,
                      "imageUrl": null,
                      "priceMin": "50.00",
                      "priceDiscountRate": null
                    }
                  ]
                }
              }
            }
            """;

    private MockRestServiceServer server;
    private ShopeeClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ShopeeClient(builder, new ObjectMapper(),
                "https://open-api.affiliate.shopee.com.br/graphql", "test-app-id", "test-secret", "", 5);
    }

    @Test
    void buscaOfertasEMapeiaCamposDaRespostaDocumentada() {
        server.expect(requestTo("https://open-api.affiliate.shopee.com.br/graphql"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", matchesPattern(
                        "^SHA256 Credential=test-app-id, Timestamp=\\d+, Signature=[0-9a-f]{64}$")))
                .andRespond(withSuccess(RESPONSE_BODY, MediaType.APPLICATION_JSON));

        List<ShopeeDeal> deals = client.fetchDeals(1, 20);

        server.verify();
        assertThat(deals).hasSize(2);

        ShopeeDeal first = deals.get(0);
        assertThat(first.itemId()).isEqualTo(123456789L);
        assertThat(first.title()).isEqualTo("Fone de Ouvido Bluetooth TWS");
        assertThat(first.url()).isEqualTo("https://s.shopee.com.br/abc123XYZ");
        assertThat(first.imageUrl()).isEqualTo("https://cf.shopee.com.br/file/exemplo.jpg");
        assertThat(first.price()).isEqualByComparingTo(BigDecimal.valueOf(65.00));
        assertThat(first.originalPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(first.percentOff()).isEqualTo(35);

        ShopeeDeal second = deals.get(1);
        assertThat(second.url())
                .as("sem offerLink, a url deve ficar nula (não cair pro productLink sem comissão)")
                .isNull();
        assertThat(second.originalPrice()).isNull();
        assertThat(second.percentOff()).isZero();
    }

    @Test
    void retornaListaVaziaQuandoRespostaNaoTemDados() {
        server.expect(requestTo("https://open-api.affiliate.shopee.com.br/graphql"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"data\":null}", MediaType.APPLICATION_JSON));

        List<ShopeeDeal> deals = client.fetchDeals(1, 20);

        assertThat(deals).isEmpty();
    }
}
