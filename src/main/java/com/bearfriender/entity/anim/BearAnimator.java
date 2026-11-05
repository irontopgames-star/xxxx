package com.bearfriender.entity.anim;

import java.util.Map;

/**
 * Reproductor de animaciones de Bearfriender (solo cliente en la práctica, pero sin dependencias de cliente).
 *
 * Capas:
 *   1) Locomoción: animación de bucle elegida según el estado; al cambiar se mezcla con la anterior.
 *   2) Acción: animación puntual (ataque, grito...) que sustituye los canales que anima, con entrada y salida suaves.
 *
 * Salida: rotaciones (grados), posiciones (px) y escalas por hueso, 3 valores por hueso.
 */
public final class BearAnimator {
    private static final float FADE = 0.25f;
    private static final float ACTION_IN = 0.12f;
    private static final float ACTION_OUT = 0.25f;

    private int bones = -1;
    private float[] rot, pos, scl;
    private final float[] a = new float[3], b = new float[3];
    private final float[] pr = new float[3], pp = new float[3], ps = new float[3];

    private String curName = "";
    private BearAnimation cur, prev;
    private float curT, prevT, fade = 1f;

    private BearAnimation action;
    private float actionT;
    private String pendingAction;

    private float last = Float.NaN;

    /** Pide reproducir una acción; se aplica en la siguiente actualización. */
    public void triggerAction(String name) {
        this.pendingAction = name;
    }

    public float[] rot() { return rot; }
    public float[] pos() { return pos; }
    public float[] scl() { return scl; }

    public void update(float nowSeconds, String locomotion, Map<String, BearAnimation> anims, int boneCount) {
        if (bones != boneCount) {
            bones = boneCount;
            rot = new float[boneCount * 3];
            pos = new float[boneCount * 3];
            scl = new float[boneCount * 3];
        }
        float dt = Float.isNaN(last) ? 0f : nowSeconds - last;
        if (dt < 0f || dt > 0.5f) dt = 0f;
        last = nowSeconds;

        if (!locomotion.equals(curName)) {
            prev = cur;
            prevT = curT;
            cur = anims.get(locomotion);
            curT = 0f;
            curName = locomotion;
            fade = prev == null ? 1f : 0f;
        }
        if (pendingAction != null) {
            action = anims.get(pendingAction);
            actionT = 0f;
            pendingAction = null;
        }

        curT += dt;
        prevT += dt;
        if (fade < 1f) fade = Math.min(1f, fade + dt / FADE);
        if (action != null) {
            actionT += dt;
            if (action.loopMode != BearAnimation.HOLD && actionT >= action.length) action = null;
        }
        compute();
    }

    private void compute() {
        float aw = 0f;
        if (action != null) {
            aw = Math.min(1f, actionT / ACTION_IN);
            if (action.loopMode != BearAnimation.HOLD) {
                aw = Math.min(aw, Math.max(0f, (action.length - actionT) / ACTION_OUT));
            }
        }
        for (int i = 0; i < bones; i++) {
            int o = i * 3;
            layer(cur, curT, i, rot, pos, scl, o);
            if (fade < 1f && prev != null) {
                layer(prev, prevT, i, pr, pp, ps, 0);
                for (int k = 0; k < 3; k++) {
                    rot[o + k] = pr[k] + (rot[o + k] - pr[k]) * fade;
                    pos[o + k] = pp[k] + (pos[o + k] - pp[k]) * fade;
                    scl[o + k] = ps[k] + (scl[o + k] - ps[k]) * fade;
                }
            }
            if (action != null) {
                float t = action.localTime(actionT);
                blend(action.rot[i], t, aw, rot, o);
                blend(action.pos[i], t, aw, pos, o);
                blend(action.scl[i], t, aw, scl, o);
            }
        }
    }

    private void blend(BearAnimation.Track track, float t, float w, float[] dst, int o) {
        if (track == null) return;
        track.sample(t, b);
        for (int k = 0; k < 3; k++) dst[o + k] += (b[k] - dst[o + k]) * w;
    }

    private void layer(BearAnimation an, float elapsed, int bone, float[] r, float[] p, float[] s, int o) {
        for (int k = 0; k < 3; k++) {
            r[o + k] = 0f;
            p[o + k] = 0f;
            s[o + k] = 1f;
        }
        if (an == null) return;
        float t = an.localTime(elapsed);
        if (an.rot[bone] != null) { an.rot[bone].sample(t, a); System.arraycopy(a, 0, r, o, 3); }
        if (an.pos[bone] != null) { an.pos[bone].sample(t, a); System.arraycopy(a, 0, p, o, 3); }
        if (an.scl[bone] != null) { an.scl[bone].sample(t, a); System.arraycopy(a, 0, s, o, 3); }
    }
}
