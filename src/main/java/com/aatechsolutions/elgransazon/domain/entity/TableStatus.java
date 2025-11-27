package com.aatechsolutions.elgransazon.domain.entity;

/**
 * Enum representing the status of a restaurant table
 */
public enum TableStatus {
    AVAILABLE("Disponible"),
    OCCUPIED("Ocupada"),
    RESERVED("Reservada"),
    OUT_OF_SERVICE("Fuera de Servicio");

    private final String displayName;

    TableStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get status by display name
     */
    public static TableStatus fromDisplayName(String displayName) {
        for (TableStatus status : values()) {
            if (status.displayName.equalsIgnoreCase(displayName)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown table status: " + displayName);
    }
}
