package com.bearfriender.campaign;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Estado persistente de la campaña: noche, reloj, misiones, eventos ya disparados y el oso único.
 * Vive en el almacenamiento del Overworld y sobrevive a guardar y cargar.
 */
public class CampaignData extends SavedData {
    private static final String NAME = "bearfriender_campaign";

    private boolean active;
    private boolean ended;
    private int night = 1;
    private int nightTicks;
    private int extraLoops;          // veces que se ha repetido la noche 4 por faltar misiones
    private int missionMask;
    private int tapesDestroyed;
    private final Set<String> fired = new HashSet<>();
    private final Set<String> cinematics = new HashSet<>();
    private UUID bearId;
    private BlockPos bearPos = BlockPos.ZERO;
    private String returnDim = "";
    private BlockPos returnPos = BlockPos.ZERO;

    public static CampaignData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CampaignData::load, CampaignData::new, NAME);
    }

    // ---- estado general ----
    public boolean isActive() { return active; }
    public boolean isEnded() { return ended; }
    public int getNight() { return night; }
    public int getNightTicks() { return nightTicks; }
    public int getExtraLoops() { return extraLoops; }

    public void start() {
        resetAll();
        active = true;
        setDirty();
    }

    public void stop() {
        resetAll();
        setDirty();
    }

    private void resetAll() {
        active = false;
        ended = false;
        night = 1;
        nightTicks = 0;
        extraLoops = 0;
        missionMask = 0;
        tapesDestroyed = 0;
        fired.clear();
        cinematics.clear();
    }

    public void finish() {
        ended = true;
        setDirty();
    }

    public void setNight(int n) {
        night = Math.max(1, Math.min(5, n));
        nightTicks = 0;
        extraLoops = 0;
        setDirty();
    }

    public void addNightTick() {
        nightTicks++;
        setDirty();
    }

    public void restartNightClock() {
        nightTicks = 0;
        setDirty();
    }

    public void nextLoop() {
        extraLoops++;
        nightTicks = 0;
        setDirty();
    }

    // ---- misiones (las completa la Fase 6) ----
    public boolean isMissionDone(MissionId m) { return (missionMask & m.bit()) != 0; }
    public int getMissionMask() { return missionMask; }

    public boolean allMissionsDone() {
        return isMissionDone(MissionId.POWER) && isMissionDone(MissionId.TAPES) && isMissionDone(MissionId.SIGNAL);
    }

    public void setMission(MissionId m, boolean done) {
        if (done) missionMask |= m.bit(); else missionMask &= ~m.bit();
        setDirty();
    }

    public int getTapesDestroyed() { return tapesDestroyed; }

    public void setTapesDestroyed(int n) {
        tapesDestroyed = Math.max(0, Math.min(3, n));
        setDirty();
    }

    // ---- eventos y cinemáticas ya reproducidos ----
    public boolean hasFired(String key) { return fired.contains(key); }

    public void markFired(String key) {
        fired.add(key);
        setDirty();
    }

    public void clearFiredForNight(int n) {
        fired.removeIf(k -> k.startsWith(n + ":"));
        setDirty();
    }

    public boolean hasCinematic(String id) { return cinematics.contains(id); }

    public void markCinematic(String id) {
        cinematics.add(id);
        setDirty();
    }

    // ---- el oso único ----
    public UUID getBearId() { return bearId; }
    public BlockPos getBearPos() { return bearPos; }

    public void setBear(UUID id, BlockPos pos) {
        bearId = id;
        bearPos = pos;
        setDirty();
    }

    public void clearBear() {
        bearId = null;
        setDirty();
    }

    // ---- punto de regreso ----
    public String getReturnDim() { return returnDim; }
    public BlockPos getReturnPos() { return returnPos; }

    public void setReturn(String dim, BlockPos pos) {
        returnDim = dim;
        returnPos = pos;
        setDirty();
    }

    // ---- guardado ----
    public static CampaignData load(CompoundTag tag) {
        CampaignData d = new CampaignData();
        d.active = tag.getBoolean("Active");
        d.ended = tag.getBoolean("Ended");
        d.night = Math.max(1, Math.min(5, tag.getInt("Night")));
        d.nightTicks = tag.getInt("NightTicks");
        d.extraLoops = tag.getInt("ExtraLoops");
        d.missionMask = tag.getInt("Missions");
        d.tapesDestroyed = tag.getInt("Tapes");
        for (Tag t : tag.getList("Fired", Tag.TAG_STRING)) d.fired.add(t.getAsString());
        for (Tag t : tag.getList("Cinematics", Tag.TAG_STRING)) d.cinematics.add(t.getAsString());
        if (tag.hasUUID("Bear")) d.bearId = tag.getUUID("Bear");
        d.bearPos = new BlockPos(tag.getInt("BX"), tag.getInt("BY"), tag.getInt("BZ"));
        d.returnDim = tag.getString("RDim");
        d.returnPos = new BlockPos(tag.getInt("RX"), tag.getInt("RY"), tag.getInt("RZ"));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Active", active);
        tag.putBoolean("Ended", ended);
        tag.putInt("Night", night);
        tag.putInt("NightTicks", nightTicks);
        tag.putInt("ExtraLoops", extraLoops);
        tag.putInt("Missions", missionMask);
        tag.putInt("Tapes", tapesDestroyed);
        ListTag f = new ListTag();
        for (String s : fired) f.add(StringTag.valueOf(s));
        tag.put("Fired", f);
        ListTag c = new ListTag();
        for (String s : cinematics) c.add(StringTag.valueOf(s));
        tag.put("Cinematics", c);
        if (bearId != null) tag.putUUID("Bear", bearId);
        tag.putInt("BX", bearPos.getX());
        tag.putInt("BY", bearPos.getY());
        tag.putInt("BZ", bearPos.getZ());
        tag.putString("RDim", returnDim);
        tag.putInt("RX", returnPos.getX());
        tag.putInt("RY", returnPos.getY());
        tag.putInt("RZ", returnPos.getZ());
        return tag;
    }
}
