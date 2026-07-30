package com.maisofertas.amazon;

import com.maisofertas.deals.CreateDealRequest;
import com.maisofertas.deals.Deal;
import com.maisofertas.deals.DealService;
import com.maisofertas.deals.DealSource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fase 0: entrada manual de ofertas, no mesmo formato que a Fase 1 (Canopy
 * API) preenche automaticamente via
 * {@link com.maisofertas.canopy.CanopySyncScheduler}.
 */
@RestController
@RequestMapping("/deals")
public class AmazonController {

    private final DealService dealService;

    public AmazonController(DealService dealService) {
        this.dealService = dealService;
    }

    @PostMapping("/manual")
    public ResponseEntity<Deal> createManualDeal(@Valid @RequestBody CreateDealRequest request) {
        Deal deal = dealService.createDeal(request, DealSource.MANUAL);
        return ResponseEntity.status(HttpStatus.CREATED).body(deal);
    }
}
