package com.bearfriender.campaign;

/** Las tres misiones principales. La Fase 6 las completa de verdad; la campaña solo consulta su estado. */
public enum MissionId {
    POWER("power", "Restaurar el sistema eléctrico"),
    TAPES("tapes", "Destruir las cintas malditas"),
    SIGNAL("signal", "Desactivar la señal");

    public final String id;
    public final String title;

    MissionId(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public int bit() { return 1 << ordinal(); }

    public static MissionId byId(String id) {
        for (MissionId m : values()) if (m.id.equalsIgnoreCase(id)) return m;
        return null;
    }
}
