package com.bearfriender.block;

import net.minecraft.util.StringRepresentable;

/** Imagen que muestra un televisor CRT. El nombre debe coincidir con el estado de bloque (blockstates/crt_tv.json). */
public enum TvScreen implements StringRepresentable {
    OFF("off", 0),
    STATIC("static", 5),
    FACE("face", 7),
    SMILE("smile", 6),
    INTERFERENCE("interference", 5);

    private final String name;
    private final int light;

    TvScreen(String name, int light) {
        this.name = name;
        this.light = light;
    }

    /** Nivel de luz que emite la pantalla. */
    public int light() { return light; }

    @Override
    public String getSerializedName() { return name; }
}
