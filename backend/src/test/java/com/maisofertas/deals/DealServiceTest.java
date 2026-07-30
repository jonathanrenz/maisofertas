package com.maisofertas.deals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DealServiceTest {

    private final DealRepository repository = mock(DealRepository.class);
    private final DealService service = new DealService(repository, 7, "");

    private CreateDealRequest sampleRequest() {
        return new CreateDealRequest("Livro de autoconfiança", "https://amazon.com.br/dp/ABC123",
                "https://images.example/abc.jpg", BigDecimal.valueOf(39.90), BigDecimal.valueOf(59.90), Store.AMAZON);
    }

    @Test
    void bloqueiaCriacaoQuandoJaExisteDealParaMesmaUrlDentroDaJanela() {
        when(repository.existsByUrlAndCreatedAtAfter(anyString(), any(Instant.class))).thenReturn(true);

        assertThatThrownBy(() -> service.createDeal(sampleRequest(), DealSource.MANUAL))
                .isInstanceOf(DuplicateDealException.class);

        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void permiteCriacaoQuandoNaoHaDealRecenteParaAUrl() {
        when(repository.existsByUrlAndCreatedAtAfter(anyString(), any(Instant.class))).thenReturn(false);
        when(repository.save(any(Deal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Deal saved = service.createDeal(sampleRequest(), DealSource.MANUAL);

        assertThat(saved.getStatus()).isEqualTo(DealStatus.PENDING);
        assertThat(saved.getSource()).isEqualTo(DealSource.MANUAL);
        assertThat(saved.getStore()).isEqualTo(Store.AMAZON);
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void defaultStoreParaAmazonQuandoNaoInformado() {
        when(repository.existsByUrlAndCreatedAtAfter(anyString(), any(Instant.class))).thenReturn(false);
        when(repository.save(any(Deal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateDealRequest request = new CreateDealRequest("Produto", "https://amazon.com.br/dp/XYZ",
                null, BigDecimal.TEN, null, null);

        Deal saved = service.createDeal(request, DealSource.MANUAL);

        assertThat(saved.getStore()).isEqualTo(Store.AMAZON);
    }

    @Test
    void marcaComoPostedSoQuandoOsDoisCanaisConfirmam() {
        UUID id = UUID.randomUUID();
        Deal deal = Deal.builder()
                .id(id)
                .store(Store.AMAZON)
                .title("Produto")
                .url("https://amazon.com.br/dp/AAA")
                .price(BigDecimal.TEN)
                .source(DealSource.MANUAL)
                .status(DealStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        when(repository.findById(id)).thenReturn(Optional.of(deal));
        when(repository.save(any(Deal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.markTelegramPosted(id);
        assertThat(deal.getStatus()).isEqualTo(DealStatus.PENDING);
        assertThat(deal.getPostedTelegramAt()).isNotNull();

        service.markWhatsappPosted(id);
        assertThat(deal.getStatus()).isEqualTo(DealStatus.POSTED);
        assertThat(deal.getPostedWhatsappAt()).isNotNull();
    }

    @Test
    void findPendingDelegaParaRepositorio() {
        Deal deal = Deal.builder().id(UUID.randomUUID()).status(DealStatus.PENDING).build();
        when(repository.findByStatus(DealStatus.PENDING)).thenReturn(List.of(deal));

        List<Deal> pending = service.findPending();

        assertThat(pending).containsExactly(deal);
    }

    @Test
    void aplicaTagDeAfiliadoQuandoUrlNaoTemQueryString() {
        DealService withTag = new DealService(repository, 7, "maisoferta0e0-20");
        when(repository.existsByUrlAndCreatedAtAfter(anyString(), any(Instant.class))).thenReturn(false);
        when(repository.save(any(Deal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateDealRequest request = new CreateDealRequest("Produto", "https://amazon.com.br/dp/ABC123",
                null, BigDecimal.TEN, null, Store.AMAZON);

        Deal saved = withTag.createDeal(request, DealSource.MANUAL);

        assertThat(saved.getUrl()).isEqualTo("https://amazon.com.br/dp/ABC123?tag=maisoferta0e0-20");
    }

    @Test
    void aplicaTagDeAfiliadoComEComercialQuandoUrlJaTemQueryString() {
        DealService withTag = new DealService(repository, 7, "maisoferta0e0-20");
        when(repository.existsByUrlAndCreatedAtAfter(anyString(), any(Instant.class))).thenReturn(false);
        when(repository.save(any(Deal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateDealRequest request = new CreateDealRequest("Produto", "https://amazon.com.br/dp/ABC123?psc=1",
                null, BigDecimal.TEN, null, Store.AMAZON);

        Deal saved = withTag.createDeal(request, DealSource.MANUAL);

        assertThat(saved.getUrl()).isEqualTo("https://amazon.com.br/dp/ABC123?psc=1&tag=maisoferta0e0-20");
    }

    @Test
    void naoSobrescreveTagQuandoUrlJaTraiUmaExplicita() {
        DealService withTag = new DealService(repository, 7, "maisoferta0e0-20");
        when(repository.existsByUrlAndCreatedAtAfter(anyString(), any(Instant.class))).thenReturn(false);
        when(repository.save(any(Deal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateDealRequest request = new CreateDealRequest("Produto",
                "https://amazon.com.br/dp/ABC123?tag=outra-tag-20", null, BigDecimal.TEN, null, Store.AMAZON);

        Deal saved = withTag.createDeal(request, DealSource.MANUAL);

        assertThat(saved.getUrl()).isEqualTo("https://amazon.com.br/dp/ABC123?tag=outra-tag-20");
    }

    @Test
    void verificaDuplicataUsandoUrlComTagJaAplicada() {
        // Regressão: o dedup checava a URL crua (sem "tag=") mas salvava a URL com
        // a tag de afiliado aplicada. Como o repositório sempre guarda a URL com
        // tag, o check nunca batia e o mesmo deal era recriado a cada sync.
        DealService withTag = new DealService(repository, 7, "maisoferta0e0-20");
        String rawUrl = "https://amazon.com.br/dp/ABC123";
        String taggedUrl = "https://amazon.com.br/dp/ABC123?tag=maisoferta0e0-20";
        when(repository.existsByUrlAndCreatedAtAfter(eq(taggedUrl), any(Instant.class))).thenReturn(true);

        CreateDealRequest request = new CreateDealRequest("Produto", rawUrl, null, BigDecimal.TEN, null,
                Store.AMAZON);

        assertThatThrownBy(() -> withTag.createDeal(request, DealSource.CANOPY))
                .isInstanceOf(DuplicateDealException.class);
        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void naoAplicaTagQuandoNenhumaEstaConfigurada() {
        when(repository.existsByUrlAndCreatedAtAfter(anyString(), any(Instant.class))).thenReturn(false);
        when(repository.save(any(Deal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Deal saved = service.createDeal(sampleRequest(), DealSource.MANUAL);

        assertThat(saved.getUrl()).isEqualTo("https://amazon.com.br/dp/ABC123");
    }
}
