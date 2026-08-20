# Focus Dhikr — Arquitectura y decisiones técnicas

> Documento de decisión. Se escribe **antes** del código y explica qué es real,
> qué es imposible y por qué.

---

## 1. Decisión de plataforma: **Android nativo primero**

### Resumen

| | Android | iOS |
|---|---|---|
| Leer tiempo de uso por app | ✅ `UsageStatsManager` (permiso especial, el usuario lo concede) | ⚠️ Solo agregado y opaco. `DeviceActivity` da *umbrales*, no minutos legibles |
| Saber qué app está en primer plano *ahora* | ✅ `AccessibilityService` / `UsageStatsManager.queryEvents()` | ❌ Imposible. No existe API pública |
| Mostrar una pantalla propia al abrir otra app | ✅ Overlay (`SYSTEM_ALERT_WINDOW`) o Activity | ❌ Solo el *shield* del sistema, no es tu UI |
| Personalizar esa pantalla | ✅ Total (Compose, cualquier diseño) | ❌ `ShieldConfiguration`: icono + título + subtítulo + 2 botones |
| Flujo progresivo de 5 fases | ✅ Sí | ❌ No dentro del shield |
| Instalar en tu móvil sin permiso de Apple | ✅ APK, inmediato | ❌ Requiere entitlement aprobado por Apple |

### Por qué Android

**El requisito #2 y #3 (bloqueo progresivo de varias fases con reflexión, dhikr,
objetivos y texto escrito) es literalmente imposible en iOS.**

En iOS el bloqueo lo dibuja el sistema, no tu app. `ManagedSettings` + el
`ShieldConfiguration` de `ShieldConfigurationExtension` solo permiten
personalizar: icono, título, subtítulo, color de fondo y **dos botones**
(primario y secundario). Cuando el usuario pulsa un botón, tu
`ShieldActionExtension` recibe una acción y solo puede responder
`.close`, `.defer` o `.none`. **No puedes** navegar a otra pantalla, mostrar un
temporizador, pedir texto escrito ni encadenar fases.

Además:

- `FamilyControls` requiere el entitlement `com.apple.developer.family-controls`,
  que **hay que solicitar a Apple** y que Apple concede caso por caso. Sin él,
  ni siquiera compila para dispositivo.
- Las extensiones `DeviceActivityMonitor` corren con un límite de memoria muy
  bajo (~6 MB) y sin UI.
- `FamilyActivitySelection` devuelve tokens **opacos**: tu app nunca sabe que el
  usuario eligió "Instagram", solo un `ApplicationToken` sin nombre ni icono.
  El requisito "quiero ver Instagram → 1 h" con nombre e icono no se puede
  representar fielmente.
- No hay forma de saber que el usuario está intentando abrir una app *ahora*.
  El sistema aplica el shield; tú no te enteras hasta que pulsa un botón.

**Conclusión:** iOS puede hacer un bloqueador, pero no *este* bloqueador.
Android puede hacer exactamente lo que has descrito. Empezamos por Android.

Ver `docs/LIMITES_PLATAFORMA.md` para el detalle completo, incluida la
arquitectura que **sí** sería viable en iOS si algún día se hace.

---

## 2. Stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Room** para persistencia local
- **DataStore (Proto/Preferences)** para ajustes
- **Servicio en primer plano** para el contador
- **AccessibilityService** para la intercepción instantánea
- Sin Dagger/Hilt: un contenedor de dependencias manual y explícito
  (`AppGraph`). El proyecto es pequeño; menos magia, menos build time.

**Sin frameworks multiplataforma.** Flutter o React Native obligarían igualmente
a escribir en Kotlin nativo el 100 % de la parte difícil (usage stats, servicio
de accesibilidad, overlay, receptor de arranque) y a comunicarlo por platform
channels. Es más código y más frágil para cero beneficio, porque iOS queda
descartado de todos modos.

### Privacidad estructural

El `AndroidManifest.xml` **no declara el permiso `android.permission.INTERNET`.**

Esto no es una promesa: es una garantía verificable por el sistema operativo.
Sin ese permiso, el proceso no puede abrir un socket. No hay analítica, no hay
crash reporting remoto, no hay anuncios, no hay trackers. Puedes comprobarlo tú
mismo con `aapt dump permissions app-debug.apk`.

---

## 3. Cómo funciona el bloqueo (de verdad)

### 3.1 Contabilizar el tiempo de uso

`UsageStatsManager.queryEvents(desde, hasta)` devuelve un flujo de eventos
`ACTIVITY_RESUMED` (23) y `ACTIVITY_PAUSED` (24) por paquete. Recorriendo esos
eventos se reconstruyen las **sesiones** reales de cada app y se suman.

Por qué eventos y no `queryUsageStats()`: `queryUsageStats` devuelve
`totalTimeInForeground` agregado por intervalo, con bucketing impreciso y
comportamiento distinto entre fabricantes. Los eventos son exactos y permiten
recuperar tiempo perdido si el servicio muere.

- **Permiso:** `android.permission.PACKAGE_USAGE_STATS`, que es de tipo
  `appop`. No se pide con un diálogo: hay que enviar al usuario a
  `Settings.ACTION_USAGE_ACCESS_SETTINGS` y que lo active a mano.
- **Comprobación:** `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, uid, pkg)`.
- El contador es **idempotente**: se guarda una marca de agua
  (`lastProcessedEventTs`) y en cada pasada solo se procesan eventos nuevos.
  Si el móvil se apaga 3 horas, al arrancar se recuperan esas 3 horas.

### 3.2 Detectar que abres una app bloqueada

Dos mecanismos, ambos implementados:

**A. `AccessibilityService` (preferido).**
Escucha `TYPE_WINDOW_STATE_CHANGED`. Latencia ~0 ms: se dispara en el mismo
momento en que la app pasa a primer plano. Es el mecanismo que usan todos los
bloqueadores serios de Android.

**B. Sondeo con `UsageStatsManager` (respaldo).**
Un servicio en primer plano consulta cada segundo cuál es el último
`ACTIVITY_RESUMED`. Latencia real de 1–2 s: verás Instagram un instante antes
de que aparezca la pantalla de pausa. Funciona sin accesibilidad.

La app usa A si está concedido y cae a B si no. La pantalla de ajustes te dice
cuál está activo y qué diferencia hay.

### 3.3 Mostrar la pantalla de pausa

Desde Android 10 (API 29) una app **no puede lanzar Activities desde segundo
plano**… salvo excepciones. La que usamos es la oficial: si la app tiene
`SYSTEM_ALERT_WINDOW` concedido, el lanzamiento desde segundo plano está
permitido (`ActivityManager`: *"the app has the SYSTEM_ALERT_WINDOW permission"*).

Por eso el bloqueo usa dos capas:

1. **`GateActivity`** a pantalla completa, con
   `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK`.
2. **Overlay `TYPE_APPLICATION_OVERLAY`** por `WindowManager` como red de
   seguridad, para fabricantes que estrangulan el lanzamiento de Activities
   (Xiaomi/MIUI, Huawei/EMUI, algunos Oppo). El overlay se dibuja *encima* de
   Instagram aunque la Activity no llegue a lanzarse.
3. Si además hay accesibilidad, se ejecuta `GLOBAL_ACTION_HOME` para sacarte de
   la app cuando decides no entrar.

### 3.4 Lo que NO se puede hacer

Está en `docs/LIMITES_PLATAFORMA.md`, pero lo esencial:

- **No es un candado del sistema.** Puedes desinstalar Focus Dhikr o revocar sus
  permisos y el bloqueo desaparece. Ninguna app normal de Android puede impedir
  eso. Lo que sí se puede es **añadir fricción**: el `AppBlockAccessibilityService`
  detecta que estás navegando a la pantalla de "Información de la aplicación" de
  Focus Dhikr y te devuelve al inicio. Es exactamente lo que hacen las apps
  comerciales del sector. Es fricción, no una barrera.
- **Device Admin** (opcional, apagado por defecto) sí impide desinstalar mientras
  esté activo: Android bloquea la desinstalación de un administrador de
  dispositivo. Desactivarlo requiere ir a Ajustes → Seguridad → Apps de
  administración. Es reversible, como debe ser (requisito #10).
- **No podemos ocultar el tiempo dentro de la propia app bloqueada.** Contamos
  tiempo de app, no de contenido.

---

## 4. Modelo del bloqueo progresivo

Máquina de estados en `domain/gate/GateStateMachine.kt`, pura y testeada
(sin dependencias de Android, para poder probarla en JVM).

```
   abres Instagram (límite agotado)
              │
              ▼
     ┌──────────────────┐
  1  │ PAUSA            │  tiempo usado / límite / cuánto falta
     │                  │  + una frase corta
     └────────┬─────────┘
              │ "Quiero entrar igualmente"
              ▼
     ┌──────────────────┐
  2  │ INTENCIÓN        │  ¿qué ibas a hacer exactamente?
     │                  │  ¿esto te acerca a lo de hoy?
     └────────┬─────────┘
              │
              ▼
     ┌──────────────────┐
  3  │ ESPERA           │  cuenta atrás + dhikr
     └────────┬─────────┘
              │
              ▼
     ┌──────────────────┐
  4  │ PROPÓSITO        │  uno de tus objetivos
     │                  │  + cita verificada (opcional)
     └────────┬─────────┘
              │
              ▼
     ┌──────────────────┐
  5  │ ESCRITURA        │  con tus palabras + frase de reconocimiento
     └────────┬─────────┘
              │
              ▼
     ┌──────────────────┐
  6  │ DECISIÓN         │  se respeta tu elección
     └────────┬─────────┘
              │
              ▼
        acceso temporal (por defecto 5 min), luego vuelve a bloquear
```

**Principio de diseño:** cada fase tiene siempre una salida hacia adelante.
La app nunca te encierra, nunca te insulta y nunca te llama fracasado. El texto
es descriptivo, no moral. "Has usado 1 h 04 min" en lugar de "Has vuelto a
caer".

En **modo estricto** ("No me dejes entrar") se alarga la espera, se exige el
texto completo y no se pueden saltar fases opcionales. Dentro de una **ventana
horaria** definida (p. ej. 22:00–08:00) el modo estricto es todavía más duro.

---

## 5. Estructura de paquetes

```
com.focusdhikr
├── FocusDhikrApp.kt        Application, arranque del contenedor
├── core/                   utilidades: tiempo, formato, día lógico
├── data/
│   ├── db/                 Room: entidades, DAOs, base de datos
│   ├── prefs/              DataStore: ajustes
│   └── repo/               repositorios (única puerta a los datos)
├── domain/
│   ├── model/              modelos de dominio puros
│   ├── gate/               máquina de estados del bloqueo (JVM puro, testeada)
│   └── usage/              cálculo de sesiones y límites (JVM puro, testeado)
├── content/                adhkar, Corán y hadices verificados
├── service/                servicios de Android (tracking, accesibilidad, overlay)
└── ui/                     Compose: onboarding, home, apps, gate, goals, stats, settings
```

**Regla:** `domain/` y `content/` no importan nada de `android.*`. Así se pueden
probar en la JVM sin emulador, que es la única manera de probar de verdad la
lógica del bloqueo en este entorno.

---

## 6. El día lógico

Un "día" no empieza a medianoche necesariamente. Si te acuestas a las 02:00,
medianoche parte tu noche en dos y el límite se resetea justo cuando peor viene.
Por eso el día lógico tiene un **inicio configurable** (por defecto 04:00) y
todos los cálculos usan `DayBoundary.dayKeyFor(instant, resetHour, zone)`.

---

## 7. Contenido islámico: reglas duras

1. **Ninguna cita se escribe de memoria.** Cada cita del Corán lleva sura,
   número de aleya, texto árabe y traducción. Cada hadiz lleva colección,
   número de referencia y grado de autenticidad.
2. **Hay un validador en CI** (`tools/verify_citations.py`) que descarga cada
   aleya de la API de Quran.com y compara el árabe carácter a carácter,
   normalizando solo las diacríticas de presentación. Si una cita no coincide,
   **la build falla**.
3. El componente espiritual es **opcional y desactivable**, y por defecto
   discreto. Un dhikr breve en el momento de la pausa, no un sermón en cada
   pantalla.
4. Los recordatorios tienen frecuencia configurable, incluido "nunca".

---

## 8. Cómo se prueba en tu móvil

No hay SDK de Android en el entorno donde se escribió esto (`dl.google.com`
está bloqueado por la política de red), así que la compilación ocurre en
**GitHub Actions**, que sí tiene el SDK y red abierta.

1. Haces push a la rama.
2. El workflow `.github/workflows/android.yml` compila `assembleDebug`.
3. Descargas el APK desde la pestaña *Actions* → *Artifacts*.
4. Lo instalas en el móvil (Ajustes → permitir instalar apps de fuentes
   desconocidas para tu navegador o gestor de archivos).

Instrucciones detalladas en `docs/INSTALACION.md`.
