package com.bearfriender.entity.anim;

/**
 * Una animación por fotogramas clave para el esqueleto de Bearfriender.
 * Cada hueso puede tener una pista de rotación (grados), posición (px) y escala.
 * Es lógica pura, sin clases de Minecraft.
 */
public final class BearAnimation {
    public static final int LOOP_ONCE = 0;
    public static final int LOOP = 1;
    public static final int HOLD = 2;

    /** Pista de fotogramas clave de un canal (3 valores por fotograma). */
    public static final class Track {
        public final float[] times;
        public final float[][] values;

        public Track(float[] times, float[][] values) {
            this.times = times;
            this.values = values;
        }

        /** Interpola linealmente; antes del primer fotograma y después del último mantiene el valor. */
        public void sample(float t, float[] out) {
            int n = times.length;
            if (t <= times[0]) {
                System.arraycopy(values[0], 0, out, 0, 3);
                return;
            }
            if (t >= times[n - 1]) {
                System.arraycopy(values[n - 1], 0, out, 0, 3);
                return;
            }
            int i = 0;
            while (i < n - 2 && t > times[i + 1]) i++;
            float span = times[i + 1] - times[i];
            float f = span > 0 ? (t - times[i]) / span : 0f;
            for (int k = 0; k < 3; k++) {
                out[k] = values[i][k] + (values[i + 1][k] - values[i][k]) * f;
            }
        }
    }

    public final String name;
    public final float length;
    public final int loopMode;
    /** Pistas por índice de hueso; null si el hueso no se anima en ese canal. */
    public final Track[] rot;
    public final Track[] pos;
    public final Track[] scl;

    public BearAnimation(String name, float length, int loopMode, int boneCount) {
        this.name = name;
        this.length = length;
        this.loopMode = loopMode;
        this.rot = new Track[boneCount];
        this.pos = new Track[boneCount];
        this.scl = new Track[boneCount];
    }

    /** Convierte el tiempo transcurrido en el tiempo a muestrear según el modo de bucle. */
    public float localTime(float elapsed) {
        if (loopMode == LOOP) return length > 0 ? elapsed % length : 0f;
        return Math.min(elapsed, length);
    }
}
