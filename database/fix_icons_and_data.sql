-- Ver datos actuales
SELECT id, name, url, icon, active, display_order FROM social_networks;

-- Actualizar los datos correctos de redes sociales
UPDATE social_networks SET 
    name = 'Facebook',
    url = 'https://facebook.com/elgransazon',
    icon = 'fab fa-facebook-f',
    display_order = 1,
    active = true
WHERE id = 1;

UPDATE social_networks SET 
    name = 'Instagram',
    url = 'https://instagram.com/elgransazon',
    icon = 'fab fa-instagram',
    display_order = 2,
    active = true
WHERE id = 2;

UPDATE social_networks SET 
    name = 'WhatsApp',
    url = 'https://wa.me/5215512345678',
    icon = 'fab fa-whatsapp',
    display_order = 3,
    active = true
WHERE id = 3;

UPDATE social_networks SET 
    name = 'Twitter',
    url = 'https://twitter.com/elgransazon',
    icon = 'fab fa-twitter',
    display_order = 4,
    active = true
WHERE id = 4;

-- Verificar cambios
SELECT id, name, url, icon, active, display_order FROM social_networks ORDER BY display_order;
