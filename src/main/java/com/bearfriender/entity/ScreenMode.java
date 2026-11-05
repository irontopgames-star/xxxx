package com.bearfriender.entity;

/** Imagen mostrada por la pantalla CRT del abdomen (el renderizador la usa en la Fase 3). */
public enum ScreenMode {
    OFF, STATIC, FACE, SMILE, INTERFERENCE;

    public static ScreenMode byId(int id) {
        ScreenMode[] v = values();
        return v[Math.floorMod(id, v.length)];
    }
}
