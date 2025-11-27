# Carpeta de Sonidos

Esta carpeta contiene los archivos de audio utilizados en la aplicación.

## Archivos de Sonido

### notification.mp3

**Uso:** Notificación de nuevos pedidos para el chef
**Ubicación:** Se reproduce cuando:

- Un nuevo pedido llega mientras el chef está en el dashboard
- Un nuevo pedido llega mientras el chef está viendo pedidos pendientes (pending.html)
- Se cambia el estado de un pedido (IN_PREPARATION, READY, CANCELLED)

**Formato:** MP3
**Duración recomendada:** 1-2 segundos
**Volumen:** Configurado al 50% (0.5)
**Tipo:** Sonido corto y llamativo (ding, chime, alert)

---

### payment.mp3

**Uso:** Notificación de pago exitoso
**Ubicación:** Se reproduce cuando:

- Se confirma el pago de una orden en el formulario de pagos
- El usuario hace clic en "Sí, procesar pago" en la confirmación
- Se ejecuta en todos los roles: Admin, Cajero, Mesero, Delivery

**Formato:** MP3
**Duración recomendada:** 1-3 segundos
**Volumen:** Configurado al 60% (0.6)
**Tipo:** Sonido alegre de éxito (cash register, ka-ching, success chime)

---

## Cómo agregar los archivos

1. Descarga DOS archivos de sonido diferentes:

   - `notification.mp3` - Para nuevos pedidos (corto, urgente)
   - `payment.mp3` - Para pagos exitosos (alegre, celebración)

2. Coloca ambos archivos en esta carpeta

3. Los archivos deben ser ligeros:

   - notification.mp3: preferiblemente < 50KB
   - payment.mp3: preferiblemente < 100KB

4. Asegúrate de que sean sonidos cortos y agradables

## Sonidos recomendados gratuitos

### Para notification.mp3:

- https://mixkit.co/free-sound-effects/notification/
  - Busca: "notification bell", "alert short"
- https://freesound.org/
  - Busca: "notification", "ding", "chime"

### Para payment.mp3:

- https://mixkit.co/free-sound-effects/
  - Busca: "success", "cash register", "coin"
- https://freesound.org/
  - Busca: "cash register", "payment success", "ka-ching"
- https://www.zapsplat.com/
  - Busca: "cash register", "payment", "success chime"

## Formatos soportados

- MP3 (recomendado)
- WAV
- OGG

## Vistas que usan los sonidos

### notification.mp3:

- `chef/dashboard.html` - Nuevos pedidos
- `chef/orders/pending.html` - Nuevos pedidos y cambios de estado

### payment.mp3:

- `admin/payments/form.html` - Cuando se confirma el pago
- `cashier/payments/form.html` - Cuando se confirma el pago
- `waiter/payments/form.html` - Cuando se confirma el pago
- `delivery/payments/form.html` - Cuando se confirma el pago

## Verificación

Puedes verificar que los archivos están accesibles visitando:

- http://localhost:8080/sounds/notification.mp3
- http://localhost:8080/sounds/payment.mp3

## Nota

Si los archivos no existen, el sistema continuará funcionando normalmente pero sin reproducir sonido.
La consola mostrará:

- "Audio file not found or failed to load"
- "Make sure [archivo].mp3 exists in /static/sounds/"

## Consejos

1. **notification.mp3** debe ser más urgente y llamativo (para alertar al chef)
2. **payment.mp3** debe ser más alegre y satisfactorio (celebrar el pago)
3. Usa sonidos diferentes para distinguirlos fácilmente
4. Mantén los archivos pequeños para carga rápida
5. Prueba los sonidos antes de usarlos en producción
