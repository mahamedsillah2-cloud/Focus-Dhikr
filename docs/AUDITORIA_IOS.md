# Auditoría técnica de las APIs de Apple

Antes de una línea de código: qué permite realmente un iPhone hoy, comprobado
contra la documentación oficial de Apple (agosto de 2026), no de memoria.

Cada afirmación de este documento sale de `developer.apple.com`. Cuando algo no
está documentado por Apple y solo se sabe por la experiencia de la comunidad,
lo digo explícitamente. Cuando algo **no se puede hacer**, lo digo sin adornos y
propongo lo más cercano que sí se puede.

---

## Resumen en una tabla: tus 17 puntos contra la realidad

| Lo que pediste | ¿Se puede? | Cómo, o por qué no |
|---|---|---|
| Elegir apps concretas de mi iPhone | ✅ | `FamilyActivityPicker` |
| Ver *qué* apps he elegido, por nombre | ⚠️ | El sistema las dibuja (`Label(token)`); tu app no lee el nombre. **Salvo en la UE con iOS 26.4+**, ver §1b |
| Límite diario distinto por app | ✅ | Un `DeviceActivityEvent` con `threshold` por app |
| Ver «llevo 45 min hoy» | ⚠️ | No hay API que devuelva minutos a tu app. Dos sucedáneos legítimos, §2b |
| Bloquear a las 22:00–08:00 | ✅ | Un `DeviceActivitySchedule` por franja |
| Días concretos (solo lunes a viernes) | ⚠️ | El `Schedule` no tiene día de la semana: se filtra en la extensión, §2c |
| Bloqueo real, del sistema | ✅ | `ManagedSettingsStore.shield.applications` |
| Que el bloqueo sea *mi* pantalla | ❌ | `ShieldConfiguration`: fondo, icono, título, subtítulo y dos botones. Nada más |
| Fases 2–5 (intención, espera, objetivo, escritura) dentro del bloqueo | ❌ | Imposible ahí. Van en la app, §3c |
| Salto del bloqueo a mi app | ⚠️→✅ | Hasta iOS 26.4, una notificación que tocar. Desde **iOS 26.5**, `.openParentalControlsApp` lo hace el sistema |
| Dhikr en el momento del impulso | ✅ | En el subtítulo del escudo (estático) y completo en la app |
| Corán y hadices verificados | ✅ | No depende de Apple. Validador en CI |
| Estadísticas 7/30 días | ⚠️ | Reales pero **solo visibles**, no legibles, §2d |
| Modo disciplina | ✅ | Con un extra real: `denyAppRemoval`, §3d |
| Que no me la pueda desinstalar | ⚠️ | Con `.individual` **no**. Con `.child` sí, y tiene un coste, §1a |
| Todo local, sin servidores | ✅ | Sandbox + App Group. La extensión de informes ni siquiera puede abrir un socket |
| Probarlo en mi iPhone | ✅ | Con el entitlement de desarrollo, hoy. Publicar es otra cosa, §5 |

---

## 1. Qué puede hacer FamilyControls

### 1a. Autorización

```swift
try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
```

Dos modalidades, y la diferencia es **la más importante de todo el documento**:

| | `.individual` | `.child` |
|---|---|---|
| Quién aprueba | Tú, con Face ID / Touch ID | Un padre/tutor de tu grupo de Familia en Compartir en familia |
| ¿Puedes desinstalar la app? | **Sí** | **No.** El sistema lo impide |
| ¿Puedes cerrar sesión de iCloud? | Sí | No, mientras haya una app autorizada |
| ¿Puedes revocar el permiso tú solo? | Sí, `revokeAuthorization()` o Ajustes | No sin el padre/tutor |

Apple lo dice literalmente para `.individual`: *«the system removes any
restrictions that prevent the user from bypassing parental controls so the user
can delete an authorized app or sign out of iCloud as needed»*.

**Consecuencia honesta:** en modo `.individual` —el que usa esta app— siempre
podrás desinstalarla y saltarte todo en menos de un minuto. Eso no es un fallo:
es lo que pediste en el punto 8 y en el punto 17. La app crea fricción
psicológica, no una cárcel.

Si alguna vez quisieras la versión que **no** se puede desinstalar, existe y es
legítima: usar una cuenta de menor en Compartir en familia (tuya o de un
familiar de confianza) y autorizar con `.child`. El precio es real: esa cuenta
no puede cerrar sesión de iCloud y necesitas a otra persona para revocar.

`AuthorizationStatus`: `.notDetermined`, `.denied`, `.approved`,
`.approvedWithDataAccess` (esta última, iOS 26.4+, ver abajo).

### 1b. Selección de apps: el modelo de privacidad

`FamilyActivityPicker` devuelve un `FamilyActivitySelection` con tres conjuntos
de **tokens opacos**: `applicationTokens`, `categoryTokens`, `webDomainTokens`.

Un `ApplicationToken` **no es** un bundle id. No trae nombre, no trae icono, y
no hay forma soportada de convertirlo en texto. Lo único que puedes hacer es
`Label(token)`, una vista SwiftUI que **dibuja el sistema** en tu pantalla: tú
ves «Instagram», tu código no.

Esto es a propósito. Es la razón por la que Apple permite que una app cualquiera
lea la actividad de otras: porque nunca la lee de verdad.

**Novedad importante para ti (estás en la UE): iOS 26.4.**

`FamilyActivityData.shared.installedApplications` devuelve `[Application]`, y
cada `Application` trae `bundleIdentifier` **y** `token`. Es decir: sí se puede
mapear token → «com.burbn.instagram» → «Instagram», en la propia app.

Condiciones, todas obligatorias:

- Entitlement adicional `com.apple.developer.family-controls.app-and-website-usage`.
- Estado `.approvedWithDataAccess`, que el usuario concede aparte.
- **Solo una app del dispositivo puede tener ese estado a la vez.** Si le das
  acceso a otra, la tuya cae a `.notDetermined`.
- En clientes reales: **solo en la UE, con una cuenta de Apple de país de la UE**.
  Fuera de la UE `authorizationStatus` nunca devuelve `approvedWithDataAccess`.
  En desarrollo, con perfil de Apple, funciona en cualquier región.
- iOS 26.4 o superior, y Xcode con el SDK 26.4 o superior para compilarlo.

Es la diferencia entre «tu app de límites» y una app que puede escribir
«Instagram — 1 h/día» en su propia interfaz. Está implementado detrás de una
bandera de compilación, ver §6.

---

## 2. Qué puede hacer DeviceActivity

### 2a. El modelo: horarios y umbrales, no un contador

```swift
try DeviceActivityCenter().startMonitoring(
    DeviceActivityName("daily"),
    during: DeviceActivitySchedule(
        intervalStart: DateComponents(hour: 4),
        intervalEnd: DateComponents(hour: 3, minute: 59),
        repeats: true
    ),
    events: [DeviceActivityEvent.Name("instagram"): DeviceActivityEvent(
        applications: [token],
        threshold: DateComponents(minute: 60)
    )]
)
```

Tu extensión (`DeviceActivityMonitor`, proceso aparte) recibe exactamente seis
llamadas y ninguna más:

| Callback | Cuándo |
|---|---|
| `intervalDidStart(for:)` | Empieza la franja |
| `intervalDidEnd(for:)` | Termina la franja |
| `intervalWillStartWarning(for:)` | `warningTime` antes de empezar |
| `intervalWillEndWarning(for:)` | `warningTime` antes de acabar |
| `eventDidReachThreshold(_:activity:)` | Se cruzó un umbral |
| `eventWillReachThresholdWarning(_:activity:)` | `warningTime` antes del umbral |

Funciona con tu app cerrada. El sistema despierta la extensión. Eso es
exactamente lo que pediste en el punto 3.

**Límites:** Apple documenta que `startMonitoring` *lanza* si intentas
monitorizar «demasiadas actividades o actividades demasiado juntas», pero **no
publica el número**. La comunidad reporta ~20 actividades simultáneas y una
duración mínima de franja de 15 minutos; no lo doy por oficial. La app registra
una actividad diaria + una por franja horaria y avisa si te pasas.

La extensión corre con un presupuesto de memoria muy pequeño (≈6 MB, tampoco
documentado) y **sin interfaz**. Por eso solo hace una cosa: poner el escudo.

### 2b. «¿Cuántos minutos llevo hoy?» — no existe esa API

No hay ninguna función que devuelva «45 minutos». Lo repito porque es la
limitación que más rompe expectativas. Hay dos sucedáneos y la app usa los dos:

1. **Umbrales escalonados.** En vez de un solo evento a los 60 min, registramos
   varios: 50 %, 80 % y 100 % del límite. Cuando la extensión recibe el de
   80 %, sabe —y anota en el App Group— que has usado *al menos* 48 min. La app
   muestra «al menos 48 min», nunca «48 min». Es un dato real del sistema, con
   la resolución que Apple deja.
2. **`DeviceActivityReport`**, abajo.

### 2c. Días de la semana

`DeviceActivitySchedule` se construye con `DateComponents` de hora y minuto y un
`repeats: Bool`. **No tiene día de la semana.** Una franja «22:00–08:00» se
repite todos los días o ninguno.

Solución real, no simulada: la franja se registra a diario, y en
`intervalDidStart` la extensión mira el día de hoy contra tu máscara de días. Si
hoy no toca, no pone el escudo. El sistema despierta igual, nosotros decidimos.

### 2d. DeviceActivityReport: los minutos reales, que puedes ver pero no leer

`DeviceActivityReport` es una vista SwiftUI que pones en tu app:

```swift
DeviceActivityReport(.init("total"), filter: filter)
```

La rellena **una extensión tuya** (`DeviceActivityReportExtension`, punto de
extensión `com.apple.deviceactivityui.report-extension`), que sí recibe datos de
verdad: `ApplicationActivity.totalActivityDuration`, `numberOfPickups`,
`numberOfNotifications`, segmentados por intervalo.

Y aquí está la trampa, en palabras de Apple: *«your extension runs in a sandbox.
This sandbox prevents your extension from making network requests or moving
sensitive content outside the extension's address space»*.

Es decir: la extensión **puede dibujar** «Instagram — 1 h 04 min» dentro de tu
app, y **no puede** escribir ese número en el App Group para que tu app lo use.
Los minutos reales existen en pantalla; el resto de tu código no los ve.

Por eso las pantallas son dos, y la app lo dice: *tus minutos reales* (el
informe, dibujado por el sistema) y *tu historial de pausas* (lo que la app sí
puede contar: cuántas veces diste la vuelta).

---

## 3. Qué puede hacer ManagedSettings y ManagedSettingsUI

### 3a. El bloqueo

```swift
store.shield.applications = tokens        // hasta 50 apps
store.shield.applicationCategories = .specific(categoryTokens)
store.shield.webDomains = domainTokens
```

Efecto real, del sistema: el icono se atenúa con un reloj de arena, y al abrir
la app el sistema la tapa con el escudo. No es una pantalla tuya encima. No se
descarta deslizando. **Esto es exactamente el punto 4 de tu lista y sí se puede.**

Además existe `store.application.blockedApplications`, que **oculta** la app y
ni siquiera deja lanzarla (también máximo 50). Es más duro: no hay escudo, no
hay pausa, no hay reflexión. Va contra la filosofía de tu punto 17, así que la
app no lo usa.

### 3b. El escudo: esto es todo lo que Apple deja personalizar

```swift
ShieldConfiguration(
    backgroundBlurStyle:, backgroundColor:, icon:,
    title:, subtitle:,
    primaryButtonLabel:, primaryButtonBackgroundColor:,
    secondaryButtonLabel:,
    secondaryButtonSubmenuItems:   // iOS 26.4+, hasta 3
)
```

Y ya. **No hay** temporizador, campo de texto, lista de opciones, navegación,
imágenes propias ni vistas SwiftUI. Un icono, dos etiquetas, dos botones.

Detalle asimétrico y útil: en la extensión de **configuración** del escudo, iOS
sí te da `application.localizedDisplayName`. Ahí, y solo ahí, puedes escribir
«Habías decidido limitar Instagram». En la extensión de **acción** no: Apple lo
documenta explícitamente («The system doesn't provide the name of a shielded
Application… to preserve the Family Sharing group's privacy»).

### 3c. Los dos botones, y por qué tus fases 2–5 no caben ahí

`ShieldActionDelegate` recibe `.primaryButtonPressed` o `.secondaryButtonPressed`
y responde una de cuatro cosas:

| Respuesta | Qué hace |
|---|---|
| `.close` | Cierra el escudo, vuelves al inicio |
| `.defer` | El escudo se queda |
| `.none` | Nada |
| `.openParentalControlsApp` | **iOS 26.5+**: el sistema abre *tu* app |

`PAUSA → RECUERDA → REFLEXIONA → DECIDE` con opciones, cuenta atrás, objetivo y
texto escrito **no es implementable dentro del escudo**. No hay dónde dibujarlo.

La arquitectura viable —y la que está implementada— invierte el flujo:

```
escudo del sistema  ──"Quiero entrar igualmente"──▶  extensión de acción
                                                          │
                          iOS ≤26.4: apunta en App Group + notificación (un toque)
                          iOS 26.5+: .openParentalControlsApp (automático)
                                                          │
                                                          ▼
                                          las 6 fases, en tu app, completas
```

En iOS 26.5 esto deja de ser un apaño y pasa a ser el camino oficial. En
versiones anteriores es un toque en una notificación. Con eso, **todo lo que
pediste en los puntos 5, 6, 7 y 8 se puede hacer**, solo que a un toque de
distancia del escudo, no encima de Instagram.

Además, desde iOS 26.4 el botón secundario admite hasta 3 opciones de submenú
(`firstSecondarySubmenuItemPressed`, etc.). Eso permite una versión mínima de tu
fase 2 —«¿por qué quieres entrar?»— **en el propio escudo**, con tres opciones.

### 3d. Modo Disciplina: lo que sí endurece de verdad

```swift
store.application.denyAppRemoval = true      // no se puede desinstalar NADA
store.application.denyAppInstallation = true // no se puede instalar NADA
```

`denyAppRemoval` es real y aplica a **todas** las apps del dispositivo, no solo
a las tuyas: incluida Focus Dhikr. Fricción auténtica, y reversible desde la app
(que sigue siendo tuya). Es opt-in dentro del Modo Disciplina y está apagado por
defecto, porque impedir desinstalar cualquier cosa es un efecto secundario que
tienes que elegir a sabiendas.

---

## 4. Qué extensiones hacen falta, y por qué son procesos separados

| Extensión | Punto de extensión | Trabajo |
|---|---|---|
| Device Activity Monitor | `com.apple.deviceactivity.monitor-extension` | Poner/quitar el escudo al cruzar umbrales y al empezar/acabar franjas |
| Shield Configuration | `com.apple.ManagedSettings.shield-configuration-service` | Dibujar el escudo |
| Shield Action | `com.apple.ManagedSettings.shield-action-service` | Responder a los dos botones |
| Device Activity Report | `com.apple.deviceactivityui.report-extension` | Dibujar los minutos reales dentro de la app |

Son cuatro procesos distintos del de tu app. Lo único que comparten es un **App
Group**. Si el identificador del App Group no coincide en los cinco targets, la
app y las extensiones dejan de verse **sin dar ningún error**: el escudo se pone
y la app cree que no hay nada bloqueado. Es el fallo nº 1 en este tipo de apps.

---

## 5. Permisos, entitlements y distribución

| | |
|---|---|
| **Entitlement base** | `com.apple.developer.family-controls`. En **desarrollo** lo activas tú en Xcode y funciona hoy. Para **App Store/TestFlight** hay que pedirle permiso a Apple: <https://developer.apple.com/contact/request/family-controls-distribution> |
| **Entitlement de datos (opcional)** | `com.apple.developer.family-controls.app-and-website-usage`, iOS 26.4+, solo tiene efecto para clientes en la UE |
| **App Group** | `group.com.tuid.shared` en los cinco targets |
| **Autorización en ejecución** | `requestAuthorization(for: .individual)`, con Face ID |
| **Notificaciones** | `UNUserNotificationCenter`, para el traspaso y los recordatorios del punto 11 |
| **Cuenta de desarrollador** | Gratuita: la app **caduca a los 7 días** y hay que reinstalarla. De pago (99 $/año): un año, y TestFlight |
| **Un Mac con Xcode** | Imprescindible. No hay forma de compilar para iPhone sin él |

**Qué puedes probar hoy en tu iPhone, sin esperar a nadie:** todo lo de este
documento. El entitlement de desarrollo se activa solo. Lo que necesita
aprobación de Apple es **publicarla**, no usarla tú.

**Restricciones desde App Store/TestFlight:** exactamente las mismas que en
desarrollo, más la aprobación del entitlement. La app no gana ni pierde
capacidades por venir de la Store. Apple revisa con lupa las apps de Screen
Time: exigen que la función principal sea control parental o bienestar digital
(esta lo es) y que no uses la API para analítica ni publicidad (no hay red).

**En visionOS los intentos de autorización siempre fallan.** No es tu caso, pero
que conste.

---

## 6. Lo que esta app hace con todo eso

| Fase que pediste | Estado | Nota |
|---|---|---|
| 1. Proyecto iOS en SwiftUI | ✅ | `ios/project.yml`, cinco targets |
| 2–3. Family Controls y autorización | ✅ | Onboarding con Face ID |
| 4–5. Selector y guardado | ✅ | `FamilyActivityPicker` → App Group |
| 6. Límites diarios **por app** | ✅ | Uno por token, no un límite global |
| 7. Device Activity | ✅ | Diaria + una actividad por franja + umbrales escalonados |
| 8. Managed Settings | ✅ | Escudo por app; `denyAppRemoval` opcional |
| 9. Experiencia de bloqueo | ✅ | Escudo del sistema, con dhikr en el subtítulo |
| 10. Reflexión de 6 fases | ✅ | En la app, a un toque del escudo |
| 11. Dhikr y contenido islámico | ✅ | Verificado en CI contra la fuente |
| 12. Objetivos | ✅ | Tuyos, editables, aparecen en la fase 4 |
| 13. Estadísticas | ⚠️ | Historial de pausas (exacto) + informe del sistema (real, no legible) |
| 14. Ajustes y privacidad | ✅ | Sin red, sin cuentas, borrado total |
| 15. Probar en iPhone real | ⏳ | Necesita tu Mac. Nadie ha ejecutado esto todavía |

Lo que **no** está y no lo va a estar, porque iOS no lo permite:

- Las fases 2–5 dentro del escudo. Van en la app.
- Un contador de minutos exacto legible por la app.
- Impedir que desinstales la app en modo `.individual`.

---

## Fuentes

Todo lo anterior está comprobado contra la documentación oficial:

- [Family Controls](https://developer.apple.com/documentation/familycontrols) ·
  [AuthorizationStatus.approvedWithDataAccess](https://developer.apple.com/documentation/familycontrols/authorizationstatus/approvedwithdataaccess) ·
  [FamilyActivityData](https://developer.apple.com/documentation/familycontrols/familyactivitydata)
- [DeviceActivity](https://developer.apple.com/documentation/deviceactivity) ·
  [startMonitoring](https://developer.apple.com/documentation/deviceactivity/deviceactivitycenter/startmonitoring(_:during:events:)) ·
  [DeviceActivityReport](https://developer.apple.com/documentation/deviceactivity/deviceactivityreport)
- [ManagedSettings](https://developer.apple.com/documentation/managedsettings) ·
  [ApplicationSettings](https://developer.apple.com/documentation/managedsettings/applicationsettings) ·
  [ShieldActionResponse](https://developer.apple.com/documentation/managedsettings/shieldactionresponse)
- [ShieldConfiguration](https://developer.apple.com/documentation/managedsettingsui/shieldconfiguration) ·
  [secondaryButtonSubmenuItems](https://developer.apple.com/documentation/managedsettingsui/shieldconfiguration/secondarybuttonsubmenuitems)
- [Entitlement Family Controls](https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.family-controls) ·
  [Entitlement App and Website Usage](https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.family-controls.app-and-website-usage)
