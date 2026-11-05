package com.bearfriender.entity.ai;

/** Estados de la máquina de estados de Bearfriender (la lógica de transición llega en la Fase 8). */
public enum BearState {
    DORMANT, OBSERVING, STALKING, INVESTIGATING, HIDING,
    TV_MANIFESTATION, HUNTING, ATTACKING, RETREATING, STUNNED, BOSS_PHASE;

    public static BearState byId(int id) {
        BearState[] v = values();
        return v[Math.floorMod(id, v.length)];
    }
}
