package com.bearfriender.entity;

/** Animaciones de una sola ejecución, disparadas desde el servidor. */
public enum BearAction {
    PEEK("peek"), ATTACK("attack"), GRAB("grab"), MISSED_ATTACK("missed_attack"),
    TV_CONTROL("tv_control"), SCREAM("scream"), DAMAGE("damage"),
    PHASE_TRANSITION("phase_transition"), DEATH("death");

    public final String animName;
    BearAction(String animName) { this.animName = animName; }
}
