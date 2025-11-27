package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Customer entity representing registered customers in the system
 * Customers can place TAKEOUT and DELIVERY orders
 */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idCustomer"})
@ToString(exclude = {"password"})
public class Customer implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_customer")
    private Long idCustomer;

    @NotBlank(message = "El nombre completo es requerido")
    @Size(min = 2, max = 200, message = "El nombre debe tener entre 2 y 200 caracteres")
    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @NotBlank(message = "El nombre de usuario es requerido")
    @Size(min = 3, max = 50, message = "El nombre de usuario debe tener entre 3 y 50 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "El nombre de usuario solo puede contener letras, números y guión bajo")
    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @NotBlank(message = "El correo electrónico es requerido")
    @Email(message = "Formato de correo inválido")
    @Size(max = 200, message = "El correo no puede exceder 200 caracteres")
    @Column(name = "email", nullable = false, unique = true, length = 200)
    private String email;

    @NotBlank(message = "El teléfono es requerido")
    @Pattern(regexp = "^[+]?[0-9\\-\\s()]{7,20}$", message = "Formato de teléfono inválido")
    @Column(name = "phone", nullable = false, unique = true, length = 20)
    private String phone;

    @Size(max = 500, message = "La dirección no puede exceder 500 caracteres")
    @Column(name = "address", length = 500)
    private String address;

    @NotBlank(message = "La contraseña es requerida")
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    @Column(name = "password", nullable = false, length = 200)
    private String password; // BCrypt hashed

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    // ========== Timestamps ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_access")
    private LocalDateTime lastAccess;

    // ========== Lifecycle Callbacks ==========

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.active == null) {
            this.active = true;
        }
        if (this.emailVerified == null) {
            this.emailVerified = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ========== Business Methods ==========

    /**
     * Check if customer is active
     */
    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    /**
     * Update last access timestamp
     */
    public void updateLastAccess() {
        this.lastAccess = LocalDateTime.now();
    }
}
