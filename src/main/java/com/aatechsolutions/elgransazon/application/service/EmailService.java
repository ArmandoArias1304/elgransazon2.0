package com.aatechsolutions.elgransazon.application.service;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;

/**
 * Email service using SendGrid
 */
@Service
@Slf4j
public class EmailService {

    @Value("${spring.email.password}")
    private String sendGridApiKey;

    @Value("${mail.from.email}")
    private String fromEmail;

    @Value("${mail.from.name}")
    private String fromName;

    @Value("${app.protocol}")
    private String appProtocol;

    @Value("${app.domain}")
    private String appDomain;

    @Value("${app.port}")
    private String appPort;

    /**
     * Build base URL from environment variables
     * Examples:
     * - Development: http://localhost:8080
     * - Production: https://midominio.com (without port)
     * - Production with port: https://midominio.com:443
     */
    private String getBaseUrl() {
        StringBuilder baseUrl = new StringBuilder();
        baseUrl.append(appProtocol).append("://").append(appDomain);
        
        // Only add port if it's not empty and not the default ports (80 for http, 443 for https)
        if (appPort != null && !appPort.isEmpty() 
            && !("http".equals(appProtocol) && "80".equals(appPort))
            && !("https".equals(appProtocol) && "443".equals(appPort))) {
            baseUrl.append(":").append(appPort);
        }
        
        return baseUrl.toString();
    }

    /**
     * Send password reset email
     */
    public void sendPasswordResetEmail(String toEmail, String token) {
        log.info("Sending password reset email to: {}", toEmail);
        
        // Build reset URL dynamically from environment variables
        String resetUrl = getBaseUrl() + "/client/reset-password?token=" + token;

        Email from = new Email(fromEmail, fromName);
        Email to = new Email(toEmail);
        String subject = "Restablecimiento de Contraseña - " + fromName;
        
        String htmlContent = buildPasswordResetEmailHtml(resetUrl);
        Content content = new Content("text/html", htmlContent);

        sendEmail(from, to, subject, content);
    }

    /**
     * Send email verification email
     */
    public void sendEmailVerification(String toEmail, String token) {
        log.info("Sending email verification to: {}", toEmail);
        
        // Build verification URL dynamically from environment variables
        String verificationUrl = getBaseUrl() + "/client/verify-email?token=" + token;

        Email from = new Email(fromEmail, fromName);
        Email to = new Email(toEmail);
        String subject = "Verifica tu Correo Electrónico - " + fromName;
        
        String htmlContent = buildEmailVerificationHtml(verificationUrl);
        Content content = new Content("text/html", htmlContent);

        sendEmail(from, to, subject, content);
    }

    /**
     * Send email using SendGrid
     */
    private void sendEmail(Email from, Email to, String subject, Content content) {
        Mail mail = new Mail(from, subject, to, content);
        SendGrid sg = new SendGrid(sendGridApiKey);
        Request request = new Request();

        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);

            log.info("SendGrid Response Status: {}", response.getStatusCode());

            if (response.getStatusCode() >= 400) {
                log.error("Error sending email. Status: {}, Body: {}", 
                    response.getStatusCode(), response.getBody());
                throw new RuntimeException("Error al enviar email. Status: " + response.getStatusCode());
            }
            
            log.info("Email sent successfully to: {}", to.getEmail());
            
        } catch (IOException e) {
            log.error("Error sending email via SendGrid", e);
            throw new RuntimeException("Error al enviar email a través de SendGrid", e);
        }
    }

    /**
     * Build HTML content for password reset email
     */
    private String buildPasswordResetEmailHtml(String resetUrl) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; background: white; padding: 30px; border-radius: 10px; }
                    .header { text-align: center; color: #38e07b; }
                    .button { display: inline-block; padding: 15px 30px; background: #38e07b; color: white; text-decoration: none; border-radius: 5px; margin: 20px 0; }
                    .footer { text-align: center; color: #666; font-size: 12px; margin-top: 20px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1 class="header">🍽️ El Gran Sazón</h1>
                    <h2>Restablecimiento de Contraseña</h2>
                    <p>Hola,</p>
                    <p>Has solicitado restablecer tu contraseña en El Gran Sazón.</p>
                    <p>Haz clic en el siguiente botón para continuar:</p>
                    <div style="text-align: center;">
                        <a href="%s" class="button">Restablecer Contraseña</a>
                    </div>
                    <p><strong>Este enlace expirará en 15 minutos por seguridad.</strong></p>
                    <p>Si no solicitaste esto, por favor ignora este correo.</p>
                    <div class="footer">
                        <p>Gracias,<br>El equipo de El Gran Sazón</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(resetUrl);
    }

    /**
     * Build HTML content for email verification
     */
    private String buildEmailVerificationHtml(String verificationUrl) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; background: white; padding: 30px; border-radius: 10px; }
                    .header { text-align: center; color: #38e07b; }
                    .button { display: inline-block; padding: 15px 30px; background: #38e07b; color: white; text-decoration: none; border-radius: 5px; margin: 20px 0; }
                    .footer { text-align: center; color: #666; font-size: 12px; margin-top: 20px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1 class="header">🍽️ El Gran Sazón</h1>
                    <h2>¡Bienvenido a El Gran Sazón!</h2>
                    <p>Hola,</p>
                    <p>Gracias por registrarte en El Gran Sazón.</p>
                    <p>Por favor, haz clic en el siguiente botón para verificar tu correo electrónico:</p>
                    <div style="text-align: center;">
                        <a href="%s" class="button">Verificar Mi Email</a>
                    </div>
                    <p><strong>Este enlace expirará en 15 minutos.</strong></p>
                    <p>Si no te has registrado en El Gran Sazón, por favor ignora este correo.</p>
                    <div class="footer">
                        <p>Saludos,<br>El equipo de El Gran Sazón</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(verificationUrl);
    }
}
