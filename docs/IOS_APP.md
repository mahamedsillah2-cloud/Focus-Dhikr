# Focus Dhikr para iPhone — la app nativa

La versión con Atajos ([`IOS_ATAJO.md`](IOS_ATAJO.md)) funciona hoy y no necesita
nada. Esta es la de verdad: el bloqueo lo aplica iOS, no se descarta deslizando.

**Necesitas tres cosas, y una no depende de ti.**

---

## Lo que hace falta

| | |
|---|---|
| **Un Mac con Xcode 16.4 o superior** | Sin esto no hay app de iOS. No existe forma de compilar para iPhone sin un Mac. El proyecto está fijado a iOS 16 como mínimo, así que Xcode 15 también vale |
| **El entitlement `com.apple.developer.family-controls`** | Se le pide a Apple. Para **desarrollo** lo tienes al momento; para **distribuir** hay que esperar su aprobación |
| **Una cuenta de desarrollador** | Con la gratuita la app **caduca a los 7 días** y hay que reinstalarla. Con la de pago dura un año |

> Sobre si el entitlement exige además cuenta de pago: la tabla de
> [capacidades compatibles en iOS](https://developer.apple.com/help/account/reference/supported-capabilities-ios/)
> de Apple lista **Family Controls** y **App Groups** también en la columna de
> la cuenta gratuita, y marca Family Controls como *«development only»*. Es
> decir: sobre el papel, con cuenta gratuita puedes compilarla e instalarla en
> tu iPhone. Lo que **no** he podido confirmar es que la firma automática de
> Xcode con un *Personal Team* provisione de verdad el entitlement de Family
> Controls, que es donde suele romperse. Pruébalo gratis primero; si la firma
> falla ahí, la cuenta de pago es el arreglo.
>
> Lo que sí es seguro es lo de los 7 días.

---

## Compilar

```bash
git clone https://github.com/mahamedsillah2-cloud/Focus-Dhikr.git
cd Focus-Dhikr/ios
open FocusDhikr.xcodeproj
```

O sin terminal: descarga el repositorio como ZIP, descomprímelo, y en Xcode
**File → Open…** → `Focus-Dhikr/ios/FocusDhikr.xcodeproj`.

`FocusDhikr.xcodeproj` **está en el repositorio** y es el fichero de verdad: no
hay que generarlo, no hace falta XcodeGen ni Homebrew, y **no hay ningún
`.xcworkspace`** — no hay CocoaPods ni Swift Packages que lo justifiquen. El
único fichero que se abre es el `.xcodeproj`.

En Xcode, en cada uno de los cinco *targets*:

1. **Signing & Capabilities → Team** → tu cuenta.
2. Cambia los *bundle identifiers* — `com.focusdhikr.app` y sus cuatro
   extensiones — por unos tuyos. Los míos estarán cogidos.
3. Cambia el App Group `group.com.focusdhikr.shared` por uno tuyo, **en los
   cinco sitios**, y actualiza `SharedStore.appGroupID` para que coincida.
   Si no coinciden, la app y las extensiones dejan de verse y el bloqueo se
   queda a medias sin dar ningún error.

### Pedir el entitlement

<https://developer.apple.com/contact/request/family-controls-distribution>

Pídelo **el primer día**, no cuando termines. En los foros de Apple hay gente
esperando semanas y alguno meses. Mientras tanto puedes compilar e instalar en
tu propio iPhone con el entitlement de desarrollo.

---

## Cómo está montado, y por qué así

iOS reparte el trabajo entre cinco procesos. No es una decisión de diseño mía:
es la única forma que permite el sistema.

```
      abres Instagram
            │
            ▼
   ┌──────────────────┐   el sistema, no tu app
   │  Shield de iOS   │   icono + título + subtítulo + 2 botones
   └────────┬─────────┘
            │
   ┌────────┴─────────────────────┐
   │                              │
   ▼                              ▼
"Dejarlo por ahora"      "Quiero entrar igualmente"
   │                              │
   ▼                              ▼
.close  →  vuelves         se apunta la petición
al inicio, y la            en el App Group
extensión lo apunta        + notificación
como TURNED_BACK           + .defer (el shield sigue)
                                  │
                                  ▼
                        abres Focus Dhikr
                                  │
                                  ▼
                    ┌──────────────────────────┐
                    │  Las 6 fases, aquí       │
                    │  pausa · intención ·     │
                    │  espera+dhikr · objetivo │
                    │  · escritura · decisión  │
                    └──────────────────────────┘
```

### Los cinco targets

| Target | Qué hace | Por qué está separado |
|---|---|---|
| `FocusDhikr` | La app: elegir apps, límites por aplicación, franjas, objetivos, recordatorios, ajustes y **las seis fases** | — |
| `FocusDhikrMonitor` | Anota los umbrales cruzados y recalcula los escudos | `DeviceActivityMonitor` corre en su propio proceso, con muy poca memoria y sin interfaz |
| `FocusDhikrShield` | Dibuja el contenido del escudo | `ShieldConfigurationDataSource` |
| `FocusDhikrShieldAction` | Responde a los dos botones | `ShieldActionDelegate` |
| `FocusDhikrReport` | Dibuja tus **minutos reales** dentro de la app | `DeviceActivityReportExtension`, en un sandbox que le impide sacar esos datos de ahí |

Todos comparten `ios/Shared` y un **App Group**, que es lo único que los cinco
procesos pueden ver. Si el identificador del App Group no coincide en los cinco,
la app y las extensiones dejan de verse **sin dar ningún error**.

### Cómo se decide qué está bloqueado

Los escudos no se ponen y se quitan uno a uno según llegan los avisos: se
**recalculan enteros** desde las reglas (`ShieldDecision.evaluate`) cada vez que
pasa algo — un umbral cruzado, una franja que empieza, la app que vuelve a
primer plano. Una llamada perdida o un reinicio dejarían un token escudado para
siempre sin que nadie supiera por qué; recalculando, lo peor que puede pasar es
que una pantalla esté mal un rato, no para siempre.

Esa función es pura y no conoce Family Controls: recibe claves, límites,
minutos y franjas, y devuelve qué bloquear. Por eso se puede probar en
`ios/Tests/EnforcementTests.swift` sin entitlement y sin esperar una hora a que
se cumpla un límite.

---

> Desde iOS 26.5 el traspaso deja de necesitar el toque: `ShieldActionResponse`
> tiene un caso `.openParentalControlsApp` que abre tu app directamente. Y desde
> iOS 26.4 el botón secundario admite un submenú de hasta tres opciones. Ambas
> cosas están escritas y desactivadas: necesitan el SDK de iOS 26.4+, así que
> viven detrás de `FOCUSDHIKR_MODERN_SHIELD` en los ajustes del proyecto y CI no las
> compila. Ver `docs/AUDITORIA_IOS.md`, sección 3c.

## Lo que iOS no deja hacer

Esto no es una lista de pendientes. Son límites del sistema.

**El escudo no es tu pantalla.** `ShieldConfiguration` admite color de fondo,
desenfoque, icono, título, subtítulo y **dos botones**. Y
`ShieldActionDelegate` solo puede responder `.close`, `.defer` o `.none`. No
hay cuenta atrás, ni campo de texto, ni navegación. **Las fases 2 a 5 no caben
ahí**, y por eso viven en la app.

**Hasta iOS 26.4, la extensión no puede abrir tu app.** De ahí la notificación:
convierte el salto en un toque en vez de en «ahora búscate la app». Desde
iOS 26.5 hay una respuesta oficial que sí la abre, y está implementada tras la
bandera de compilación de arriba.

**No sabes qué apps ha elegido el usuario.** `FamilyActivitySelection` devuelve
tokens opacos, a propósito. La app no puede escribir «Instagram — 1 h 04 min»;
solo puede pintar `Label(token)`, que dibuja el sistema.

> Curiosidad útil: en la **extensión del escudo**, y solo ahí, iOS sí da
> `application.localizedDisplayName`. Por eso el escudo puede decir «Habías
> decidido limitar Instagram» y la app no.

**No hay minutos.** `DeviceActivity` avisa cuando se **cruza un umbral**; no
existe API que devuelva «45 minutos usados hoy». La app registra umbrales
escalonados (50 %, 80 % y 100 % del límite) y por eso siempre escribe «al menos
48 min», nunca «48 min».

Los minutos exactos sí existen, en `FocusDhikrReport`, y Apple documenta que esa
extensión corre en un sandbox que le impide sacarlos de su propio proceso. Se
pueden **ver**; no se pueden **leer**. La pantalla de historial separa las dos
cosas en dos bloques, con su explicación.

**El usuario puede desinstalarla.** Con autorización `.individual`, Apple
documenta que el sistema *retira* las restricciones que impedirían borrar la
app: es tu dispositivo y tu decisión. Hay dos matices reales: el Modo Disciplina
puede activar `denyAppRemoval`, que impide desinstalar cualquier app del iPhone
mientras esté puesto (y se quita desde la propia app), y una autorización
`.child` en Compartir en familia sí impide borrarla — a cambio de necesitar a
otra persona para revocarla.

---

## Lo que iOS te da y lo que te cobra

| | En iOS |
|---|---|
| La pausa aparece sobre la app | ⚠️ a un toque desde el escudo (automática en iOS 26.5) |
| Elegir apps por nombre e icono | ⚠️ el sistema los dibuja; los tokens son opacos |
| Minutos exactos por app | ❌ solo umbrales — visibles en pantalla, no legibles por la app |
| Las 6 fases | ✅ dentro de la app |
| Bloqueo que el sistema aplica de verdad | ✅ `ManagedSettings` |
| Instalarla sin permiso de nadie | ❌ hace falta el entitlement de Apple |
| Que no caduque | ⚠️ 7 días con cuenta gratuita; un año con cuenta de pago |

El detalle, API por API, está en [`AUDITORIA_IOS.md`](AUDITORIA_IOS.md).

---

## Qué está probado y qué no

**Probado en CI** ([`.github/workflows/ios.yml`](../.github/workflows/ios.yml)):
que compila, y las pruebas unitarias de la máquina de estados, las reglas de
bloqueo (límites por app, franjas, días de la semana, permisos temporales), los
umbrales de uso, las rachas, el día lógico y las citas.

**No probado:** nada de lo que necesita un iPhone real. Nadie ha ejecutado esta
app. En concreto, sin verificar: que la autorización de Tiempo de uso se
conceda, que el escudo aparezca, que el traspaso al App Group funcione entre
procesos, que la notificación llegue, y que retirar el escudo devuelva el acceso.

Compilar no es funcionar. Cuando tengas el Mac, lo primero es ejecutarla y ver
cuál de esas cinco cosas falla — porque alguna fallará.

---

## Si te bloqueas

- **Apple deniega el entitlement**, o tarda demasiado → te quedas con el atajo
  de [`IOS_ATAJO.md`](IOS_ATAJO.md), que no depende de nadie.
- **No consigues Mac** → igual. El atajo se monta desde el propio iPhone.
