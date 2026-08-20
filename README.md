# Focus Dhikr

Una aplicación para iPhone que convierte el gesto automático de abrir Instagram
en una decisión consciente.

No te castiga, no te llama débil y no te encierra. Te pone unos segundos
delante, te enseña lo que tú mismo decidiste, y si después de todo sigues
queriendo entrar, **te deja entrar**.

> PAUSA → RECUERDA → REFLEXIONA → DECIDE

---

## Qué hace, con las APIs oficiales de Apple

- Eliges tus aplicaciones con el **selector del sistema** (`FamilyActivityPicker`)
  y le pones a cada una **su propio límite diario**: Instagram 1 h, TikTok 30 min.
- **Franjas horarias** propias — «de 22:00 a 08:00, de lunes a viernes» — con
  una actividad de `DeviceActivity` por franja.
- Cuando llegas al límite, el bloqueo lo aplica **iOS**, no una pantalla falsa:
  `ManagedSettings` pone el escudo del sistema y el icono se apaga.
- El escudo lleva **tu** texto y, si quieres, un dhikr
  (`ShieldConfigurationExtension`).
- El botón «Quiero entrar igualmente» te trae a la app, donde ocurre la pausa
  progresiva de hasta seis fases: lo que decidiste → qué ibas a hacer → una
  espera → uno de tus objetivos → escribirlo con tus palabras → tu decisión.
- Componente islámico **opcional y desactivable**: dhikr en el momento del
  impulso, y aleyas y hadices **con referencia y grado de autenticidad**,
  verificados en CI contra su fuente. Nunca inventados.
- **Modo Disciplina**, franjas, acceso de emergencia con su propia fricción, y
  la opción real de impedir desinstalar aplicaciones (`denyAppRemoval`).
- Estadísticas de hoy, 7 y 30 días, con el foco en el **tiempo recuperado**.

## Lo que iOS no permite, dicho claro

Esto importa tanto como lo anterior, y está detallado API por API en
[`docs/AUDITORIA_IOS.md`](docs/AUDITORIA_IOS.md).

- **El escudo no es tu pantalla.** Apple solo deja poner fondo, icono, título,
  subtítulo y dos botones. Las fases 2 a 5 no caben ahí: viven en la app, a un
  toque de distancia (automático desde iOS 26.5).
- **No hay API de minutos.** iOS avisa al cruzar un umbral; no dice «45 min».
  La app registra umbrales escalonados y siempre escribe «al menos 45 min».
  Los minutos exactos existen en la pantalla de historial, dibujados por una
  extensión del sistema que **no puede** pasárselos a la app.
- **No sabe qué apps has elegido.** Los tokens son opacos a propósito. (Con
  iOS 26.4, en la UE y con permiso aparte, hay una excepción documentada.)
- **Puedes desinstalarla.** En modo `.individual` Apple garantiza que puedas.
  La app crea fricción psicológica, no una cárcel — que es justamente el
  encargo.

## Privacidad

Sin cuentas, sin analítica, sin anuncios, sin servidores. Todo vive en el App
Group del propio iPhone. La extensión que ve tus minutos reales corre en un
sandbox que le impide hacer peticiones de red, por diseño de Apple.

Ver [`docs/PRIVACIDAD.md`](docs/PRIVACIDAD.md).

## Cómo compilarla

Hace falta un Mac con Xcode. No hay otra forma de instalar nada en un iPhone.

```bash
brew install xcodegen
cd ios
xcodegen generate
open FocusDhikr.xcodeproj
```

Los pasos completos —firma, App Group, entitlement de Family Controls y qué
pedirle a Apple— están en [`docs/IOS_APP.md`](docs/IOS_APP.md).

## Documentación

| | |
|---|---|
| [`docs/AUDITORIA_IOS.md`](docs/AUDITORIA_IOS.md) | Qué permite cada API de Apple, comprobado contra su documentación |
| [`docs/IOS_APP.md`](docs/IOS_APP.md) | Compilar, firmar, entitlements y arquitectura de los cinco targets |
| [`docs/CITAS.md`](docs/CITAS.md) | De dónde sale cada aleya y cada hadiz, y cómo se verifican |
| [`docs/PRIVACIDAD.md`](docs/PRIVACIDAD.md) | Qué datos existen y dónde viven |
| [`docs/LIMITES_PLATAFORMA.md`](docs/LIMITES_PLATAFORMA.md) | Los límites del sistema, plataforma por plataforma |
| [`docs/IOS_ATAJO.md`](docs/IOS_ATAJO.md) | Una versión con Atajos que funciona hoy, sin Mac y sin esperar a Apple |

## Sobre la carpeta `app/`

El repositorio contiene también una implementación anterior para Android
(`app/`, Kotlin). **No es el producto**: la app es la de iPhone. Se conserva
porque de ahí vienen el motor de fricción y las citas verificadas, que la
versión de iOS reimplementa una a una y prueba con la misma batería de tests.
Si quieres que desaparezca, se borra en un commit.
