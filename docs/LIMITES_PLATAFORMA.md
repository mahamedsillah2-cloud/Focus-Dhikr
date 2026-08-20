# Límites reales del sistema operativo

Este documento responde exactamente a lo que pediste en el punto 13: para cada
cosa que **no** se puede hacer directamente, cuál es el motivo, qué API oficial
existe, qué permisos hace falta, qué restricciones tiene y cuál es la
alternativa viable.

---

## ANDROID

### A1. Leer cuánto tiempo usas cada app

| | |
|---|---|
| **API oficial** | `android.app.usage.UsageStatsManager` |
| **Permiso** | `android.permission.PACKAGE_USAGE_STATS` (protection level `signature\|privileged\|appop`) |
| **Cómo se concede** | No con un diálogo. `startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))` y el usuario lo activa a mano |
| **Cómo se comprueba** | `AppOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, packageName) == MODE_ALLOWED` |
| **Restricciones** | Solo da tiempo en primer plano por paquete. No dice *qué* miraste. Los datos se retienen un tiempo limitado (los eventos, del orden de días). Algunos fabricantes recortan la retención |

**Método usado:** `queryEvents()` en vez de `queryUsageStats()`.
`queryUsageStats()` agrega por buckets (`INTERVAL_DAILY`, etc.) y su precisión
varía entre fabricantes. `queryEvents()` da la secuencia cruda de
`ACTIVITY_RESUMED` / `ACTIVITY_PAUSED` y permite reconstruir sesiones exactas.

**Limitación honesta:** si el usuario revoca el permiso, dejamos de contar. No
hay forma de impedirlo.

---

### A2. Saber qué app está en primer plano en este instante

Hay tres formas y solo dos son legítimas hoy.

**❌ `ActivityManager.getRunningTasks()`** — deprecado desde API 21 y capado: solo
devuelve las tareas de tu propia app. No sirve.

**✅ Opción 1 — `AccessibilityService`** (la buena)

| | |
|---|---|
| **API** | `android.accessibilityservice.AccessibilityService`, evento `TYPE_WINDOW_STATE_CHANGED` |
| **Permiso** | `android.permission.BIND_ACCESSIBILITY_SERVICE` en el manifest + activación manual en Ajustes → Accesibilidad |
| **Latencia** | Prácticamente inmediata |
| **Restricción grave (Android 13+)** | Es un *restricted setting*. Si la app se instaló por sideload (APK), el sistema **impide activar la accesibilidad** hasta que vayas a *Información de la app → menú ⋮ → Permitir ajustes restringidos*. Está documentado en `docs/INSTALACION.md` |
| **Restricción de política** | Google Play exige justificar el uso de accesibilidad. No afecta a instalación por APK |

**✅ Opción 2 — sondeo con `UsageStatsManager`** (el respaldo)

| | |
|---|---|
| **API** | `queryEvents(now - 2s, now)`, se busca el último `ACTIVITY_RESUMED` |
| **Permiso** | El mismo de A1 |
| **Latencia** | 1–2 s reales |
| **Restricción** | Requiere un proceso vivo sondeando → servicio en primer plano con notificación permanente |

La app implementa **las dos** y usa la mejor disponible.

---

### A3. Dibujar tu pantalla encima de otra app

| | |
|---|---|
| **API** | `WindowManager.addView()` con `TYPE_APPLICATION_OVERLAY` |
| **Permiso** | `android.permission.SYSTEM_ALERT_WINDOW` |
| **Cómo se concede** | `Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$pkg"))`. Manual |
| **Cómo se comprueba** | `Settings.canDrawOverlays(context)` |
| **Restricción** | Desde API 26 hay que usar `TYPE_APPLICATION_OVERLAY`; los tipos viejos (`TYPE_PHONE`, `TYPE_SYSTEM_ALERT`) lanzan excepción. El sistema puede mostrar un aviso de "se está mostrando sobre otras apps" |

**Bonus decisivo:** tener `SYSTEM_ALERT_WINDOW` concedido es una de las
excepciones oficiales a la **restricción de lanzar Activities desde segundo
plano** (Android 10 / API 29+). Sin ese permiso, `startActivity()` desde el
servicio se ignoraría silenciosamente en muchos casos. Por eso el permiso es
obligatorio en el onboarding, no opcional.

---

### A4. Mantener el contador vivo

| | |
|---|---|
| **API** | `Service` + `startForeground()` |
| **Permisos (API 34+)** | `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`, con `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE">` justificado en el manifest |
| **Permiso (API 33+)** | `POST_NOTIFICATIONS` en tiempo de ejecución, para la notificación del servicio |
| **Arranque** | `RECEIVE_BOOT_COMPLETED` + `BroadcastReceiver` de `ACTION_BOOT_COMPLETED` y `ACTION_MY_PACKAGE_REPLACED` |
| **Restricción: Doze** | En reposo profundo el sondeo se ralentiza. Mitigación oficial: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (el usuario acepta un diálogo del sistema) |
| **Restricción: fabricantes** | Xiaomi, Huawei, Samsung, Oppo, OnePlus y Vivo matan servicios en segundo plano con políticas propias no documentadas. Mitigación: instrucciones por fabricante en la app (autoarranque, "sin restricciones" de batería) |

El servicio en primer plano **no es lo que produce el bloqueo** cuando la
accesibilidad está activa — el `AccessibilityService` es un proceso ligado por
el sistema y sobrevive mucho mejor. El servicio en primer plano es para
contabilizar y para el modo sin accesibilidad.

---

### A5. Listar las apps instaladas

| | |
|---|---|
| **API** | `PackageManager.queryIntentActivities(Intent(ACTION_MAIN, CATEGORY_LAUNCHER))` |
| **Permiso (API 30+)** | `android.permission.QUERY_ALL_PACKAGES` |
| **Restricción** | `QUERY_ALL_PACKAGES` es un permiso sensible en Google Play y exige justificación en la ficha. Para uso personal por APK no hay problema. La alternativa sin ese permiso es declarar `<queries>` con los paquetes concretos, lo cual rompe "cualquier otra aplicación que yo seleccione" |

Se declara `QUERY_ALL_PACKAGES` **y** un bloque `<queries>` con los paquetes
más habituales, para que la app siga funcionando razonablemente si algún día se
retira el permiso amplio.

---

### A6. Impedir que te saltes el bloqueo

Esto es lo más importante que hay que entender bien.

**Lo que NO se puede hacer, y por qué:**

- **Impedir desinstalar la app.** Una app normal no tiene ese poder. Solo un
  *Device Owner* (aprovisionado por MDM, requiere reset de fábrica) o un
  administrador de dispositivo pueden.
- **Impedir revocar sus propios permisos.** Ídem.
- **Impedir el modo seguro.** En modo seguro no se ejecutan apps de terceros.
- **Impedir cambiar la hora del sistema** para simular otro día. Mitigación
  parcial: la app usa además `SystemClock.elapsedRealtime()` para detectar
  saltos de reloj hacia atrás y lo registra.

**Lo que SÍ se puede hacer (implementado):**

| Fricción | API | Efecto real |
|---|---|---|
| Interceptar la navegación a "Información de la app" de Focus Dhikr | `AccessibilityService` detecta el paquete `com.android.settings` + nodo con el nombre de la app → `GLOBAL_ACTION_BACK` | Te cuesta llegar a "Desinstalar". No es imposible |
| Bloquear la desinstalación mientras esté activo | `DeviceAdminReceiver` (`android.app.action.DEVICE_ADMIN_ENABLED`) | Android **impide** desinstalar un administrador activo. Hay que desactivarlo primero en Ajustes → Seguridad → Apps de administración de dispositivos. Fricción real y significativa. **Opcional y desactivado por defecto**, porque es intrusivo |
| Retrasar la desactivación del modo estricto | Lógica propia: `strictModeUnlockAt` | Si desactivas el modo estricto, el cambio no surte efecto hasta pasado un tiempo que tú fijaste antes, en frío |

`DeviceAdminReceiver.onDisableRequested()` permite además mostrar un texto de
advertencia justo antes de desactivar. Se usa para recordarte por qué lo
activaste.

**Postura de diseño (tu requisito #10 y #15):** la app está pensada para
crear una pausa, no una cárcel. La fricción se mide en decenas de segundos y en
tener que escribir una frase, no en "no puedes salir". Si de verdad quieres
entrar, entras.

---

### A7. Restringir una app en una franja horaria (22:00–08:00)

No hay API de "bloqueo programado" en Android. Se implementa en la propia app:

- `AlarmManager.setExactAndAllowWhileIdle()` para reevaluar en los bordes de la
  franja (permiso `SCHEDULE_EXACT_ALARM` en API 31+, o
  `USE_EXACT_ALARM` para apps cuya función principal son alarmas —
  aquí usamos `SCHEDULE_EXACT_ALARM`, que en API 33+ se concede por defecto y
  el usuario puede revocar).
- El motor de decisión consulta la franja en cada evento de primer plano, así
  que el bloqueo funciona aunque la alarma no dispare.

---

## iOS — por qué no, y qué se haría si algún día sí

### I1. Screen Time / Family Controls

| | |
|---|---|
| **Frameworks** | `FamilyControls`, `ManagedSettings`, `DeviceActivity` (iOS 15+/16+) |
| **Entitlement** | `com.apple.developer.family-controls`. **Hay que pedirlo a Apple** mediante formulario y lo aprueban caso por caso. Sin él no se compila para dispositivo |
| **Autorización en runtime** | `AuthorizationCenter.shared.requestAuthorization(for: .individual)` |

### I2. Lo que rompe tus requisitos

**El selector de apps devuelve tokens opacos.**
`FamilyActivityPicker` → `FamilyActivitySelection` contiene `ApplicationToken`
y `ActivityCategoryToken`. Son **opacos por diseño**, por privacidad: tu app no
puede saber que uno de ellos es Instagram, ni obtener su nombre o icono. Solo
puedes pintarlos con `Label(token)`, que renderiza el sistema. Tu pantalla
principal no podría decir "Instagram — 1 h 04 min / 1 h".

**No puedes leer minutos de uso.**
`DeviceActivity` no te da "45 minutos hoy". Te deja registrar un
`DeviceActivityEvent` con un `threshold` (p. ej. 1 hora) y te avisa *cuando se
cruza*. Puedes aproximar con umbrales escalonados, pero es un sucedáneo.
`DeviceActivityReport` sí muestra datos, pero **solo dentro de una extensión de
SwiftUI cuya salida tu app no puede leer** — es una vista sellada.

**El shield no es tu UI.**
`ManagedSettingsStore.shield.applications = tokens` hace que el sistema muestre
su propia pantalla. La personalizas con `ShieldConfigurationExtension`
devolviendo un `ShieldConfiguration(backgroundBlurStyle:backgroundColor:icon:
title:subtitle:primaryButtonLabel:secondaryButtonLabel:)`. Eso es todo:
**dos botones y texto estático.** No hay temporizador, ni campo de texto, ni
navegación.

`ShieldActionExtension.handle(action:for:completionHandler:)` recibe
`.primaryButtonPressed` o `.secondaryButtonPressed` y solo puede responder
`.close`, `.defer` o `.none`.

**Conclusión:** las fases 2, 3, 4 y 5 (opciones de intención, cuenta atrás,
objetivo, texto escrito) **no son implementables** dentro del bloqueo de iOS.

### I3. Arquitectura viable en iOS, si se hiciera

La única forma honesta sería invertir el flujo:

1. El shield del sistema muestra el título y un botón "Pausar y reflexionar".
2. Ese botón responde `.defer`, y la extensión escribe una bandera en un
   **App Group** compartido.
3. La app principal, al abrirse, lee la bandera y ejecuta ahí el flujo
   progresivo completo.
4. Al terminar, la app retira el token del `ManagedSettingsStore` durante N
   minutos con un `DeviceActivitySchedule`.

Funciona, pero el usuario tiene que **saltar manualmente** a tu app. Es una
experiencia claramente peor y no es lo que pediste. Por eso: Android primero.

---

## Resumen ejecutivo

| Requisito tuyo | Android | iOS |
|---|---|---|
| Elegir apps por nombre e icono | ✅ | ⚠️ tokens opacos |
| Límite diario por app | ✅ exacto | ⚠️ por umbrales |
| Bloquear al agotar | ✅ | ✅ (shield del sistema) |
| Pantalla de pausa propia | ✅ | ❌ |
| 5 fases de reflexión | ✅ | ❌ |
| Cuenta atrás dentro del bloqueo | ✅ | ❌ |
| Texto escrito para desbloquear | ✅ | ❌ |
| Dhikr en el momento del impulso | ✅ | ⚠️ como subtítulo estático |
| Franja horaria 22:00–08:00 | ✅ | ✅ |
| Estadísticas 7/30 días | ✅ | ❌ no legibles por la app |
| Todo local, sin red | ✅ (sin permiso `INTERNET`) | ✅ |
| Instalarlo hoy en tu móvil | ✅ APK | ❌ entitlement de Apple |
