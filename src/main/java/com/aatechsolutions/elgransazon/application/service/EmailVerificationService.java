package com.aatechsolutions.elgransazon.application.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aatechsolutions.elgransazon.domain.entity.EmailVerificationToken;
import com.aatechsolutions.elgransazon.domain.repository.EmailVerificationTokenRepository;
import com.aatechsolutions.elgransazon.domain.entity.Customer;
import com.aatechsolutions.elgransazon.domain.repository.CustomerRepository;

/**
 * Service for email verification
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private static final int TOKEN_BYTES = 48; // 64 chars aprox Base64 URL
    private static final int EXP_MINUTES = 15; // Duración del token en minutos

    private final CustomerRepository customerRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Genera o reutiliza un token vigente para el cliente.
     * 
     * @param customer Cliente destino.
     * @return true si se generó y envió un NUEVO token, false si ya existía uno
     *         vigente y no se reenviò.
     */
    @Transactional
    public boolean createOrReuseToken(Customer customer) {
        log.info("Creating or reusing verification token for customer: {}", customer.getEmail());
        
        var existingOpt = emailVerificationTokenRepository.findByCustomer(customer);
        if (existingOpt.isPresent()) {
            EmailVerificationToken existing = existingOpt.get();
            if (existing.getExpiration().isAfter(LocalDateTime.now())) {
                log.info("Valid token already exists for customer: {}", customer.getEmail());
                // Token vigente: no reenviar
                return false;
            }
            // Expirado: eliminar para reemplazar
            log.info("Token expired, deleting old token for customer: {}", customer.getEmail());
            emailVerificationTokenRepository.delete(existing);
        }

        // Generar nuevo token
        String token = generateSecureToken();
        EmailVerificationToken evt = EmailVerificationToken.builder()
                .customer(customer)
                .token(token)
                .expiration(LocalDateTime.now().plusMinutes(EXP_MINUTES))
                .build();
        emailVerificationTokenRepository.save(evt);

        log.info("New verification token generated for customer: {}", customer.getEmail());

        // Enviar email
        emailService.sendEmailVerification(customer.getEmail(), token);
        return true;
    }

    /**
     * Enviar email de verificación a un cliente por su email
     */
    public void sendVerificationEmail(String email) {
        log.info("Attempting to send verification email to: {}", email);
        customerRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(email, email)
                .ifPresent(this::createOrReuseToken);
    }

    /**
     * Verificar email usando el token
     */
    @Transactional
    public void verifyEmail(String token) {
        log.info("Verifying email with token");
        
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token de verificación no proporcionado.");
        }

        EmailVerificationToken evt = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token de verificación inválido o expirado."));

        if (evt.getExpiration().isBefore(LocalDateTime.now())) {
            log.warn("Token expired for customer: {}", evt.getCustomer().getEmail());
            throw new IllegalArgumentException("Token de verificación inválido o expirado.");
        }

        Customer customer = evt.getCustomer();
        if (Boolean.TRUE.equals(customer.getEmailVerified())) {
            log.info("Email already verified for customer: {}", customer.getEmail());
            // Ya verificado: eliminar token redundante
            emailVerificationTokenRepository.delete(evt);
            return;
        }

        customer.setEmailVerified(true);
        customerRepository.save(customer);
        log.info("Email verified successfully for customer: {}", customer.getEmail());

        // Consumido: eliminar token
        emailVerificationTokenRepository.delete(evt);
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
