package com.aatechsolutions.elgransazon.application.service;

import java.security.SecureRandom;
import java.util.Base64;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aatechsolutions.elgransazon.domain.repository.PasswordResetTokenRepository;
import com.aatechsolutions.elgransazon.domain.repository.CustomerRepository;
import com.aatechsolutions.elgransazon.domain.entity.PasswordResetToken;

/**
 * Service for password reset
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {
    
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final CustomerRepository customerRepository;
    private final EmailService emailService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Solicitar restablecimiento de contraseña
     */
    @Transactional
    public void requestPasswordReset(String email) {
        log.info("Password reset requested for email: {}", email);
        
        // Buscar cliente por email o username
        var customerOpt = customerRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(email, email);
        
        if (customerOpt.isPresent()) {
            var customer = customerOpt.get();
            var token = generateToken();

            // Eliminar tokens anteriores del cliente
            passwordResetTokenRepository.deleteByCustomer(customer);

            var resetToken = PasswordResetToken.builder()
                    .customer(customer)
                    .token(token)
                    .expiration(java.time.LocalDateTime.now().plusMinutes(15)) // Expira en 15 minutos
                    .used(false)
                    .build();

            passwordResetTokenRepository.save(resetToken);
            log.info("Password reset token generated for customer: {}", customer.getEmail());

            // Enviar el correo con el token
            emailService.sendPasswordResetEmail(customer.getEmail(), token);
        } else {
            log.warn("Password reset requested for non-existent email: {}", email);
            // Por seguridad, no revelamos si el email existe o no
        }
    }

    /**
     * Confirmar restablecimiento de contraseña
     */
    @Transactional
    public void confirmPasswordReset(String tokenHash, String newPassword) {
        log.info("Confirming password reset with token");
        
        // Validar contraseña
        validatePassword(newPassword);
        
        var tokenOpt = passwordResetTokenRepository.findByToken(tokenHash);
        
        if (tokenOpt.isEmpty()) {
            log.warn("Invalid password reset token");
            throw new IllegalArgumentException("Token inválido.");
        }
        
        var token = tokenOpt.get();
        
        if (token.getExpiration().isBefore(java.time.LocalDateTime.now())) {
            log.warn("Expired password reset token");
            throw new IllegalArgumentException("Token expirado.");
        }
        
        if (Boolean.TRUE.equals(token.getUsed())) {
            log.warn("Already used password reset token");
            throw new IllegalArgumentException("Token ya usado.");
        }
        
        var customer = token.getCustomer();
        customer.setPassword(passwordEncoder.encode(newPassword));
        customerRepository.save(customer);
        log.info("Password reset successfully for customer: {}", customer.getEmail());

        // Marcar token como usado y eliminarlo
        token.setUsed(true);
        passwordResetTokenRepository.delete(token);
    }

    private String generateToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
    
    private void validatePassword(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 6 caracteres.");
        }
        if (password.length() > 100) {
            throw new IllegalArgumentException("La contraseña es demasiado larga.");
        }
    }
}
