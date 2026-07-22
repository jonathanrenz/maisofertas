package com.maisofertas.deals;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateDealException extends RuntimeException {

    public DuplicateDealException(String url) {
        super("Já existe uma oferta postada para essa URL na janela de dedup: " + url);
    }
}
