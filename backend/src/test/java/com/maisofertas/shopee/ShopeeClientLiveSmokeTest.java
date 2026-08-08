package com.maisofertas.shopee;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Chamada real à Shopee Affiliate Open API pra validar o contrato assumido em {@link ShopeeClient}
 * (ver aviso na classe) antes de ligar {@code SHOPEE_SYNC_ENABLED=true} em produção. Não roda no
 * {@code mvn test} normal (lane {@code live}, mesmo mecanismo da lane {@code eval}) — só quando
 * {@code SHOPEE_APP_ID}/{@code SHOPEE_SECRET} estão definidas e a tag é pedida explicitamente:
 *
 * <pre>mvn test -DexcludedGroups= -Dgroups=live -Dtest=ShopeeClientLiveSmokeTest</pre>
 *
 * Não escreve no Postgres nem publica em canal nenhum - só chama {@code ShopeeClient.fetchDeals}
 * diretamente (sem subir o contexto do Spring) e imprime o resultado mapeado pra conferência
 * visual, além de validar que os campos essenciais (título, preço, link) não vieram nulos - o que
 * aconteceria se a Shopee tiver renomeado algum campo do {@code productOfferV2}.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "SHOPEE_APP_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SHOPEE_SECRET", matches = ".+")
class ShopeeClientLiveSmokeTest {

    @Test
    void buscaOfertasReaisEValidaCamposEssenciais() {
        String baseUrl = System.getenv().getOrDefault("SHOPEE_BASE_URL",
                "https://open-api.affiliate.shopee.com.br/graphql");
        String appId = System.getenv("SHOPEE_APP_ID");
        String secret = System.getenv("SHOPEE_SECRET");
        String keyword = System.getenv().getOrDefault("SHOPEE_KEYWORD", "");
        int sortType = Integer.parseInt(System.getenv().getOrDefault("SHOPEE_SORT_TYPE", "5"));

        ShopeeClient client = new ShopeeClient(
                RestClient.builder(), new ObjectMapper(), baseUrl, appId, secret, keyword, sortType);

        List<ShopeeDeal> deals = client.fetchDeals(1, 5);

        System.out.println("=== ShopeeClient smoke test: " + deals.size() + " ofertas ===");
        deals.forEach(deal -> System.out.println(
                "itemId=%s | title=%s | price=%s | originalPrice=%s | percentOff=%d | url=%s | imageUrl=%s"
                        .formatted(deal.itemId(), deal.title(), deal.price(), deal.originalPrice(),
                                deal.percentOff(), deal.url(), deal.imageUrl())));

        assertThat(deals)
                .as("fetchDeals devolveu lista vazia - confira o WARN de log acima: schema mudou "
                        + "ou keyword/sortType não bateram com nenhum produto")
                .isNotEmpty();

        deals.forEach(deal -> {
            assertThat(deal.itemId()).as("itemId nulo em: %s", deal).isNotNull();
            assertThat(deal.title()).as("title nulo/vazio em: %s", deal).isNotBlank();
            assertThat(deal.url()).as("url nula/vazia em: %s", deal).isNotBlank();
            assertThat(deal.price()).as("price nulo ou <= 0 em: %s", deal).isNotNull()
                    .isGreaterThan(BigDecimal.ZERO);
        });
    }
}
