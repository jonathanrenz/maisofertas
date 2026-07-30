package com.maisofertas.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.maisofertas.deals.Deal;
import com.maisofertas.deals.DealSource;
import com.maisofertas.deals.DealStatus;
import com.maisofertas.deals.Store;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

/**
 * Eval de qualidade da legenda gerada pela OpenAI de verdade (chamada real,
 * paga). Não roda no {@code mvn test} normal (gate lane) — só quando
 * {@code OPENAI_API_KEY} está definida e você pede a tag explicitamente:
 *
 * <pre>mvn test -Dgroups=eval</pre>
 *
 * Rubrica por item: tem emoji, menciona o preço, tamanho cabe no limite de
 * legenda do Telegram/WhatsApp, não deixa placeholder do template vazando.
 * Threshold de aprovação: 90% dos itens passando em todos os critérios.
 */
@Tag("eval")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiCaptionGeneratorEvalTest {

    private static final double PASS_THRESHOLD = 0.90;
    private static final Pattern EMOJI_PATTERN = Pattern.compile("[\\p{So}\\p{Cn}\\x{1F300}-\\x{1FAFF}]");

    private record Fixture(String title, BigDecimal price, BigDecimal originalPrice) {
    }

    private static final List<Fixture> FIXTURES = List.of(
            new Fixture("Livro Pai Rico, Pai Pobre", bd(29.90), bd(49.90)),
            new Fixture("Fone de Ouvido Bluetooth JBL Tune 510BT", bd(149.00), bd(229.00)),
            new Fixture("Echo Dot 5ª Geração com Alexa", bd(219.00), bd(349.00)),
            new Fixture("Kindle 11ª Geração 16GB", bd(379.00), bd(499.00)),
            new Fixture("Cadeira Gamer Ergonômica", bd(899.00), bd(1299.00)),
            new Fixture("Panela de Pressão Elétrica 5L", bd(249.90), null),
            new Fixture("Mochila para Notebook Antifurto", bd(89.90), bd(139.90)),
            new Fixture("Smartwatch Amazfit Bip 5", bd(199.00), bd(299.00)),
            new Fixture("Livro O Poder do Hábito", bd(24.90), bd(39.90)),
            new Fixture("Carregador Portátil 20000mAh", bd(79.90), null),
            new Fixture("Air Fryer 4L Digital", bd(259.00), bd(399.00)),
            new Fixture("Kit 3 Camisetas Básicas Masculinas", bd(69.90), bd(99.90)));

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    @Test
    void legendasGeradasPassamNaRubricaDeQualidade() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        String model = System.getenv().getOrDefault("OPENAI_MODEL", "gpt-5-nano");
        String baseUrl = System.getenv().getOrDefault("OPENAI_BASE_URL", "https://api.openai.com/v1");

        OpenAiCaptionGenerator generator = new OpenAiCaptionGenerator(
                RestClient.builder(), baseUrl, apiKey, model, new FallbackCaptionGenerator());

        List<String> failures = new ArrayList<>();
        int passed = 0;

        for (Fixture fixture : FIXTURES) {
            String url = "https://amazon.com.br/dp/TESTE?tag=maisoferta0e0-20";
            Deal deal = Deal.builder()
                    .id(UUID.randomUUID())
                    .store(Store.AMAZON)
                    .title(fixture.title())
                    .url(url)
                    .price(fixture.price())
                    .originalPrice(fixture.originalPrice())
                    .source(DealSource.MANUAL)
                    .status(DealStatus.PENDING)
                    .createdAt(Instant.now())
                    .build();

            String caption = generator.generateCaption(deal);
            List<String> reasons = rubricFailures(caption, fixture, url);

            if (reasons.isEmpty()) {
                passed++;
            } else {
                failures.add("\"%s\" -> %s | legenda: %s".formatted(fixture.title(), reasons, caption));
            }
        }

        double passRate = (double) passed / FIXTURES.size();
        String report = "Pass rate: %.0f%% (%d/%d). Falhas:\n%s"
                .formatted(passRate * 100, passed, FIXTURES.size(), String.join("\n", failures));

        assertThat(passRate).as(report).isGreaterThanOrEqualTo(PASS_THRESHOLD);
    }

    private List<String> rubricFailures(String caption, Fixture fixture, String url) {
        List<String> reasons = new ArrayList<>();

        if (caption == null || caption.isBlank()) {
            reasons.add("legenda vazia");
            return reasons;
        }
        if (!caption.contains(url)) {
            reasons.add("não contém o link de afiliado (" + url + ")");
        }
        if (!EMOJI_PATTERN.matcher(caption).find()) {
            reasons.add("sem emoji");
        }
        if (caption.length() < 15 || caption.length() > 1024) {
            reasons.add("tamanho fora do limite (%d chars)".formatted(caption.length()));
        }
        String priceDigits = fixture.price().setScale(0, java.math.RoundingMode.DOWN).toBigInteger().toString();
        if (!caption.contains(priceDigits)) {
            reasons.add("não menciona o preço (" + priceDigits + ")");
        }
        if (caption.contains("%s") || caption.toLowerCase().contains("[detalhes")) {
            reasons.add("placeholder do template vazou pra legenda");
        }
        return reasons;
    }
}
