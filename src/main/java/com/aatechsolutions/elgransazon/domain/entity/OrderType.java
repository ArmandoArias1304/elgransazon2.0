package com.aatechsolutions.elgransazon.domain.entity;

/**
 * Enum representing the type of order
 */
public enum OrderType {
    DINE_IN("Para comer aquí"),
    TAKEOUT("Para llevar"),
    DELIVERY("Entrega a domicilio");

    private final String displayName;

    OrderType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
