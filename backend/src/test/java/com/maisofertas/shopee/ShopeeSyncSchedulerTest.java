package com.maisofertas.shopee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maisofertas.deals.CreateDealRequest;
import com.maisofertas.deals.DealService;
import com.maisofertas.deals.DealSource;
import com.maisofertas.deals.DuplicateDealException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ShopeeSyncSchedulerTest {

    private final ShopeeClient shopeeClient = mock(ShopeeClient.class);
    private final DealService dealService = mock(DealService.class);

    private ShopeeDeal deal(long itemId, BigDecimal price, BigDecimal originalPrice, int percentOff) {
        return new ShopeeDeal(itemId, "Produto " + itemId, "https://s.shopee.com.br/" + itemId,
                "https://images.example/" + itemId + ".jpg", price, originalPrice, percentOff);
    }

    @Test
    void criaDealApenasParaOfertasComDescontoAcimaDoMinimo() {
        ShopeeSyncScheduler scheduler = new ShopeeSyncScheduler(shopeeClient, dealService, 1, 20, 20);
        when(shopeeClient.fetchDeals(1, 20)).thenReturn(List.of(
                deal(1L, BigDecimal.TEN, BigDecimal.valueOf(20), 50),
                deal(2L, BigDecimal.TEN, BigDecimal.valueOf(11), 9)));

        scheduler.syncDeals();

        ArgumentCaptor<CreateDealRequest> captor = ArgumentCaptor.forClass(CreateDealRequest.class);
        verify(dealService, times(1)).createDeal(captor.capture(), eq(DealSource.SHOPEE));
        assertThat(captor.getValue().url()).isEqualTo("https://s.shopee.com.br/1");
        assertThat(captor.getValue().price()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(captor.getValue().originalPrice()).isEqualByComparingTo(BigDecimal.valueOf(20));
    }

    @Test
    void ignoraOfertaComTituloVazioOuPrecoAusente() {
        ShopeeSyncScheduler scheduler = new ShopeeSyncScheduler(shopeeClient, dealService, 1, 20, 0);
        when(shopeeClient.fetchDeals(1, 20)).thenReturn(List.of(
                new ShopeeDeal(1L, "", "https://s.shopee.com.br/1", null, BigDecimal.TEN, null, 10),
                new ShopeeDeal(2L, "Produto", "https://s.shopee.com.br/2", null, null, null, 10)));

        scheduler.syncDeals();

        verify(dealService, never()).createDeal(any(), any());
    }

    @Test
    void ignoraOfertaSemLinkDeAfiliadoParaNaoPublicarDealSemComissao() {
        ShopeeSyncScheduler scheduler = new ShopeeSyncScheduler(shopeeClient, dealService, 1, 20, 0);
        when(shopeeClient.fetchDeals(1, 20)).thenReturn(List.of(
                new ShopeeDeal(1L, "Produto sem offerLink", null, null, BigDecimal.TEN, null, 10)));

        scheduler.syncDeals();

        verify(dealService, never()).createDeal(any(), any());
    }

    @Test
    void naoPropagaExcecaoQuandoDealJaExisteEContinuaOsProximos() {
        ShopeeSyncScheduler scheduler = new ShopeeSyncScheduler(shopeeClient, dealService, 1, 20, 0);
        when(shopeeClient.fetchDeals(1, 20)).thenReturn(List.of(
                deal(1L, BigDecimal.TEN, null, 10),
                deal(2L, BigDecimal.TEN, null, 10)));
        when(dealService.createDeal(any(), eq(DealSource.SHOPEE)))
                .thenThrow(new DuplicateDealException("https://s.shopee.com.br/1"))
                .thenReturn(null);

        scheduler.syncDeals();

        verify(dealService, times(2)).createDeal(any(), eq(DealSource.SHOPEE));
    }

    @Test
    void buscaVariasPaginasConformeConfigurado() {
        ShopeeSyncScheduler scheduler = new ShopeeSyncScheduler(shopeeClient, dealService, 3, 20, 0);
        when(shopeeClient.fetchDeals(anyInt(), eq(20))).thenReturn(List.of());

        scheduler.syncDeals();

        verify(shopeeClient).fetchDeals(1, 20);
        verify(shopeeClient).fetchDeals(2, 20);
        verify(shopeeClient).fetchDeals(3, 20);
    }

    @Test
    void paraDeBuscarProximasPaginasSeUmaFalhar() {
        ShopeeSyncScheduler scheduler = new ShopeeSyncScheduler(shopeeClient, dealService, 3, 20, 0);
        when(shopeeClient.fetchDeals(1, 20)).thenThrow(new RuntimeException("falha de rede"));

        scheduler.syncDeals();

        verify(shopeeClient).fetchDeals(1, 20);
        verify(shopeeClient, never()).fetchDeals(2, 20);
        verify(shopeeClient, never()).fetchDeals(3, 20);
    }
}
