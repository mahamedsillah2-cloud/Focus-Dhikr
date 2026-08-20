package com.focusdhikr.content

/**
 * The non-religious half of the voice: short lines meant to buy a few seconds of
 * attention.
 *
 * Editorial rules, from requirement 15:
 *  - Describe, never accuse. "Estás a punto de..." not "Otra vez has...".
 *  - Ask, do not instruct.
 *  - Never call the user weak, addicted, or a failure.
 *  - Short enough to read fully in the two seconds someone impulsive will give.
 */
object Reflections {

    /** Phase 1: one line under the numbers. */
    val pause: List<String> = listOf(
        "Antes de entrar, pregúntate: ¿de verdad quiero esto o solo busco una distracción?",
        "Este momento es tuyo. Todavía no has decidido nada.",
        "Tu yo de esta mañana decidió este límite con la cabeza fría.",
        "No pasa nada por parar aquí. Parar también es una decisión.",
        "¿Qué estabas haciendo justo antes de coger el móvil?",
        "Un par de segundos de atención. Nada más te pide esta pantalla.",
    )

    /** Phase 3: shown during the countdown, alongside the dhikr. */
    val wait: List<String> = listOf(
        "Respira. No tienes que decidir con prisa.",
        "El impulso sube y baja. Ahora mismo está bajando.",
        "Nadie te está esperando ahí dentro.",
        "Cuando esto termine seguirás pudiendo entrar. Es tu elección.",
        "Unos segundos de silencio no le quitan nada a tu día.",
    )

    /** Phase 4: framing above the user's own goal. */
    val purpose: List<String> = listOf(
        "Recuerda por qué decidiste limitar esta aplicación.",
        "Esto es lo que dijiste que querías de tu tiempo.",
        "Tu decisión de antes, con tus propias palabras.",
    )

    /** Phase 6: shown next to the final confirmation. */
    val decide: List<String> = listOf(
        "Estás eligiendo conscientemente usar esta aplicación aunque hayas alcanzado tu límite.",
    )

    /** After the user turns back. Warm, brief, never smug. */
    val turnedBack: List<String> = listOf(
        "Has recuperado este rato.",
        "Bien. Vuelve a lo tuyo.",
        "Ese momento era el difícil, y ha pasado.",
        "Hecho. Sigue con tu día.",
    )

    /** Occasional standalone reminders (requirement 6). */
    val ambient: List<String> = listOf(
        "Recuerda a Allah.",
        "Una pausa también puede ser una decisión.",
        "Tu tiempo es limitado.",
        "¿Qué podrías hacer ahora que de verdad te beneficie?",
        "SubhanAllah, Alhamdulillah, Allahu Akbar.",
        "¿Sigue siendo esto lo que querías hacer?",
        "El día todavía da para algo bueno.",
    )

    /** Deterministic pick so a screen does not reshuffle on every recomposition. */
    fun pick(pool: List<String>, seed: Int): String = pool[Math.floorMod(seed, pool.size)]
}
