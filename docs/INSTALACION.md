# Instalar Focus Dhikr en tu móvil

No necesitas Android Studio. El APK lo compila GitHub Actions y tú solo lo
descargas.

---

## 1. Descargar el APK

1. Entra en el repositorio en GitHub → pestaña **Actions**.
2. Abre la ejecución más reciente del workflow **Build APK** (la que tiene el
   tic verde).
3. Baja hasta **Artifacts** y descarga `focus-dhikr-debug-apk`.
4. Descomprime el `.zip`. Dentro está `app-debug.apk`.

> Si el workflow está en rojo, ábrelo y mira qué job ha fallado. La compilación
> no debería fallar; si falla, el log dice exactamente por qué.

## 2. Instalarlo

1. Pasa el APK al móvil (cable, Drive, Telegram contigo mismo, lo que prefieras).
2. Ábrelo desde el gestor de archivos.
3. Android te dirá que la app con la que lo abres no tiene permiso para instalar
   aplicaciones. Pulsa **Ajustes**, activa el permiso y vuelve atrás.
4. Instalar.

El paquete es `com.focusdhikr.debug`, así que **no choca** con nada que tengas.

## 3. Conceder los permisos

La app te guía por los cuatro. Los dos primeros son obligatorios:

| Permiso | Para qué | Dónde |
|---|---|---|
| **Acceso al uso** | Contar el tiempo de cada app | Ajustes → Apps → Acceso especial → Acceso al uso |
| **Mostrar sobre otras apps** | Dibujar la pantalla de pausa | Ajustes → Apps → Acceso especial → Mostrar sobre otras apps |
| **Accesibilidad** *(recomendado)* | Reaccionar al instante en vez de en 1–2 s | Ajustes → Accesibilidad → Focus Dhikr |
| **Batería sin restricciones** *(opcional)* | Que Android no mate el contador | Ajustes → Apps → Focus Dhikr → Batería |

### ⚠️ Android 13 o superior: el permiso de accesibilidad aparecerá bloqueado

Esto no es un fallo. Desde Android 13, la accesibilidad es un *ajuste
restringido* para las apps instaladas por APK, y el sistema no te deja
activarla hasta que lo desbloqueas a mano:

1. Ajustes → Aplicaciones → **Focus Dhikr**
2. Menú **⋮** arriba a la derecha
3. **Permitir ajustes restringidos**
4. Vuelve a Ajustes → Accesibilidad → Focus Dhikr y actívalo

Si no lo haces, la app **sigue funcionando**, solo que con uno o dos segundos de
retraso: verás el feed un instante antes de que aparezca la pausa.

## 4. Si tu móvil es Xiaomi, Huawei, Samsung, Oppo, OnePlus o Vivo

Estos fabricantes matan procesos en segundo plano con reglas propias que no
están documentadas. Busca en los ajustes de tu móvil:

- **Inicio automático** / *Autostart* → activarlo para Focus Dhikr
- **Ahorro de batería** para esta app → *Sin restricciones*
- **Bloquear en recientes** → mantener pulsada la app en multitarea y fijarla

La web [dontkillmyapp.com](https://dontkillmyapp.com) tiene instrucciones
concretas por fabricante y modelo.

---

## Compilarlo tú mismo (opcional)

Si prefieres compilarlo en tu ordenador:

```bash
git clone https://github.com/mahamedsillah2-cloud/Focus-Dhikr.git
cd Focus-Dhikr
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

Necesitas JDK 17 y el SDK de Android (API 35). Android Studio los trae.

Ejecutar solo las pruebas de la lógica, sin dispositivo ni emulador:

```bash
./gradlew testDebugUnitTest
```

Verificar las citas contra sus fuentes (necesita conexión):

```bash
python3 tools/verify_citations.py
```

---

## Desinstalar

Ajustes → Aplicaciones → Focus Dhikr → Desinstalar.

Si activaste la opción de **dificultar la desinstalación**, primero tienes que
desactivarla en Ajustes → Seguridad → Aplicaciones de administración de
dispositivos. Eso es exactamente lo que se supone que tiene que costarte.
