# Privacidad

## Lo esencial

**No hay servidores. No hay cuentas. No hay analítica. No hay anuncios. No hay
SDKs de terceros.**

Y, a diferencia de otras apps que dicen lo mismo, aquí hay tres cosas que
puedes comprobar tú, sin fiarte de esta página.

### 1. No hay ni una línea de código de red

```bash
grep -rn "URLSession\|URLRequest\|Network\.\|CFStream\|NWConnection" ios/
```

No devuelve nada. Tampoco hay dependencias externas: el proyecto no
declara ni un paquete de Swift Package Manager, así que no existe código de
nadie más dentro de la app que pudiera abrir una conexión por su cuenta.

> **Honestidad:** en Android se puede *demostrar* esto omitiendo el permiso
> `INTERNET`, y entonces el sistema impide abrir un socket. **iOS no tiene ese
> mecanismo**: cualquier app puede conectarse sin declarar nada. Así que aquí la
> garantía es que el código no está y puedes verificarlo, no que el sistema lo
> impida. Salvo en el caso siguiente, donde sí lo impide.

### 2. La única extensión que ve tus minutos reales no puede hablar con nadie

Los minutos exactos de uso solo existen dentro de `FocusDhikrReport`
(`DeviceActivityReportExtension`). Apple lo dice literalmente en su
documentación:

> *«your extension runs in a sandbox. This sandbox prevents your extension from
> making network requests or moving data out of the extension.»*

No es una promesa nuestra: es el sistema operativo. Esa extensión dibuja tus
minutos en pantalla y **no puede** pasárselos a la app, ni a nosotros, ni a
nadie. Que sea también una limitación molesta para el producto (ver
[`AUDITORIA_IOS.md`](AUDITORIA_IOS.md), sección 4) es el precio del diseño de
Apple, y en privacidad juega a tu favor.

### 3. Ni siquiera la app sabe qué aplicaciones has elegido

`FamilyActivityPicker` devuelve `ApplicationToken`s **opacos**. Son
identificadores cifrados y sin significado fuera de tu iPhone: no contienen el
nombre, ni el icono, ni el bundle ID. La app guarda tokens y minutos, y nunca
llega a saber que uno de ellos es Instagram.

El nombre solo aparece en el momento del bloqueo, dentro de la extensión del
escudo, porque el sistema se lo entrega ahí. Si lo guardamos para enseñártelo
en tus estadísticas, es porque **tú** pasaste por ese bloqueo, y se guarda en tu
iPhone.

## Qué se guarda, y dónde

Todo en `UserDefaults` dentro del contenedor del App Group
`group.com.focusdhikr.shared`. Es almacenamiento privado del sandbox de la app:
ninguna otra aplicación del iPhone puede leerlo.

Está en el App Group —y no en el almacenamiento normal de la app— porque los
cinco procesos (la app y sus cuatro extensiones) son procesos separados y esa
carpeta compartida es lo único que todos pueden ver.

| Dato | Para qué |
|---|---|
| Los tokens de las apps que has limitado, y su límite | Aplicar los límites |
| Umbrales de uso cruzados hoy | Estadísticas (mínimos, no minutos exactos) |
| Intentos de entrar y cómo acabaron | Contador de «has dado la vuelta» |
| Tus objetivos | Mostrártelos en la fase 4 |
| Franjas horarias | Aplicarlas |
| Lo que escribes en las fases 2 y 5 | Que puedas releerlo |
| Nombres de apps que el escudo ya te ha mostrado | Que las estadísticas no digan «app 3» |
| Ajustes | Ajustes |

El historial de intentos se recorta solo a **90 días**; las pantallas de
estadísticas nunca miran más atrás de 30.

## Lo que escribes en las pausas

Se guarda solo en el iPhone, y puedes:

- **Desactivar que se guarde** (Ajustes → Privacidad). El texto se usa durante
  la fase y se descarta al terminar.
- **Borrar lo ya escrito** con un botón, conservando el resto del historial.
- **Borrarlo todo** —historial, estadísticas y ajustes— de una vez.

## Copias de seguridad

El contenedor del App Group **sí entra** en la copia de seguridad de iCloud si
la tienes activada. No lo ocultamos: iOS no ofrece una forma limpia de excluir
`UserDefaults` de la copia.

Si prefieres que ni eso salga del móvil: Ajustes → tu nombre → iCloud →
Copia de seguridad de iCloud → *Este iPhone* → desactiva **Focus Dhikr**.

(La copia de iCloud está cifrada, y con la Protección de Datos Avanzada de
Apple activada lo está de extremo a extremo.)

## Sobre el permiso de Family Controls

Es el único permiso que pide la app, y conviene entender exactamente qué es.

Se solicita con `AuthorizationCenter.shared.requestAuthorization(for:
.individual)`. **`.individual`** significa: control sobre *este* dispositivo,
para ti, sin cuenta de organizador ni de menor. La app no ve tu grupo de
Compartir en Familia, no ve otros dispositivos y no puede pedir nada sobre otra
persona.

Lo que ese permiso concede:

- poner y quitar restricciones (`ManagedSettings`) sobre los tokens que tú
  elegiste,
- pedirle al sistema que avise al cruzar un umbral de uso (`DeviceActivity`).

Lo que **no** concede, ni con permiso: leer qué apps tienes instaladas, saber
qué app está abierta ahora, leer contenido de otras apps, ni ver tu historial
de Tiempo de Uso.

Puedes revocarlo cuando quieras (Ajustes → Tiempo de Uso), y desinstalar la app
cuando quieras: en modo `.individual` Apple **garantiza** que puedas.

## Capturas de pantalla

Android permite marcar una pantalla como no capturable (`FLAG_SECURE`). **iOS
no tiene equivalente público**, así que lo que escribes en la fase 5 puede salir
en una captura que hagas tú. No hay forma de impedirlo sin API privada, y no
vamos a usar API privada.

## Qué pasaría si esto se publicara en la App Store

La *App Privacy* de la ficha sería la casilla que casi nadie marca: **«No se
recopilan datos»**. No es una postura de marketing; es que no hay ningún sitio
al que enviarlos.
