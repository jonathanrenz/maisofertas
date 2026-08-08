package com.maisofertas.deals;

public enum Store {
    AMAZON("Amazon"),
    SHOPEE("Shopee");

    private final String displayName;

    Store(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
