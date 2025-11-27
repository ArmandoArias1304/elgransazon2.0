package com.aatechsolutions.elgransazon.domain.entity;

/**
 * Enum representing the status of a reservation
 */
public enum ReservationStatus {
    RESERVED("Reservado"),           // Reservada, esperando cliente
    OCCUPIED("Ocupada"),             // Cliente llegó y está en la mesa
    COMPLETED("Completada"),         // Cliente terminó y se fue
    CANCELLED("Cancelada"),          // Reservación cancelada
    NO_SHOW("No se presentó");       // Cliente no llegó

    private final String displayName;

    ReservationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get status by display name
     */
    public static ReservationStatus fromDisplayName(String displayName) {
        for (ReservationStatus status : values()) {
            if (status.displayName.equalsIgnoreCase(displayName)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown reservation status: " + displayName);
    }

    /**
     * Check if reservation is active (not completed, cancelled or no-show)
     */
    public boolean isActive() {
        return this == RESERVED || this == OCCUPIED;
    }

    /**
     * Check if reservation can be edited
     */
    public boolean isEditable() {
        return this == RESERVED;
    }

    /**
     * Check if reservation can be cancelled
     */
    public boolean isCancellable() {
        return this == RESERVED;
    }
}
