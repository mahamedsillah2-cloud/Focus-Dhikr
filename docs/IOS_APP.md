# Focus Dhikr para iPhone — la app nativa

La versión con Atajos ([`IOS_ATAJO.md`](IOS_ATAJO.md)) funciona hoy y no necesita
nada. Esta es la de verdad: el bloqueo lo aplica iOS, no se descarta deslizando.

**Necesitas tres cosas, y una no depende de ti.**

---

## Lo que hace falta

| | |
|---|---|
| **Un Mac con Xcode 15 o superior** | Sin esto no hay app de iOS. No existe forma de compilar para iPhone sin un Mac |
| **El entitlement `com.apple.developer.family-controls`** | Se le pide a Apple. Para **desarrollo** lo tienes al momento; para **distribuir** hay que esperar su aprobación |
| **Una cuenta de desarrollador** | Con la gratuita la app **caduca a los 7 días** y hay que reinstalarla. Con la de pago dura un año |

> Sobre si el entitlement exige además cuenta de pago: no he podido confirmarlo
> en la documentación de Apple, así que no te lo doy por seguro. Lo que sí es
> seguro es lo de los 7 días.

---

## Compilar

```bash
brew install xcodegen
cd ios
xcodegen generate
open FocusDhikr.xcodeproj
```

El `.xcodeproj` **no está en el repositorio**: se genera desde
[`ios/project.yml`](../ios/project.yml). Un `project.pbxproj` escrito a mano son
mil líneas de UUIDs que nadie puede revisar y que se rompen en silencio.

En Xcode, en cada uno de los cuatro *targets*:

1. **Signing & Capabilities → Team** → tu cuenta.
2. Cambia los *bundle identifiers* — `com.focusdhikr.app` y sus tres
   extensiones — por unos tuyos. Los míos estarán cogidos.
3. Cambia el App Group `group.com.focusdhikr.shared` por uno tuyo, **en los
   cuatro sitios**, y actualiza `SharedStore.appGroupID` para que coincida.
   Si no coinciden, la app y las extensiones dejan de verse y el bloqueo se
   queda a medias sin dar ningún error.

### Pedir el entitlement

<https://developer.apple.com/contact/request/family-controls-distribution>

Pídelo **el primer día**, no cuando termines. En los foros de Apple hay gente
esperando semanas y alguno meses. Mientras tanto puedes compilar e instalar en
tu propio iPhone con el entitlement de desarrollo.

---

## Cómo está montado, y por qué así

iOS reparte el trabajo entre cuatro procesos. No es una decisión de diseño mía:
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

### Los cuatro targets

| Target | Qué hace | Por qué está separado |
|---|---|---|
| `FocusDhikr` | La app: elegir apps, límites, objetivos, ajustes y **las seis fases** | — |
| `FocusDhikrMonitor` | Pone el escudo al cruzar un límite | `DeviceActivityMonitor` corre en su propio proceso, con ~6 MB de memoria y sin interfaz |
| `FocusDhikrShield` | Dibuja el contenido del escudo | `ShieldConfigurationDataSource` |
| `FocusDhikrShieldAction` | Responde a los dos botones | `ShieldActionDelegate` |

Todos comparten `ios/Shared` y un **App Group**, que es lo único que los cuatro
procesos pueden ver.

---

## Lo que iOS no deja hacer

Esto no es una lista de pendientes. Son límites del sistema.

**El escudo no es tu pantalla.** `ShieldConfiguration` admite color de fondo,
desenfoque, icono, título, subtítulo y **dos botones**. Y
`ShieldActionDelegate` solo puede responder `.close`, `.defer` o `.none`. No
hay cuenta atrás, ni campo de texto, ni navegación. **Las fases 2 a 5 no caben
ahí**, y por eso viven en la app.

**La extensión no puede abrir tu app.** De ahí la notificación: convierte el
salto en un toque en vez de en «ahora búscate la app». Es lo más fluido que
permite iOS.

**No sabes qué apps ha elegido el usuario.** `FamilyActivitySelection` devuelve
tokens opacos, a propósito. La app no puede escribir «Instagram — 1 h 04 min»;
solo puede pintar `Label(token)`, que dibuja el sistema.

> Curiosidad útil: en la **extensión del escudo**, y solo ahí, iOS sí da
> `application.localizedDisplayName`. Por eso el escudo puede decir «Habías
> decidido limitar Instagram» y la app no.

**No hay minutos.** `DeviceActivity` avisa cuando se **cruza un umbral**; no
existe API que devuelva «45 minutos usados hoy». Por eso los límites son
umbrales y el «tiempo recuperado» del historial es una estimación declarada
como tal en pantalla, no un dato.

**El usuario puede desinstalarla.** Igual que en Android. Ninguna app normal
puede impedirlo en ninguna de las dos plataformas.

---

## Diferencias reales con la versión de Android

| | Android | iOS |
|---|---|---|
| La pausa aparece sobre la app | ✅ automática | ⚠️ un toque en la notificación |
| Elegir apps por nombre e icono | ✅ | ⚠️ tokens opacos |
| Minutos exactos por app | ✅ | ❌ solo umbrales |
| Las 6 fases | ✅ | ✅ pero dentro de la app |
| Estadísticas reales | ✅ | ⚠️ estimadas |
| Instalarla sin permiso de nadie | ✅ | ❌ entitlement de Apple |
| Que no caduque | ✅ | ⚠️ 7 días con cuenta gratuita |

---

## Qué está probado y qué no

**Probado en CI** ([`.github/workflows/ios.yml`](../.github/workflows/ios.yml)):
que compila, y las pruebas unitarias de la máquina de estados, las franjas
horarias, el día lógico y las citas — las mismas que en Android, portadas, para
que las dos plataformas se comporten igual.

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
- **No consigues Mac** → igual.
- **Cambias a Android** → la app completa ya está hecha y compilando.
