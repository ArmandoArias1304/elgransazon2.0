-- Script para corregir los iconos de las redes sociales
-- Ejecutar este script si los iconos no aparecen

-- Ver estado actual de redes sociales
SELECT id, name, url, icon, active FROM social_networks ORDER BY display_order;

-- Actualizar iconos de redes sociales con valores correctos (SIN ESPACIOS EXTRAS)
UPDATE social_networks SET icon = 'fab fa-facebook-f' WHERE name = 'Facebook';
UPDATE social_networks SET icon = 'fab fa-instagram' WHERE name = 'Instagram';
UPDATE social_networks SET icon = 'fab fa-whatsapp' WHERE name = 'WhatsApp';
UPDATE social_networks SET icon = 'fab fa-twitter' WHERE name = 'Twitter';
UPDATE social_networks SET icon = 'fab fa-linkedin-in' WHERE name = 'LinkedIn';
UPDATE social_networks SET icon = 'fab fa-tiktok' WHERE name = 'TikTok';
UPDATE social_networks SET icon = 'fab fa-youtube' WHERE name = 'YouTube';

-- Si los nombres no coinciden exactamente, puedes actualizar por ID
-- Ejemplo:
-- UPDATE social_networks SET icon = 'fab fa-facebook-f' WHERE id = 1;
-- UPDATE social_networks SET icon = 'fab fa-instagram' WHERE id = 2;
-- UPDATE social_networks SET icon = 'fab fa-whatsapp' WHERE id = 3;
-- UPDATE social_networks SET icon = 'fab fa-twitter' WHERE id = 4;

-- Verificar que se actualizaron correctamente
SELECT 
    id, 
    name, 
    icon,
    LENGTH(icon) as icon_length,
    HEX(icon) as icon_hex,
    active
FROM social_networks 
ORDER BY display_order;

-- El LENGTH debe ser el número exacto de caracteres (ej: 'fab fa-facebook-f' = 17 caracteres)
-- Si ves números diferentes, hay espacios o caracteres invisibles

-- Para limpiar completamente y reinsertar (OPCIONAL - ejecutar solo si persiste el problema)
/*
DELETE FROM social_networks WHERE system_configuration_id = 1;

INSERT INTO social_networks (name, url, icon, display_order, active, system_configuration_id, created_at)
VALUES 
('Facebook', 'https://facebook.com/elgransazon', 'fab fa-facebook-f', 1, true, 1, NOW()),
('Instagram', 'https://instagram.com/elgransazon', 'fab fa-instagram', 2, true, 1, NOW()),
('WhatsApp', 'https://wa.me/5215512345678', 'fab fa-whatsapp', 3, true, 1, NOW()),
('Twitter', 'https://twitter.com/elgransazon', 'fab fa-twitter', 4, true, 1, NOW());
*/

-- Verificar resultado final
SELECT 
    CONCAT('✓ ', name, ': ', icon) as social_network_config
FROM social_networks 
WHERE active = true
ORDER BY display_order;
