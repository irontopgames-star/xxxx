package com.bearfriender.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registro persistente de la instalación: si está construida, dónde, y las posiciones clave ("anclas")
 * que usan las demás fases (misiones, televisores, IA, jefe). Se guarda con el mundo.
 */
public class FacilityData extends SavedData {
    private static final String NAME = "bearfriender_facility";

    private boolean built;
    private boolean exitOpen;
    private String dimension = "minecraft:overworld";
    private BlockPos origin = BlockPos.ZERO;
    private final Map<String, List<BlockPos>> anchors = new LinkedHashMap<>();

    /** Los datos viven siempre en el almacenamiento del Overworld, aunque la instalación esté en otra dimensión. */
    public static FacilityData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FacilityData::load, FacilityData::new, NAME);
    }

    public boolean isBuilt() { return built; }
    public boolean isExitOpen() { return exitOpen; }
    public BlockPos origin() { return origin; }

    public ServerLevel level(MinecraftServer server) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dimension));
        return server.getLevel(key);
    }

    /** Esquina mínima (x, y, z) del volumen ocupado. */
    public BlockPos min() { return origin.offset(0, FacilityLayout.MIN_Y, 0); }

    /** Esquina máxima del volumen ocupado. */
    public BlockPos max() { return origin.offset(FacilityLayout.SIZE_X - 1, FacilityLayout.MAX_Y, FacilityLayout.SIZE_Z - 1); }

    public BlockPos anchor(String name) {
        List<BlockPos> list = anchors.get(name);
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    public List<BlockPos> anchors(String name) {
        List<BlockPos> list = anchors.get(name);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    public Map<String, List<BlockPos>> allAnchors() { return Collections.unmodifiableMap(anchors); }

    public void markBuilt(ServerLevel level, BlockPos origin, Map<String, List<BlockPos>> found) {
        this.built = true;
        this.exitOpen = false;
        this.dimension = level.dimension().location().toString();
        this.origin = origin;
        this.anchors.clear();
        this.anchors.putAll(found);
        setDirty();
    }

    public void setExitOpen(boolean open) {
        this.exitOpen = open;
        setDirty();
    }

    public void clear() {
        this.built = false;
        this.exitOpen = false;
        this.origin = BlockPos.ZERO;
        this.anchors.clear();
        setDirty();
    }

    public static FacilityData load(CompoundTag tag) {
        FacilityData d = new FacilityData();
        d.built = tag.getBoolean("Built");
        d.exitOpen = tag.getBoolean("ExitOpen");
        d.dimension = tag.getString("Dimension");
        if (d.dimension.isEmpty()) d.dimension = "minecraft:overworld";
        d.origin = new BlockPos(tag.getInt("OX"), tag.getInt("OY"), tag.getInt("OZ"));
        CompoundTag a = tag.getCompound("Anchors");
        for (String key : a.getAllKeys()) {
            List<BlockPos> list = new ArrayList<>();
            for (long l : a.getLongArray(key)) list.add(BlockPos.of(l));
            d.anchors.put(key, list);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Built", built);
        tag.putBoolean("ExitOpen", exitOpen);
        tag.putString("Dimension", dimension);
        tag.putInt("OX", origin.getX());
        tag.putInt("OY", origin.getY());
        tag.putInt("OZ", origin.getZ());
        CompoundTag a = new CompoundTag();
        for (Map.Entry<String, List<BlockPos>> e : anchors.entrySet()) {
            long[] arr = new long[e.getValue().size()];
            for (int i = 0; i < arr.length; i++) arr[i] = e.getValue().get(i).asLong();
            a.put(e.getKey(), new LongArrayTag(arr));
        }
        tag.put("Anchors", a);
        return tag;
    }
}
