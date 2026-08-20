# Focus Dhikr

Una aplicación Android que convierte el gesto automático de abrir Instagram en
una decisión consciente.

No te castiga, no te llama débil y no te encierra. Te pone unos segundos
delante, te enseña lo que tú mismo decidiste, y si después de todo sigues
queriendo entrar, **te deja entrar**.

> PAUSA → RECUERDA → REFLEXIONA → DECIDE

---

## Qué hace

- Eliges **cualquier** aplicación instalada y le pones un límite diario.
- Cuenta el tiempo real de uso con las APIs oficiales de Android
  (`UsageStatsManager`), no con estimaciones.
- Al alcanzar el límite, aparece una pausa progresiva de hasta seis fases:
  lo que decidiste → qué ibas a hacer → una espera → uno de tus objetivos →
  escribirlo con tus palabras → tu decisión.
- Componente islámico **opcional y desactivable**: un dhikr breve en el momento
  del impulso, y citas del Corán y hadices con referencia y grado de
  autenticidad, nunca inventadas.
- Modo «no me dejes entrar», franjas horarias (22:00–08:00), acceso de
  emergencia con su propia fricción.
- Estadísticas de hoy, 7 y 30 días, con el foco en el **tiempo recuperado**.

## Lo que NO hace, y por qué

Esto importa tanto como lo anterior.

- **No puede impedir que la desinstales.** Ninguna app normal de Android puede.
  Lo que sí hace es ponértelo incómodo, y ofrece activar un administrador de
  dispositivo (opcional, desactivado por defecto) que obliga a pasar por
  Ajustes → Seguridad antes de poder desinstalar.
- **No existe para iPhone**, y no por pereza: el bloqueo de iOS lo dibuja el
  sistema y solo admite un título, un subtítulo y dos botones. Las fases 2 a 5
  son literalmente imposibles ahí.
- **No manda nada a ningún servidor.** No puede: no tiene el permiso
  `INTERNET`.

El detalle completo, API por API, está en
[`docs/LIMITES_PLATAFORMA.md`](docs/LIMITES_PLATAFORMA.md).

## Privacidad

El `AndroidManifest.xml` **no declara `android.permission.INTERNET`**. El
sistema operativo impide al proceso abrir un socket. No hay analítica, ni
anuncios, ni rastreadores, ni cuentas, ni copia en la nube — y no por decisión
de producto, sino porque técnicamente no es posible.

Hay una comprobación en CI que falla la compilación si alguien añade ese
permiso alguna vez. Ver [`docs/PRIVACIDAD.md`](docs/PRIVACIDAD.md).

## ¿iPhone o Android?

Hay tres versiones, y la que te sirve depende de tu móvil y de si tienes un Mac.

| | Para quién | Qué necesitas |
|---|---|---|
| **[App de Android](docs/INSTALACION.md)** | Móvil Android | Nada. Descargas el APK de la pestaña *Actions* |
| **[Atajo de iOS](docs/IOS_ATAJO.md)** | iPhone, hoy mismo | Nada. Media hora montándolo en la app Atajos |
| **[App nativa de iOS](docs/IOS_APP.md)** | iPhone, bien hecho | Un Mac, y que Apple te apruebe el entitlement |

Las tres comparten la misma pausa de seis fases, los mismos adhkar y las mismas
citas verificadas. Cambia cuánto puede bloquear cada sistema operativo, no la
idea.

## Citas religiosas

Ninguna se ha escrito de memoria. Cada aleya lleva sura y número; cada hadiz,
colección, número, narrador y grado de autenticidad.

Un validador (`tools/verify_citations.py`) descarga cada aleya de la API de
Quran.com y la compara carácter a carácter, y comprueba cada referencia de
hadiz en sunnah.com. **Corre en CI y falla la build si algo no coincide.**

Ver [`docs/CITAS.md`](docs/CITAS.md).

## Estructura

```
ios/
├── Shared/      lógica pura y contenido verificado (compartido con las extensiones)
│   ├── Domain/  la misma máquina de estados que en Android, portada
│   ├── Content/ generado desde el Kotlin: el árabe es idéntico byte a byte
│   └── Store/   App Group y Screen Time
├── FocusDhikr/  la app SwiftUI y las seis fases
├── FocusDhikrMonitor/       pone el escudo al cruzar el límite
├── FocusDhikrShield/        dibuja el escudo del sistema
└── FocusDhikrShieldAction/  responde a sus dos botones

app/src/main/java/com/focusdhikr/
├── core/        tiempo, día lógico, permisos
├── data/        Room, DataStore, repositorios
├── domain/      lógica pura, sin Android, con pruebas
│   ├── gate/    la máquina de estados de la pausa
│   └── usage/   contabilidad de sesiones y límites
├── content/     adhkar, Corán y hadices verificados
├── service/     seguimiento, accesibilidad, overlay, arranque
└── ui/          Compose
```

`domain/` y `content/` no importan nada de `android.*`, así que la lógica que se
ejecuta en el momento más impulsivo del día se prueba en la JVM, sin emulador.

```bash
./gradlew testDebugUnitTest              # Android: 69 pruebas
cd ios && xcodegen generate              # iOS: genera el proyecto
xcodebuild test -scheme FocusDhikr ...   # y ejecuta las mismas, portadas
```

## Documentación

| | |
|---|---|
| [Arquitectura](docs/ARQUITECTURA.md) | Decisiones técnicas y por qué Android |
| [Límites de plataforma](docs/LIMITES_PLATAFORMA.md) | Qué permite y qué no cada sistema, API por API |
| [Instalación (Android)](docs/INSTALACION.md) | Cómo ponerlo en tu móvil |
| [Atajo para iPhone](docs/IOS_ATAJO.md) | La versión que funciona hoy en iOS |
| [App nativa de iOS](docs/IOS_APP.md) | Compilarla, el entitlement de Apple y sus límites |
| [Privacidad](docs/PRIVACIDAD.md) | Qué se guarda y dónde |
| [Citas](docs/CITAS.md) | Fuente y grado de cada texto religioso |

## Estado

Primera versión funcional. Todo lo descrito arriba está implementado.
Lo siguiente sería probarlo unos días de uso real y ajustar los tiempos de
espera a partir de lo que se sienta bien, no de lo que parezca razonable sobre
el papel.
