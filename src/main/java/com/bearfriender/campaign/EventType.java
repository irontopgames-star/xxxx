package com.bearfriender.campaign;

/** Eventos programados de una noche. Cada uno tiene su efecto real en CampaignEvents. */
public enum EventType {
    DOOR_SLAM,        // una puerta se cierra de golpe cerca del jugador
    DISTANT_KNOCKS,   // golpes lejanos en puertas
    LIGHT_FLICKER,    // las luces cercanas parpadean
    BLACKOUT,         // apagón breve de toda la instalación
    TV_FLICKER,       // televisores cercanos se encienden solos un momento
    TV_VOICES,        // un televisor emite risas distorsionadas
    SHADOW,           // Bearfriender aparece a lo lejos unos segundos
    TV_FACE_SCARE,    // un televisor muestra su rostro y se apaga si te acercas
    TV_ALL_FACE,      // todos los televisores muestran su rostro a la vez
    STEPS,            // pasos en habitaciones vacías
    HINT              // mensaje breve sobre la historia
}
