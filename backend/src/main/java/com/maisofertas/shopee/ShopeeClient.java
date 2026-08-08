package com.maisofertas.shopee;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Busca ofertas Shopee via <a href="https://affiliate.shopee.com.br/open_api">Shopee
 * Affiliate Open API</a> (GraphQL, {@code POST /graphql}, query {@code productOfferV2}).
 *
 * <p>Contrato validado com uma chamada real em 08/ago/2026 via {@code ShopeeClientLiveSmokeTest}
 * (lane {@code live}, fora do {@code mvn test} comum): {@code priceMin}, {@code priceDiscountRate},
 * {@code offerLink} e a fórmula de {@code originalPrice} bateram exatamente com o que a API
 * devolveu. Se a Shopee renomear algum campo no futuro, o sintoma mais provável é {@code fetchDeals}
 * devolver lista vazia (ver {@link #fetchDeals}, que loga WARN nesse caso) ou deals com
 * {@code originalPrice} nulo mesmo em oferta com desconto - rode a lane {@code live} de novo pra
 * confirmar antes de investigar mais fundo.
 */
@Component
public class ShopeeClient {

    private static final Logger log = LoggerFactory.getLogger(ShopeeClient.class);

    private static final String QUERY = """
            query($keyword: String, $sortType: Int, $page: Int, $limit: Int) {
              productOfferV2(keyword: $keyword, sortType: $sortType, page: $page, limit: $limit) {
                nodes {
                  itemId
                  productName
                  productLink
                  offerLink
                  imageUrl
                  priceMin
                  priceDiscountRate
                }
              }
            }
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String appId;
    private final String secret;
    private final String keyword;
    private final int sortType;

    public ShopeeClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${app.shopee.base-url}") String baseUrl,
            @Value("${app.shopee.app-id}") String appId,
            @Value("${app.shopee.secret}") String secret,
            @Value("${app.shopee.keyword:}") String keyword,
            @Value("${app.shopee.sort-type:5}") int sortType) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.appId = appId;
        this.secret = secret;
        this.keyword = keyword;
        this.sortType = sortType;
    }

    /**
     * @param page  página de resultados (1-based).
     * @param limit itens por página (1-500 conforme a API da Shopee).
     */
    public List<ShopeeDeal> fetchDeals(int page, int limit) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("keyword", keyword);
        variables.put("sortType", sortType);
        variables.put("page", page);
        variables.put("limit", limit);
        String payload = objectMapper.writeValueAsString(new GraphQlRequest(QUERY, variables));

        GraphQlResponse response = restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", authorizationHeader(payload))
                .body(payload)
                .retrieve()
                .body(GraphQlResponse.class);

        if (response == null || response.data() == null || response.data().productOfferV2() == null) {
            log.warn("Resposta da Shopee Affiliate API sem 'data.productOfferV2' - formato inesperado, "
                    + "confira se o schema da API mudou. Resposta: {}", response);
            return List.of();
        }
        List<RawNode> nodes = response.data().productOfferV2().nodes();
        return nodes == null ? List.of() : nodes.stream().map(ShopeeClient::toDeal).toList();
    }

    private String authorizationHeader(String payload) {
        long timestamp = Instant.now().getEpochSecond();
        String signature = sha256Hex(appId + timestamp + payload + secret);
        return "SHA256 Credential=%s, Timestamp=%d, Signature=%s".formatted(appId, timestamp, signature);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível na JVM", e);
        }
    }

    private static ShopeeDeal toDeal(RawNode raw) {
        BigDecimal price = parseDecimal(raw.priceMin());
        Integer percentOff = parseInt(raw.priceDiscountRate());
        BigDecimal originalPrice = computeOriginalPrice(price, percentOff);
        String url = raw.offerLink() != null && !raw.offerLink().isBlank() ? raw.offerLink() : raw.productLink();
        return new ShopeeDeal(
                raw.itemId(),
                raw.productName(),
                url,
                raw.imageUrl(),
                price,
                originalPrice,
                percentOff != null ? percentOff : 0);
    }

    /**
     * A API devolve {@code priceMin}/{@code priceDiscountRate}, mas não o preço "de" direto -
     * a fórmula abaixo (documentada pela própria Shopee) reconstrói o preço original a partir do
     * desconto: {@code original = atual / (1 - desconto/100)}. Sem desconto (ou campo ausente),
     * fica {@code null}, igual ao comportamento da Canopy quando falta o preço original.
     */
    private static BigDecimal computeOriginalPrice(BigDecimal price, Integer percentOff) {
        if (price == null || percentOff == null || percentOff <= 0 || percentOff >= 100) {
            return null;
        }
        BigDecimal remaining = BigDecimal.ONE.subtract(BigDecimal.valueOf(percentOff)
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
        return price.divide(remaining, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("Valor de preço inesperado da Shopee API: '{}'", value);
            return null;
        }
    }

    private static Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value).intValue();
        } catch (NumberFormatException e) {
            log.warn("Valor de desconto inesperado da Shopee API: '{}'", value);
            return null;
        }
    }

    record GraphQlRequest(String query, Map<String, Object> variables) {
    }

    record GraphQlResponse(GraphQlData data) {
    }

    record GraphQlData(ProductOfferV2 productOfferV2) {
    }

    record ProductOfferV2(List<RawNode> nodes) {
    }

    record RawNode(
            Long itemId,
            String productName,
            String productLink,
            String offerLink,
            String imageUrl,
            String priceMin,
            String priceDiscountRate) {
    }
}
