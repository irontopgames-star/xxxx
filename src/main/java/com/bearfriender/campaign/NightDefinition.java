package com.bearfriender.campaign;

import java.util.List;

/**
 * Definición de cada noche: textos, parámetros de comportamiento para la IA (Fase 8) y eventos programados.
 * "at" es la fracción de la noche (0.0 al comenzar, 1.0 al terminar) en la que ocurre cada evento.
 */
public record NightDefinition(int number, String title, String subtitle,
                              double stalkMin, double stalkMax, int appearCooldownSec, float tvUseChance,
                              boolean chaseAllowed, int chaseMaxSec, boolean opensDoors,
                              List<ScheduledEvent> events) {

    /** Un evento programado. dist/ticks solo los usan SHADOW y similares; text solo HINT. */
    public record ScheduledEvent(float at, EventType type, int intensity, int minDist, int maxDist, int ticks, String text) {}

    private static ScheduledEvent ev(float at, EventType t, int intensity) {
        return new ScheduledEvent(at, t, intensity, 0, 0, 0, "");
    }

    private static ScheduledEvent shadow(float at, int min, int max, int ticks) {
        return new ScheduledEvent(at, EventType.SHADOW, 1, min, max, ticks, "");
    }

    private static ScheduledEvent hint(float at, String text) {
        return new ScheduledEvent(at, EventType.HINT, 1, 0, 0, 0, text);
    }

    public static final List<NightDefinition> ALL = List.of(
            new NightDefinition(1, "NOCHE 1", "Algo nos observa",
                    30, 45, 90, 0.10F, false, 0, false, List.of(
                    ev(0.08F, EventType.DOOR_SLAM, 1),
                    ev(0.20F, EventType.DISTANT_KNOCKS, 2),
                    ev(0.26F, EventType.TV_FLICKER, 1),
                    shadow(0.30F, 18, 32, 90),
                    ev(0.34F, EventType.LIGHT_FLICKER, 1),
                    shadow(0.48F, 16, 30, 90),
                    ev(0.58F, EventType.DISTANT_KNOCKS, 3),
                    ev(0.70F, EventType.DOOR_SLAM, 1),
                    ev(0.78F, EventType.TV_FLICKER, 1),
                    shadow(0.86F, 14, 28, 80),
                    ev(0.96F, EventType.TV_FACE_SCARE, 1))),
            new NightDefinition(2, "NOCHE 2", "Las cintas",
                    18, 30, 60, 0.30F, true, 8, false, List.of(
                    hint(0.06F, "En algún lugar, una cinta se rebobina..."),
                    ev(0.10F, EventType.TV_FLICKER, 2),
                    ev(0.20F, EventType.TV_VOICES, 1),
                    shadow(0.16F, 14, 26, 80),
                    ev(0.30F, EventType.DOOR_SLAM, 1),
                    shadow(0.40F, 14, 24, 80),
                    ev(0.48F, EventType.TV_FLICKER, 3),
                    hint(0.55F, "Las cintas guardan pedazos de él."),
                    ev(0.62F, EventType.TV_VOICES, 2),
                    ev(0.72F, EventType.LIGHT_FLICKER, 2),
                    shadow(0.82F, 10, 18, 70),
                    ev(0.90F, EventType.TV_FLICKER, 3))),
            new NightDefinition(3, "NOCHE 3", "Ya está dentro",
                    10, 20, 40, 0.50F, true, 14, true, List.of(
                    ev(0.05F, EventType.DOOR_SLAM, 2),
                    ev(0.12F, EventType.LIGHT_FLICKER, 2),
                    ev(0.20F, EventType.TV_FLICKER, 3),
                    shadow(0.22F, 8, 16, 70),
                    shadow(0.28F, 8, 14, 70),
                    ev(0.35F, EventType.STEPS, 1),
                    ev(0.42F, EventType.DOOR_SLAM, 2),
                    ev(0.50F, EventType.BLACKOUT, 1),
                    shadow(0.58F, 6, 12, 60),
                    ev(0.66F, EventType.TV_VOICES, 2),
                    ev(0.75F, EventType.LIGHT_FLICKER, 3),
                    shadow(0.85F, 6, 10, 55),
                    ev(0.93F, EventType.TV_FLICKER, 4))),
            new NightDefinition(4, "NOCHE 4", "No quiere que te vayas",
                    6, 14, 25, 0.75F, true, 22, true, List.of(
                    ev(0.04F, EventType.STEPS, 1),
                    ev(0.10F, EventType.DOOR_SLAM, 3),
                    shadow(0.13F, 6, 12, 50),
                    ev(0.16F, EventType.TV_ALL_FACE, 1),
                    ev(0.24F, EventType.BLACKOUT, 1),
                    ev(0.30F, EventType.STEPS, 2),
                    shadow(0.38F, 6, 12, 50),
                    ev(0.45F, EventType.DISTANT_KNOCKS, 4),
                    ev(0.52F, EventType.TV_ALL_FACE, 1),
                    ev(0.60F, EventType.LIGHT_FLICKER, 3),
                    shadow(0.68F, 6, 10, 45),
                    ev(0.76F, EventType.BLACKOUT, 1),
                    ev(0.84F, EventType.STEPS, 2),
                    ev(0.92F, EventType.TV_ALL_FACE, 1),
                    ev(0.97F, EventType.DOOR_SLAM, 4))),
            new NightDefinition(5, "NOCHE 5", "El último amigo",
                    0, 0, 0, 1.0F, true, 40, true, List.of())
    );

    public static NightDefinition of(int night) {
        return ALL.get(Math.max(1, Math.min(5, night)) - 1);
    }
}
