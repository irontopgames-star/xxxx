package com.bearfriender.structure;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * Plano de la instalación. Coordenadas locales: x hacia el este, z hacia el sur, y=0 es el suelo de la planta baja.
 * Estilo: juguetería abandonada (paredes a rayas, alfombras de ajedrez, peluches y globos). Los muros miden 1 bloque y salen solos de lo que no se vacía. Interior de planta baja: y 1..5 (techo en y=6).
 * Sótano: suelo en y=-7, interior y -6..-2.
 *
 *   z
 *   0  ┌────────────┬─────────────┬────────────┐
 *      │ Recepción  │ Sala de TVs │ Dormitorio │   fila norte
 *  10  ├──┬──────────────────────────────┬─────┤
 *      │W │  ┌ pasillo en anillo ──────┐ │  E  │   columna oeste / este
 *      │  │  │      SALA CENTRAL       │ │     │
 *  31  ├──┴──┴─────────────────────────┴─┴─────┤
 *      │ Almacén    │ Grabación   │ Eléctrica   │   fila sur
 *  40  └────────────┴─────────────┴─────────────┘
 */
public final class FacilityLayout {
    public static final int SIZE_X = 48;     // incluye el túnel de salida
    public static final int SIZE_Z = 41;
    public static final int MIN_Y = -8;
    public static final int MAX_Y = 6;

    public enum Style { HOUSE, INDUSTRIAL, CORRIDOR, BASEMENT, ARENA }

    /** Sala rectangular; los límites son del interior (incluidos). */
    public record Room(String id, int x0, int x1, int z0, int z1, Style style, boolean basement,
                       Block carpetA, Block carpetB) {
        public int yFloor() { return basement ? -7 : 0; }
        public int yTop() { return basement ? -2 : 5; }
        public int yCeiling() { return basement ? -1 : 6; }
        public int cx() { return (x0 + x1) / 2; }
        public int cz() { return (z0 + z1) / 2; }
    }

    /** Hueco de 2 de ancho en un muro. xWall=true: el muro está en x=fixed y el hueco se extiende en z. */
    public record Door(boolean xWall, int fixed, int start, int yBase) {}

    /** Arco abierto, sin puerta. */
    public record Arch(boolean xWall, int fixed, int start, int width) {}

    private static Room r(String id, int x0, int x1, int z0, int z1, Style s, Block a, Block b) {
        return new Room(id, x0, x1, z0, z1, s, false, a, b);
    }

    private static Room base(String id, int x0, int x1, int z0, int z1, Style s, Block a, Block b) {
        return new Room(id, x0, x1, z0, z1, s, true, a, b);
    }

    /** Primero los pasillos y la sala central, después las habitaciones (así su estilo manda en los muros compartidos). */
    public static final List<Room> ROOMS = List.of(
            // Pasillo en anillo
            r("ring_n", 10, 30, 10, 12, Style.CORRIDOR, Blocks.GRAY_CARPET, Blocks.BLACK_CARPET),
            r("ring_s", 10, 30, 28, 30, Style.CORRIDOR, Blocks.GRAY_CARPET, Blocks.BLACK_CARPET),
            r("ring_w", 10, 12, 13, 27, Style.CORRIDOR, Blocks.GRAY_CARPET, Blocks.BLACK_CARPET),
            r("ring_e", 28, 30, 13, 27, Style.CORRIDOR, Blocks.GRAY_CARPET, Blocks.BLACK_CARPET),
            // Sala central (combate final)
            r("central", 14, 26, 14, 26, Style.ARENA, Blocks.BLACK_CARPET, Blocks.RED_CARPET),
            // Fila norte
            r("reception", 1, 12, 1, 8, Style.HOUSE, Blocks.RED_CARPET, Blocks.WHITE_CARPET),
            r("living", 14, 26, 1, 8, Style.HOUSE, Blocks.BLUE_CARPET, Blocks.YELLOW_CARPET),
            r("bedroom", 28, 39, 1, 8, Style.HOUSE, Blocks.PINK_CARPET, Blocks.LIGHT_BLUE_CARPET),
            // Fila sur
            r("storage", 1, 12, 32, 39, Style.HOUSE, Blocks.ORANGE_CARPET, Blocks.BROWN_CARPET),
            r("recording", 14, 26, 32, 39, Style.INDUSTRIAL, Blocks.BLACK_CARPET, Blocks.GRAY_CARPET),
            r("electrical", 28, 39, 32, 39, Style.INDUSTRIAL, Blocks.GRAY_CARPET, Blocks.BLACK_CARPET),
            // Columna oeste
            r("office", 1, 8, 10, 15, Style.HOUSE, Blocks.GREEN_CARPET, Blocks.WHITE_CARPET),
            r("tapes", 1, 8, 17, 23, Style.HOUSE, Blocks.PURPLE_CARPET, Blocks.BLACK_CARPET),
            r("teddy", 1, 8, 25, 30, Style.HOUSE, Blocks.BLACK_CARPET, Blocks.BROWN_CARPET),
            // Columna este
            r("lab", 32, 39, 10, 18, Style.INDUSTRIAL, Blocks.LIGHT_GRAY_CARPET, Blocks.GRAY_CARPET),
            r("stairwell", 32, 39, 20, 24, Style.HOUSE, Blocks.RED_CARPET, Blocks.WHITE_CARPET),
            r("exit_hall", 32, 39, 26, 30, Style.HOUSE, Blocks.LIME_CARPET, Blocks.WHITE_CARPET),
            // Sótano
            base("basement_hall", 22, 30, 16, 28, Style.BASEMENT, Blocks.BLACK_CARPET, Blocks.GRAY_CARPET),
            base("transmission", 10, 20, 16, 28, Style.BASEMENT, Blocks.GRAY_CARPET, Blocks.BLACK_CARPET),
            // Cámara sellada donde espera Bearfriender cuando no está en escena (sin puertas)
            base("bear_hold", 34, 37, 34, 37, Style.BASEMENT, Blocks.BLACK_CARPET, Blocks.BLACK_CARPET)
    );

    public static final List<Door> DOORS = List.of(
            // Fila norte -> anillo (muro z=9)
            new Door(false, 9, 10, 1), new Door(false, 9, 19, 1), new Door(false, 9, 28, 1),
            // Fila sur -> anillo (muro z=31)
            new Door(false, 31, 10, 1), new Door(false, 31, 19, 1), new Door(false, 31, 28, 1),
            // Columna oeste -> anillo (muro x=9)
            new Door(true, 9, 11, 1), new Door(true, 9, 19, 1), new Door(true, 9, 26, 1),
            // Columna este -> anillo (muro x=31)
            new Door(true, 31, 12, 1), new Door(true, 31, 23, 1), new Door(true, 31, 28, 1),
            // Atajos entre habitaciones (bucles de persecución)
            new Door(true, 13, 4, 1), new Door(true, 27, 4, 1),
            new Door(true, 13, 35, 1), new Door(true, 27, 35, 1),
            // Sótano: sala de transmisión <-> sótano
            new Door(true, 21, 21, -6)
    );

    /** Arcos de 4 de ancho hacia la sala central. */
    public static final List<Arch> ARCHES = List.of(
            new Arch(true, 13, 18, 4), new Arch(true, 27, 18, 4),
            new Arch(false, 13, 18, 4), new Arch(false, 27, 18, 4)
    );

    // Escalera al sótano (en la sala "stairwell"): baja hacia el oeste
    public static final int STAIR_TOP_X = 38;
    public static final int STAIR_STEPS = 7;
    public static final int STAIR_Z0 = 21;
    public static final int STAIR_Z1 = 22;

    // Túnel de salida (al este de la sala "exit_hall")
    public static final int EXIT_WALL_X = 40;
    public static final int EXIT_Z0 = 27;
    public static final int EXIT_Z1 = 28;
    public static final int TUNNEL_X1 = 46;

    private FacilityLayout() {}
}
