import Foundation

/// The non-religious half of the voice: short lines meant to buy a few seconds
/// of attention.
///
/// Editorial rules:
///  - Describe, never accuse. "Estás a punto de..." not "Otra vez has...".
///  - Ask, do not instruct.
///  - Never call the user weak, addicted, or a failure.
///  - Short enough to read fully in the two seconds someone impulsive will give.
public enum Reflections {

    /// Phase 1: one line under the numbers.
    public static let pause = [
        "Antes de entrar, pregúntate: ¿de verdad quiero esto o solo busco una distracción?",
        "Este momento es tuyo. Todavía no has decidido nada.",
        "Tu yo de esta mañana decidió este límite con la cabeza fría.",
        "No pasa nada por parar aquí. Parar también es una decisión.",
        "¿Qué estabas haciendo justo antes de coger el móvil?",
        "Un par de segundos de atención. Nada más te pide esta pantalla.",
    ]

    /// Phase 3: shown during the countdown, alongside the dhikr.
    public static let wait = [
        "Respira. No tienes que decidir con prisa.",
        "El impulso sube y baja. Ahora mismo está bajando.",
        "Nadie te está esperando ahí dentro.",
        "Cuando esto termine seguirás pudiendo entrar. Es tu elección.",
        "Unos segundos de silencio no le quitan nada a tu día.",
    ]

    /// Phase 4: framing above the user's own goal.
    public static let purpose = [
        "Recuerda por qué decidiste limitar esta aplicación.",
        "Esto es lo que dijiste que querías de tu tiempo.",
        "Tu decisión de antes, con tus propias palabras.",
    ]

    /// After the user turns back. Warm, brief, never smug.
    public static let turnedBack = [
        "Has recuperado este rato.",
        "Bien. Vuelve a lo tuyo.",
        "Ese momento era el difícil, y ha pasado.",
        "Hecho. Sigue con tu día.",
    ]

    /// Occasional standalone reminders.
    public static let ambient = [
        "Recuerda a Allah.",
        "Una pausa también puede ser una decisión.",
        "Tu tiempo es limitado.",
        "¿Qué podrías hacer ahora que de verdad te beneficie?",
        "SubhanAllah, Alhamdulillah, Allahu Akbar.",
        "¿Sigue siendo esto lo que querías hacer?",
        "El día todavía da para algo bueno.",
    ]

    /// Deterministic pick so a screen does not reshuffle on every redraw.
    public static func pick(_ pool: [String], seed: Int) -> String {
        pool[abs(seed) % pool.count]
    }
}
