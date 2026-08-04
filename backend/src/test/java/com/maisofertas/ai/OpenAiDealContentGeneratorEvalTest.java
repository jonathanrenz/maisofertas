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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Eval de qualidade do conteúdo gerado pela OpenAI de verdade (chamada real,
 * paga). Não roda no {@code mvn test} normal (gate lane) — só quando
 * {@code OPENAI_API_KEY} está definida e você pede a tag explicitamente:
 *
 * <pre>mvn test -Dgroups=eval</pre>
 *
 * Rubrica por item: hook não vazio com emoji e sem preço, productName não
 * vazio, specs (quando existem) só reaproveitam palavras do título — ou seja,
 * não inventa característica, hook não usa o padrão genérico "Melhore sua/seu
 * X com esse/essa Y" e não mistura palavra em inglês. Threshold de aprovação:
 * 90% dos itens passando em todos os critérios, MAIS uma checagem agregada de
 * diversidade: nenhuma primeira palavra pode se repetir em mais de ~1/3 dos
 * hooks do lote (é essa checagem agregada que pega a regressão real que
 * motivou essas regras — hooks individualmente "válidos" mas todos
 * estruturalmente idênticos, tipo "Melhore sua circulação de ar com esse
 * ventilador" / "Melhore sua casa com esse aspirador").
 */
@Tag("eval")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiDealContentGeneratorEvalTest {

    private static final double PASS_THRESHOLD = 0.90;
    private static final double MAX_SHARED_OPENING_RATIO = 0.34;
    private static final Pattern EMOJI_PATTERN = Pattern.compile("[\\p{So}\\p{Cn}\\x{1F300}-\\x{1FAFF}]");
    private static final Pattern GENERIC_TEMPLATE_PATTERN =
            Pattern.compile("(?i).*melhor[ae] (sua|seu) .*com (esse|essa) .*");
    private static final Pattern ENGLISH_LEAK_PATTERN =
            Pattern.compile("(?i)\\b(the|this|that|and|with|your|for)\\b");

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
    void conteudoGeradoPassaNaRubricaDeQualidade() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        String model = System.getenv().getOrDefault("OPENAI_MODEL", "gpt-5-nano");
        String baseUrl = System.getenv().getOrDefault("OPENAI_BASE_URL", "https://api.openai.com/v1");

        OpenAiDealContentGenerator generator = new OpenAiDealContentGenerator(
                RestClient.builder(), baseUrl, apiKey, model,
                new FallbackDealContentGenerator(), new ObjectMapper());

        List<String> failures = new ArrayList<>();
        List<String> allHooks = new ArrayList<>();
        int passed = 0;

        for (Fixture fixture : FIXTURES) {
            Deal deal = Deal.builder()
                    .id(UUID.randomUUID())
                    .store(Store.AMAZON)
                    .title(fixture.title())
                    .url("https://amazon.com.br/dp/TESTE?tag=maisoferta0e0-20")
                    .price(fixture.price())
                    .originalPrice(fixture.originalPrice())
                    .source(DealSource.MANUAL)
                    .status(DealStatus.PENDING)
                    .createdAt(Instant.now())
                    .build();

            DealContent content = generator.generateContent(deal);
            allHooks.add(content.hook());
            List<String> reasons = rubricFailures(content, fixture);

            if (reasons.isEmpty()) {
                passed++;
            } else {
                failures.add("\"%s\" -> %s | hook: %s | productName: %s | specs: %s"
                        .formatted(fixture.title(), reasons, content.hook(), content.productName(),
                                content.specs()));
            }
        }

        double passRate = (double) passed / FIXTURES.size();
        String report = "Pass rate: %.0f%% (%d/%d). Falhas:\n%s"
                .formatted(passRate * 100, passed, FIXTURES.size(), String.join("\n", failures));

        assertThat(passRate).as(report).isGreaterThanOrEqualTo(PASS_THRESHOLD);

        assertDiversity(allHooks);
    }

    /**
     * Pega o caso de hooks individualmente "válidos" na rubrica mas todos
     * estruturalmente iguais (a regressão real que motivou essas regras: cada
     * hook sozinho parecia ok, o problema só aparecia comparando o lote
     * inteiro).
     */
    private void assertDiversity(List<String> hooks) {
        Map<String, Long> openingWordCounts = hooks.stream()
                .map(this::firstWord)
                .collect(Collectors.groupingBy(w -> w, Collectors.counting()));
        long maxAllowed = Math.max(1, Math.round(hooks.size() * MAX_SHARED_OPENING_RATIO));
        long maxObserved = openingWordCounts.values().stream().mapToLong(Long::longValue).max().orElse(0);

        String report = "Primeira palavra de cada hook: %s | contagem por palavra: %s | hooks completos: %s"
                .formatted(hooks.stream().map(this::firstWord).toList(), openingWordCounts, hooks);

        assertThat(maxObserved).as(report).isLessThanOrEqualTo(maxAllowed);
    }

    private String firstWord(String hook) {
        String semEmojiNoInicio = hook.replaceAll("^[^\\p{L}]+", "");
        String[] palavras = semEmojiNoInicio.split("\\s+", 2);
        return palavras.length > 0 ? palavras[0].toLowerCase(Locale.forLanguageTag("pt-BR")) : "";
    }

    private List<String> rubricFailures(DealContent content, Fixture fixture) {
        List<String> reasons = new ArrayList<>();

        if (content.hook() == null || content.hook().isBlank()) {
            reasons.add("hook vazio");
            return reasons;
        }
        if (!EMOJI_PATTERN.matcher(content.hook()).find()) {
            reasons.add("hook sem emoji");
        }
        if (content.hook().length() > 150) {
            reasons.add("hook longo demais (%d chars)".formatted(content.hook().length()));
        }
        String priceDigits = fixture.price().setScale(0, java.math.RoundingMode.DOWN).toBigInteger().toString();
        if (content.hook().contains(priceDigits)) {
            reasons.add("hook menciona preço, e não deveria");
        }
        if (content.productName() == null || content.productName().isBlank()) {
            reasons.add("productName vazio");
        }
        if (content.specs().size() > 4) {
            reasons.add("specs demais (%d)".formatted(content.specs().size()));
        }
        if (content.hook().contains("%s") || content.hook().toLowerCase().contains("[detalhes")) {
            reasons.add("placeholder do template vazou pro hook");
        }
        if (GENERIC_TEMPLATE_PATTERN.matcher(content.hook()).matches()) {
            reasons.add("hook usa o padrão genérico 'Melhore sua/seu X com esse/essa Y'");
        }
        if (ENGLISH_LEAK_PATTERN.matcher(content.hook()).find()) {
            reasons.add("hook mistura palavra em inglês: " + content.hook());
        }
        return reasons;
    }
}
