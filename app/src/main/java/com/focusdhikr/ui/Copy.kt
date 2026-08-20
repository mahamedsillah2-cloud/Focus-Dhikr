package com.focusdhikr.ui

import com.focusdhikr.domain.model.IntentReason

/**
 * Every user-facing string in one file.
 *
 * The tone of this app is the product, so it is reviewable in a single sitting
 * rather than scattered across thirty composables. Rules, from requirement 15:
 * describe rather than accuse, ask rather than instruct, and never imply the
 * user is weak or broken.
 *
 * (Framework-owned strings - manifest labels, the accessibility description,
 * notification channels - live in res/values/strings.xml, because Android reads
 * those from resources.)
 */
object Copy {

    // --- shared -----------------------------------------------------------

    const val APP_NAME = "Focus Dhikr"
    const val CONTINUE = "Continuar"
    const val BACK = "Atrás"
    const val CANCEL = "Cancelar"
    const val SAVE = "Guardar"
    const val DELETE = "Eliminar"
    const val DONE = "Hecho"
    const val CLOSE = "Cerrar"

    // --- navigation -------------------------------------------------------

    const val TAB_TODAY = "Hoy"
    const val TAB_APPS = "Aplicaciones"
    const val TAB_STATS = "Historial"
    const val TAB_SETTINGS = "Ajustes"

    // --- onboarding -------------------------------------------------------

    const val ONBOARD_TITLE = "Recupera tu atención"
    const val ONBOARD_SUBTITLE =
        "Tú decides los límites. Cuando llegues a ellos, esta aplicación te dará unos segundos para pensar antes de entrar."
    const val ONBOARD_HONEST_TITLE = "Lo que esta aplicación puede y no puede hacer"
    const val ONBOARD_HONEST_BODY =
        "Puede contar tu tiempo, avisarte y ponerte una pausa delante en el momento exacto.\n\n" +
            "No puede impedirte desinstalarla ni quitarle los permisos. Ninguna aplicación normal de Android puede.\n\n" +
            "Lo que sí hace es convertir un gesto automático en una decisión consciente."
    const val ONBOARD_PRIVACY_TITLE = "Todo se queda en este móvil"
    const val ONBOARD_PRIVACY_BODY =
        "Focus Dhikr ni siquiera tiene permiso de acceso a internet. No es una promesa: es el sistema operativo quien lo impide.\n\n" +
            "Sin anuncios, sin cuentas, sin analítica, sin rastreadores."

    const val PERM_TITLE = "Permisos necesarios"
    const val PERM_USAGE_TITLE = "Acceso al uso"
    const val PERM_USAGE_BODY =
        "Para poder contar cuánto tiempo pasas en cada aplicación. Sin esto no hay nada que contar."
    const val PERM_OVERLAY_TITLE = "Mostrar sobre otras aplicaciones"
    const val PERM_OVERLAY_BODY =
        "Para poder dibujar tu pantalla de pausa encima de la aplicación que abres."
    const val PERM_ACCESSIBILITY_TITLE = "Accesibilidad (recomendado)"
    const val PERM_ACCESSIBILITY_BODY =
        "Sin esto la pausa tarda uno o dos segundos: verás el contenido un instante antes de que aparezca. Con esto, aparece al momento."
    const val PERM_BATTERY_TITLE = "Batería sin restricciones (opcional)"
    const val PERM_BATTERY_BODY =
        "Android puede detener el contador para ahorrar batería. Esto lo evita."
    const val PERM_GRANT = "Conceder"
    const val PERM_GRANTED = "Concedido"
    const val PERM_OPEN_SETTINGS = "Abrir ajustes"
    const val ONBOARD_START = "Empezar"

    // --- home -------------------------------------------------------------

    const val HOME_GREETING = "Hoy"
    const val HOME_USED_TODAY = "Tiempo usado hoy"
    const val HOME_RECLAIMED_TODAY = "Tiempo recuperado hoy"
    const val HOME_TURNED_BACK = "Veces que has dado la vuelta"
    const val HOME_BLOCKED_APPS = "Aplicaciones limitadas"
    const val HOME_NO_APPS_TITLE = "Todavía no has elegido ninguna aplicación"
    const val HOME_NO_APPS_BODY =
        "Elige las que más te distraen y ponles el tiempo que tú consideres justo."
    const val HOME_CHOOSE_APPS = "Elegir aplicaciones"
    const val HOME_REMAINING = "restante"
    const val HOME_LIMIT_REACHED = "límite alcanzado"
    const val HOME_OF = "de"
    const val HOME_STRICT_ON = "Modo «no me dejes entrar» activo"
    const val HOME_WINDOW_ACTIVE = "Franja activa"

    // --- apps -------------------------------------------------------------

    const val APPS_TITLE = "Aplicaciones"
    const val APPS_LIMITED = "Con límite"
    const val APPS_ADD = "Añadir aplicación"
    const val APPS_PICK_TITLE = "Elige una aplicación"
    const val APPS_SEARCH = "Buscar"
    const val APPS_SUGGESTED = "Las más habituales"
    const val APPS_ALL = "Todas"
    const val APPS_DAILY_LIMIT = "Límite diario"
    const val APPS_STRICT_THIS_APP = "Modo estricto solo para esta aplicación"
    const val APPS_REMOVE = "Quitar el límite"
    const val APPS_ENABLED = "Límite activo"

    // --- the gate ---------------------------------------------------------

    const val GATE_STEP = "Paso"
    const val GATE_LIMIT_TITLE = "Has alcanzado el tiempo que tú mismo decidiste para esta aplicación."
    const val GATE_WINDOW_TITLE = "Estás dentro de una franja que decidiste dejar libre."
    const val GATE_USED_TODAY = "Usado hoy"
    const val GATE_YOUR_LIMIT = "Tu límite"
    const val GATE_AVAILABLE_IN = "Vuelve a estar disponible en"
    const val GATE_AVAILABLE_AT = "Vuelve a estar disponible a las"

    const val GATE_ENTER_ANYWAY = "Quiero entrar igualmente"
    const val GATE_TURN_BACK = "Dejarlo por ahora"
    const val GATE_NOT_NOW = "Ahora no"

    const val GATE_INTENT_QUESTION = "¿Qué ibas a hacer exactamente?"
    const val GATE_ALIGNMENT_QUESTION = "¿Esto te acerca a lo que quieres conseguir hoy?"
    const val GATE_ALIGNMENT_YES = "Sí"
    const val GATE_ALIGNMENT_NO = "No"
    const val GATE_ALIGNMENT_UNSURE = "No estoy seguro"

    const val GATE_WAIT_TITLE = "Espera unos segundos antes de continuar."
    const val GATE_WAIT_QUESTION =
        "¿Quieres usar conscientemente este tiempo o estás actuando por impulso?"

    const val GATE_PURPOSE_TITLE = "Recuerda por qué decidiste limitar esta aplicación."
    const val GATE_PURPOSE_NO_GOALS =
        "Todavía no has escrito ningún objetivo. Puedes añadirlos cuando quieras en la pestaña Hoy."

    const val GATE_WRITE_TITLE = "Escribe con tus propias palabras por qué quieres entrar."
    const val GATE_WRITE_PLACEHOLDER = "Escribe aquí…"
    const val GATE_WRITE_MIN = "Al menos %d caracteres"

    const val GATE_DECIDE_TITLE =
        "Estás eligiendo conscientemente usar esta aplicación aunque hayas alcanzado tu límite."
    const val GATE_DECIDE_INSTRUCTION = "Para continuar, copia esta frase:"
    const val GATE_DECIDE_PLACEHOLDER = "Copia la frase"
    const val GATE_OPEN_APP = "Abrir de todos modos"

    const val GATE_GRANTED = "Tienes %s. Después volverá a bloquearse."
    const val GATE_TRANSLATION_LABEL = "Traducción del significado"

    const val GATE_EMERGENCY = "Lo necesito por trabajo"
    const val GATE_EMERGENCY_TITLE = "Acceso de emergencia"
    const val GATE_EMERGENCY_BODY =
        "Esto es para cuando de verdad lo necesitas. Te quedan %d esta semana."
    const val GATE_EMERGENCY_REASON = "¿Para qué lo necesitas?"
    const val GATE_EMERGENCY_NONE_LEFT =
        "Has usado todos los accesos de emergencia de esta semana. Se renuevan pasados siete días."
    const val GATE_EMERGENCY_CONFIRM = "Conceder acceso"

    fun intentLabel(reason: IntentReason): String = when (reason) {
        IntentReason.CONCRETE_REASON -> "Tengo una razón concreta"
        IntentReason.BORED -> "Estoy aburrido"
        IntentReason.SEEKING_DISTRACTION -> "Estoy buscando una distracción"
        IntentReason.HABIT -> "Lo he abierto por costumbre"
        IntentReason.DONT_KNOW -> "No sé por qué lo he abierto"
    }

    // --- goals ------------------------------------------------------------

    const val GOALS_TITLE = "Tus objetivos"
    const val GOALS_SUBTITLE = "Lo que quieres que haga tu tiempo cuando no está en una pantalla."
    const val GOALS_ADD = "Añadir objetivo"
    const val GOALS_EMPTY = "Todavía no has escrito ninguno."
    const val GOALS_TITLE_FIELD = "Objetivo"
    const val GOALS_NOTE_FIELD = "Detalle (opcional)"
    const val GOALS_ACTIVE = "Recordármelo en las pausas"

    val GOAL_SUGGESTIONS: List<String> = listOf(
        "Estudiar",
        "Trabajar",
        "Entrenar",
        "Leer",
        "Pasar tiempo con mi familia",
        "Aprender algo nuevo",
        "Dormir mejor",
        "Mejorar mi disciplina",
        "Dedicar tiempo a mi religión",
    )

    // --- stats ------------------------------------------------------------

    const val STATS_TITLE = "Historial"
    const val STATS_TODAY = "Hoy"
    const val STATS_WEEK = "7 días"
    const val STATS_MONTH = "30 días"
    const val STATS_RECLAIMED = "Tiempo recuperado"
    const val STATS_TURNED_BACK = "Has dado la vuelta"
    const val STATS_TIMES = "veces"
    const val STATS_USED = "Tiempo en aplicaciones limitadas"
    const val STATS_BY_APP = "Por aplicación"
    const val STATS_EMPTY = "Aún no hay suficiente historial. Vuelve en unos días."
    const val STATS_RECLAIMED_EXPLAIN =
        "Tiempo recuperado es el que habrías pasado dentro si no hubieras dado la vuelta, estimado con la duración media de tus sesiones."

    // --- settings ---------------------------------------------------------

    const val SETTINGS_TITLE = "Ajustes"
    const val SETTINGS_SECTION_STRICT = "Modo «no me dejes entrar»"
    const val SETTINGS_STRICT_TOGGLE = "Activar modo estricto"
    const val SETTINGS_STRICT_BODY =
        "Todas las fases, esperas más largas y confirmación escrita siempre."
    const val SETTINGS_STRICT_COOLDOWN = "Retardo al desactivarlo"
    const val SETTINGS_STRICT_COOLDOWN_BODY =
        "Si lo desactivas, el cambio no se aplica hasta pasado este tiempo. Decídelo ahora, en frío."
    const val SETTINGS_WINDOWS = "Franjas horarias"
    const val SETTINGS_WINDOWS_BODY = "Por ejemplo, de 22:00 a 08:00."
    const val SETTINGS_ADD_WINDOW = "Añadir franja"

    const val SETTINGS_SECTION_SPIRITUAL = "Recordatorios islámicos"
    const val SETTINGS_SPIRITUAL_DEPTH = "Presencia en las pausas"
    const val SETTINGS_DHIKR_PICK = "Adhkar que quieres ver"
    const val SETTINGS_SHOW_TRANSLATIONS = "Mostrar traducción"
    const val SETTINGS_REMINDERS = "Recordatorios ocasionales"
    const val SETTINGS_QUIET_HOURS = "Horas de silencio"

    const val SETTINGS_SECTION_GATE = "La pausa"
    const val SETTINGS_GRANT_MINUTES = "Tiempo que se concede al continuar"
    const val SETTINGS_SENTENCE = "Frase de reconocimiento"
    const val SETTINGS_SENTENCE_BODY = "La que tendrás que copiar en el último paso."
    const val SETTINGS_DAY_RESET = "El día empieza a las"
    const val SETTINGS_DAY_RESET_BODY =
        "Si trasnochas, medianoche corta tu noche por la mitad y te devuelve el límite en el peor momento."

    const val SETTINGS_SECTION_EMERGENCY = "Emergencias"
    const val SETTINGS_EMERGENCY_USES = "Accesos por semana"
    const val SETTINGS_EMERGENCY_WAIT = "Espera antes de concederlo"

    const val SETTINGS_SECTION_PRIVACY = "Privacidad"
    const val SETTINGS_KEEP_REASONS = "Guardar lo que escribo en las pausas"
    const val SETTINGS_KEEP_REASONS_BODY =
        "Se guarda solo en este móvil. Si lo desactivas, el texto se usa y se descarta."
    const val SETTINGS_FORGET_REASONS = "Olvidar lo que he escrito"
    const val SETTINGS_RETENTION = "Conservar historial"
    const val SETTINGS_ERASE = "Borrar todos mis datos"
    const val SETTINGS_ERASE_CONFIRM =
        "Se borrará el historial, las estadísticas y los ajustes de este móvil. No se puede deshacer."
    const val SETTINGS_NO_INTERNET =
        "Esta aplicación no tiene permiso de acceso a internet. Puedes comprobarlo en los ajustes de Android."

    const val SETTINGS_SECTION_PERMISSIONS = "Permisos y fiabilidad"
    const val SETTINGS_SECTION_ADMIN = "Dificultar la desinstalación"
    const val SETTINGS_ADMIN_BODY =
        "Mientras esté activo, Android no te dejará desinstalar Focus Dhikr sin desactivarlo antes en Ajustes › Seguridad. Es fricción, no un candado."
    const val SETTINGS_ADMIN_ENABLE = "Activar"
    const val SETTINGS_ADMIN_DISABLE = "Desactivar"

    const val SETTINGS_SECTION_ABOUT = "Acerca de"
    const val SETTINGS_SOURCES = "Fuentes de las citas"
    const val SETTINGS_SOURCES_BODY =
        "Cada aleya lleva su sura y número. Cada hadiz, su colección, su número y su grado de autenticidad."
}
