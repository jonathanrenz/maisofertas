package com.maisofertas.canopy;

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

class CanopySyncSchedulerTest {

    private final CanopyClient canopyClient = mock(CanopyClient.class);
    private final DealService dealService = mock(DealService.class);

    private CanopyDeal deal(String asin, BigDecimal price, BigDecimal originalPrice, int percentOff) {
        return new CanopyDeal(asin, "Produto " + asin, "https://www.amazon.com.br/dp/" + asin,
                "https://images.example/" + asin + ".jpg", price, originalPrice, percentOff);
    }

    @Test
    void criaDealApenasParaOfertasComDescontoAcimaDoMinimo() {
        CanopySyncScheduler scheduler = new CanopySyncScheduler(canopyClient, dealService, 1, 20);
        when(canopyClient.fetchDeals(1)).thenReturn(List.of(
                deal("A1", BigDecimal.TEN, BigDecimal.valueOf(20), 50),
                deal("A2", BigDecimal.TEN, BigDecimal.valueOf(11), 9)));

        scheduler.syncDeals();

        ArgumentCaptor<CreateDealRequest> captor = ArgumentCaptor.forClass(CreateDealRequest.class);
        verify(dealService, times(1)).createDeal(captor.capture(), eq(DealSource.CANOPY));
        assertThat(captor.getValue().url()).isEqualTo("https://www.amazon.com.br/dp/A1");
        assertThat(captor.getValue().price()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(captor.getValue().originalPrice()).isEqualByComparingTo(BigDecimal.valueOf(20));
    }

    @Test
    void ignoraOfertaComTituloVazioOuPrecoAusente() {
        CanopySyncScheduler scheduler = new CanopySyncScheduler(canopyClient, dealService, 1, 0);
        when(canopyClient.fetchDeals(1)).thenReturn(List.of(
                new CanopyDeal("A1", "", "https://www.amazon.com.br/dp/A1", null, BigDecimal.TEN, null, 10),
                new CanopyDeal("A2", "Produto", "https://www.amazon.com.br/dp/A2", null, null, null, 10)));

        scheduler.syncDeals();

        verify(dealService, never()).createDeal(any(), any());
    }

    @Test
    void naoPropagaExcecaoQuandoDealJaExisteEContinuaOsProximos() {
        CanopySyncScheduler scheduler = new CanopySyncScheduler(canopyClient, dealService, 1, 0);
        when(canopyClient.fetchDeals(1)).thenReturn(List.of(
                deal("A1", BigDecimal.TEN, null, 10),
                deal("A2", BigDecimal.TEN, null, 10)));
        when(dealService.createDeal(any(), eq(DealSource.CANOPY)))
                .thenThrow(new DuplicateDealException("https://www.amazon.com.br/dp/A1"))
                .thenReturn(null);

        scheduler.syncDeals();

        verify(dealService, times(2)).createDeal(any(), eq(DealSource.CANOPY));
    }

    @Test
    void buscaVariasPaginasConformeConfigurado() {
        CanopySyncScheduler scheduler = new CanopySyncScheduler(canopyClient, dealService, 3, 0);
        when(canopyClient.fetchDeals(anyInt())).thenReturn(List.of());

        scheduler.syncDeals();

        verify(canopyClient).fetchDeals(1);
        verify(canopyClient).fetchDeals(2);
        verify(canopyClient).fetchDeals(3);
    }

    @Test
    void paraDeBuscarProximasPaginasSeUmaFalhar() {
        CanopySyncScheduler scheduler = new CanopySyncScheduler(canopyClient, dealService, 3, 0);
        when(canopyClient.fetchDeals(1)).thenThrow(new RuntimeException("falha de rede"));

        scheduler.syncDeals();

        verify(canopyClient).fetchDeals(1);
        verify(canopyClient, never()).fetchDeals(2);
        verify(canopyClient, never()).fetchDeals(3);
    }
}
