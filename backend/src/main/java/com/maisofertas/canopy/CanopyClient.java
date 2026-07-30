package com.maisofertas.canopy;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Busca ofertas Amazon via <a href="https://www.canopyapi.co">Canopy API</a>
 * (REST, endpoint {@code GET /api/amazon/deals}). Escolhida no lugar da
 * Creators API/PA-API porque não exige o gate de 10 vendas qualificadas nos
 * últimos 30 dias — só pede uma API key.
 */
@Component
public class CanopyClient {

    private final RestClient restClient;
    private final String domain;

    public CanopyClient(
            RestClient.Builder builder,
            @Value("${app.canopy.base-url}") String baseUrl,
            @Value("${app.canopy.api-key}") String apiKey,
            @Value("${app.canopy.domain}") String domain) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("API-KEY", apiKey)
                .build();
        this.domain = domain;
    }

    /**
     * @param page página de resultados (1-based, conforme a API da Canopy).
     */
    public List<CanopyDeal> fetchDeals(int page) {
        RawResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/amazon/deals")
                        .queryParam("domain", domain)
                        .queryParam("page", page)
                        .build())
                .retrieve()
                .body(RawResponse.class);

        if (response == null || response.data() == null) {
            return List.of();
        }
        return response.data().amazonDeals().productResults().results().stream()
                .map(CanopyClient::toDeal)
                .toList();
    }

    private static CanopyDeal toDeal(RawDeal raw) {
        return new CanopyDeal(
                raw.asin(),
                raw.title(),
                raw.url(),
                raw.mainImageUrl(),
                raw.price() != null ? raw.price().value() : null,
                raw.dealListPrice() != null ? raw.dealListPrice().value() : null,
                raw.dealPercentOff() != null ? raw.dealPercentOff() : 0);
    }

    record RawResponse(RawData data) {
    }

    record RawData(RawAmazonDeals amazonDeals) {
    }

    record RawAmazonDeals(RawProductResults productResults) {
    }

    record RawProductResults(List<RawDeal> results) {
    }

    record RawDeal(
            String asin,
            String title,
            String url,
            String mainImageUrl,
            RawMoney price,
            RawMoney dealListPrice,
            Integer dealPercentOff) {
    }

    record RawMoney(BigDecimal value) {
    }
}
