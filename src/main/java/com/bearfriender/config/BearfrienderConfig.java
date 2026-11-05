package com.bearfriender.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Configuración del servidor: valores que el desarrollador puede ajustar sin recompilar. */
public final class BearfrienderConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue NIGHT_DURATION_SECONDS;
    public static final ForgeConfigSpec.IntValue ELECTRIC_COMPONENTS_REQUIRED;
    public static final ForgeConfigSpec.EnumValue<DeathPolicy> DEATH_POLICY;
    public static final ForgeConfigSpec.BooleanValue KEEP_TRANSMITTER_PROGRESS;
    public static final ForgeConfigSpec.BooleanValue AUTO_START;

    public enum DeathPolicy { RESTART_ENCOUNTER, RESTART_NIGHT }

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("campaign");
        NIGHT_DURATION_SECONDS = b.comment("Duración de cada noche en segundos (480 = 8 min).")
                .defineInRange("nightDurationSeconds", 480, 60, 3600);
        AUTO_START = b.comment("Inicia la campaña sola cuando un jugador entra en la instalación.")
                .define("autoStart", false);
        DEATH_POLICY = b.comment("Qué se reinicia al morir. El progreso de misiones se conserva siempre.")
                .defineEnum("deathPolicy", DeathPolicy.RESTART_ENCOUNTER);
        b.pop();

        b.push("missions");
        ELECTRIC_COMPONENTS_REQUIRED = b.comment("Componentes eléctricos necesarios para la misión 1.")
                .defineInRange("electricComponentsRequired", 3, 1, 8);
        KEEP_TRANSMITTER_PROGRESS = b.comment("Si el progreso del transmisor se conserva al salir de la sala.")
                .define("keepTransmitterProgress", true);
        b.pop();

        SPEC = b.build();
    }

    private BearfrienderConfig() {}
}
