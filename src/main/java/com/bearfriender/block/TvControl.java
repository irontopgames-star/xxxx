package com.bearfriender.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.bearfriender.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Control temporal de televisores por parte de los eventos de la campaña.
 * Mientras un televisor está "retenido" mantiene su imagen y TvProximity no lo toca.
 * Una retención puede terminar sola por tiempo o cuando un jugador se acerca (offRadius).
 */
public final class TvControl {
    private record Hold(TvScreen screen, long until, double offRadius) {}

    private static final Map<Long, Hold> HOLDS = new HashMap<>();

    private TvControl() {}

    public static void hold(ServerLevel level, BlockPos pos, TvScreen screen, int ticks, double offRadius) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof CrtTvBlock)) return;
        CrtTvBlock.setScreen(level, pos, s, screen);
        HOLDS.put(pos.asLong(), new Hold(screen, level.getGameTime() + ticks, offRadius));
    }

    public static boolean isHeld(BlockPos pos) {
        return HOLDS.containsKey(pos.asLong());
    }

    /** Se llama cada tick con los jugadores de la instalación. */
    public static void tick(ServerLevel level, List<ServerPlayer> players) {
        if (HOLDS.isEmpty()) return;
        long now = level.getGameTime();
        Iterator<Map.Entry<Long, Hold>> it = HOLDS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Hold> e = it.next();
            BlockPos pos = BlockPos.of(e.getKey());
            Hold h = e.getValue();
            boolean expire = now >= h.until();
            boolean scared = false;
            if (!expire && h.offRadius() > 0) {
                double r2 = h.offRadius() * h.offRadius();
                for (ServerPlayer p : players) {
                    if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < r2) {
                        expire = true;
                        scared = true;
                        break;
                    }
                }
            }
            if (!expire) continue;
            it.remove();
            if (!level.isLoaded(pos)) continue;
            BlockState s = level.getBlockState(pos);
            CrtTvBlock.setScreen(level, pos, s, TvScreen.OFF);
            if (scared) {
                level.playSound(null, pos, ModSounds.TV_SCARE_OFF.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
    }

    /** Apaga todos los televisores retenidos y olvida las retenciones. */
    public static void releaseAll(ServerLevel level) {
        for (Long key : HOLDS.keySet()) {
            BlockPos pos = BlockPos.of(key);
            if (level.isLoaded(pos)) CrtTvBlock.setScreen(level, pos, level.getBlockState(pos), TvScreen.OFF);
        }
        HOLDS.clear();
    }

    public static void clear() {
        HOLDS.clear();
    }

    public static int held() { return HOLDS.size(); }
}
