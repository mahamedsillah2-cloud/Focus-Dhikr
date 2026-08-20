# Privacidad

## Lo esencial

**Focus Dhikr no declara el permiso `android.permission.INTERNET`.**

Eso significa que el sistema operativo **impide** al proceso abrir un socket.
No es una política de privacidad ni una promesa: es una restricción que aplica
Android y que puedes verificar tú mismo.

```bash
aapt dump permissions app-debug.apk
```

En esa lista no aparecerá `android.permission.INTERNET`. Y hay una comprobación
automática en CI (`.github/workflows/android.yml`) que **falla la compilación**
si alguien lo añade alguna vez.

Consecuencias directas:

- ❌ No hay analítica
- ❌ No hay informes de fallos remotos
- ❌ No hay publicidad
- ❌ No hay rastreadores
- ❌ No hay cuentas ni inicio de sesión
- ❌ No hay sincronización en la nube

## Qué se guarda, y dónde

Todo en una base de datos SQLite dentro del almacenamiento privado de la app.
Ninguna otra aplicación puede leerla.

| Dato | Para qué |
|---|---|
| Apps que has limitado y su límite | Aplicar los límites |
| Minutos por app y por día | Estadísticas |
| Intentos de entrar y cómo acabaron | Contador de «has dado la vuelta» |
| Tus objetivos | Mostrártelos en la fase 4 |
| Franjas horarias | Aplicarlas |
| Lo que escribes en la fase 5 | Que puedas releerlo |
| Ajustes | Ajustes |

## Copias de seguridad

`android:allowBackup="false"` y `res/xml/data_extraction_rules.xml` excluyen
todos los dominios. Tus datos **no salen** ni siquiera por la copia de seguridad
de Google ni por la transferencia entre dispositivos.

Efecto secundario honesto: si cambias de móvil, empiezas de cero.

## Lo que escribes en las pausas

Se guarda solo en el móvil, y puedes:

- **Desactivar que se guarde** (Ajustes → Privacidad). El texto se usa para la
  fase y se descarta.
- **Borrar lo ya escrito** con un botón.
- **Fijar cuánto historial se conserva** (7–365 días, 90 por defecto). Lo más
  antiguo se borra solo.
- **Borrarlo todo** de una vez.

## Sobre el permiso de accesibilidad

Es el permiso más potente de Android y merece una explicación clara.

El servicio escucha **un solo tipo de evento**:
`TYPE_WINDOW_STATE_CHANGED`, y de él usa **un solo campo**: el nombre del
paquete que ha pasado a primer plano.

Hay una segunda función: cuando el modo estricto está activo, comprueba si la
pantalla actual de Ajustes menciona «Focus Dhikr» (para poner un poco de
fricción antes de desinstalar). Esa comprobación **no guarda ni transmite nada**.

El servicio no lee mensajes, no lee contraseñas, no hace capturas y no puede
enviar nada a ninguna parte, porque la app no tiene acceso a la red.

El código está en
[`AppBlockAccessibilityService.kt`](../app/src/main/java/com/focusdhikr/service/AppBlockAccessibilityService.kt).
Son unas cien líneas. Léelas.

## Sobre `QUERY_ALL_PACKAGES`

Se necesita para poder ofrecerte **cualquier** app instalada en el selector, en
vez de una lista fija de diez. Solo se leen nombre, icono y paquete de las apps
que tienen icono en el lanzador, y solo mientras el selector está abierto.

## Sobre la pantalla de pausa

`GateActivity` se marca con `FLAG_SECURE`, así que lo que escribes en la fase 5
no sale en capturas de pantalla ni en la miniatura de aplicaciones recientes.
