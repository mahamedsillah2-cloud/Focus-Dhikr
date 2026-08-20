# Citas: de dónde sale cada una

Requisito 5: **ninguna cita inventada.** Este documento existe para que puedas
comprobar cada una tú mismo.

## Cómo se garantiza

Tres capas, no una:

1. **Ninguna cita se escribe de memoria.** Cada entrada se comprobó contra una
   fuente primaria antes de escribirla en el código.
2. **Pruebas estructurales** (`ContentIntegrityTest`): ninguna aleya puede
   llegar a una pantalla sin sura y número; ningún hadiz sin colección, número
   y grado. Si alguien edita el archivo y se deja un campo, falla la build.
3. **Validador contra la fuente** (`tools/verify_citations.py`, que corre en CI
   y además una vez por semana): descarga cada aleya de la API de Quran.com y
   compara el árabe carácter a carácter sobre el esqueleto normalizado, y
   comprueba que cada referencia de hadiz resuelve en sunnah.com y contiene el
   árabe citado. **Si una cita no coincide, la build falla.**
4. **Contraste entre plataformas.** El árabe de Android (Kotlin) y el de iOS
   (Swift) tiene que ser **idéntico byte a byte**. El fichero de Swift se genera
   desde el de Kotlin precisamente para eso, y el validador comprueba que sigan
   coincidiendo: una cita correcta en Android y sutilmente mal en iOS es peor
   que una mal en las dos, porque el error se esconde detrás de una comprobación
   que pasa.

El validador acepta las dos ortografías estándar (uthmani e imla'i), porque
escriben la misma palabra de forma distinta —por ejemplo `مَسْـُٔولًا` frente a
`مَسْئُولًا`— sin que cambie la lectura.

> Las traducciones al español son **traducción del significado**, no sustituyen
> al árabe, y la aplicación las etiqueta así en pantalla.

---

## Corán

| Referencia | Sura | Tema |
|---|---|---|
| 5:1 | Al-Ma'ida | Cumplir los compromisos |
| 17:34 | Al-Isra | Cumplir el pacto |
| 103:1-3 | Al-'Asr | El tiempo, la paciencia |
| 13:28 | Ar-Ra'd | El recuerdo de Allah |
| 79:40-41 | An-Nazi'at | Contener el deseo |
| 2:153 | Al-Baqara | Paciencia |
| 75:36 | Al-Qiyama | El propósito |
| 33:41 | Al-Ahzab | El recuerdo abundante |
| 57:16 | Al-Hadid | «¿No ha llegado ya el momento…?» |
| 29:69 | Al-'Ankabut | El esfuerzo |
| 61:2-3 | As-Saff | Decir y no hacer |
| 23:1-3 | Al-Mu'minun | Apartarse de lo vano |
| 94:5-6 | Ash-Sharh | Junto a la dificultad, facilidad |
| 8:27 | Al-Anfal | No traicionar lo confiado |
| 2:286 | Al-Baqara | Allah no impone más de lo soportable |
| 20:14 | Ta-Ha | La oración para Su recuerdo |

Fuente de verificación: <https://api.quran.com/api/v4/verses/by_key/{sura}:{aleya}>

---

## Hadices

Cada uno con su grado y quién lo dio. Sin excepciones.

| Colección | Nº | Narrador | Grado | Fuente |
|---|---|---|---|---|
| Sahih al-Bujari | 6412 | Ibn 'Abbas | Sahih | [sunnah.com/bukhari:6412](https://sunnah.com/bukhari:6412) |
| Yami' at-Tirmidhi | 2417 | Abu Barza al-Aslami | Hasan sahih | [sunnah.com/tirmidhi:2417](https://sunnah.com/tirmidhi:2417) |
| Yami' at-Tirmidhi | 2317 | Abu Huraira | Hasan (garib por esta vía, según At-Tirmidhi) | [sunnah.com/tirmidhi:2317](https://sunnah.com/tirmidhi:2317) |
| Sahih al-Bujari | 6464 | 'A'isha | Sahih | [sunnah.com/bukhari:6464](https://sunnah.com/bukhari:6464) |
| Sahih al-Bujari | 6682 | Abu Huraira | Sahih (también Muslim 2694) | [sunnah.com/bukhari:6682](https://sunnah.com/bukhari:6682) |
| Al-Mustadrak (Al-Hakim) | — | Ibn 'Abbas | Sahih según Al-Hakim; hasan según Al-'Iraqi | recogido también por Al-Bayhaqi, *Shu'ab al-Iman* |
| Sahih al-Bujari | 6114 | Abu Huraira | Sahih (también Muslim 2609) | [sunnah.com/bukhari:6114](https://sunnah.com/bukhari:6114) |

### Nota sobre «اغتنم خمسا قبل خمس»

Es el único que no está en las nueve colecciones canónicas, así que su
referencia no puede comprobarse automáticamente. Por eso:

- el validador lo marca explícitamente como *no verificable por máquina* en vez
  de darlo por bueno en silencio;
- la app muestra siempre su cadena de atribución completa junto al texto;
- se conserva porque su grado está documentado por Al-Hakim y corroborado
  después, no porque «suene bien».

---

## Añadir una cita nueva

1. Búscala en una fuente primaria. **No de memoria, ni de una imagen, ni de una
   captura de pantalla.**
2. Añádela a `QuranLibrary.kt` o `HadithLibrary.kt` con todos los campos.
3. Ejecuta `python3 tools/verify_citations.py`.
4. Si no pasa, **quítala**. No la ajustes hasta que pase.

Una aplicación con dieciséis aleyas que puede defender vale más que una con
cuarenta que no.
