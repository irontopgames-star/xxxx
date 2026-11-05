package com.bearfriender.structure;

import com.bearfriender.campaign.Scheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Control de las luces de la instalación: son bloques de luz invisibles registrados como anclas "lamp". */
public final class LightControl {
    public static final int DEFAULT_LEVEL = 4;

    private LightControl() {}

    public static void set(ServerLevel level, BlockPos pos, int lightLevel) {
        if (!level.isLoaded(pos)) return;
        BlockState s = level.getBlockState(pos);
        if (s.is(Blocks.LIGHT) && s.getValue(LightBlock.LEVEL) != lightLevel) {
            level.setBlock(pos, s.setValue(LightBlock.LEVEL, lightLevel), Block.UPDATE_CLIENTS);
        }
    }

    public static void setAll(ServerLevel level, FacilityData data, int lightLevel) {
        for (BlockPos p : data.anchors("lamp")) set(level, p, lightLevel);
    }

    public static void restoreAll(ServerLevel level, FacilityData data) {
        setAll(level, data, DEFAULT_LEVEL);
    }

    /**
     * Parpadeo: el patrón son duraciones en ticks que alternan apagado/encendido empezando por apagado.
     * Al terminar, las luces vuelven a su nivel normal.
     */
    public static void flicker(ServerLevel level, List<BlockPos> lamps, int[] pattern) {
        int t = 0;
        boolean off = true;
        for (int d : pattern) {
            final boolean turnOff = off;
            Scheduler.after(t, () -> {
                for (BlockPos p : lamps) set(level, p, turnOff ? 0 : DEFAULT_LEVEL);
            });
            t += d;
            off = !off;
        }
        Scheduler.after(t, () -> {
            for (BlockPos p : lamps) set(level, p, DEFAULT_LEVEL);
        });
    }

    /** Apagón de toda la instalación durante unos ticks. */
    public static void blackout(ServerLevel level, FacilityData data, int ticks) {
        setAll(level, data, 0);
        Scheduler.after(ticks, () -> restoreAll(level, data));
    }
}
