package com.bearfriender.client;

import com.bearfriender.network.CampaignSyncPacket;

/** Copia, en el cliente, del estado de la campaña (solo lectura; manda el servidor). */
public final class ClientCampaignState {
    public static boolean active;
    public static boolean ended;
    public static int night = 1;
    public static int extraLoops;
    public static int nightTicks;
    public static int nightDuration = 1;
    public static int missionMask;

    private ClientCampaignState() {}

    public static void update(CampaignSyncPacket p) {
        active = p.active;
        ended = p.ended;
        night = p.night;
        extraLoops = p.extraLoops;
        nightTicks = p.nightTicks;
        nightDuration = Math.max(1, p.nightDuration);
        missionMask = p.missionMask;
    }

    /** Hora de la noche, de 12:00 AM a 6:00 AM. */
    public static String clock() {
        float progress = Math.min(1.0F, nightTicks / (float) nightDuration);
        int minutes = (int) (progress * 360);
        int h = minutes / 60;
        return String.format("%d:%02d AM", h == 0 ? 12 : h, minutes % 60);
    }
}
