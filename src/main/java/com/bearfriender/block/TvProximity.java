package com.bearfriender.block;

import com.bearfriender.entity.BearfrienderEntity;
import com.bearfriender.entity.ai.BearState;
import com.bearfriender.structure.FacilityData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Los televisores de la instalación reaccionan a la distancia de Bearfriender:
 * lejos se apagan; a media distancia muestran estática; más cerca, interferencias; muy cerca, su rostro o su sonrisa.
 * Solo se revisan los televisores registrados como anclas "tv" y cada pocos ticks, así que es muy barato.
 */
public final class TvProximity {
    public static final double FAR = 16.0D;
    public static final double MID = 10.0D;
    public static final double NEAR = 5.0D;

    private TvProximity() {}

    public static void update(BearfrienderEntity bear) {
        MinecraftServer server = bear.getServer();
        if (server == null || !(bear.level() instanceof ServerLevel level)) return;
        FacilityData data = FacilityData.get(server);
        if (!data.isBuilt() || data.level(server) != level) return;
        if (bear.getBearState() == BearState.DORMANT) return;      // oculto: los televisores no reaccionan

        for (BlockPos pos : data.anchors("tv")) {
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof CrtTvBlock)) continue;
            if (TvControl.isHeld(pos)) continue;                       // lo controla un evento de la campaña
            double d = Math.sqrt(bear.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D));
            TvScreen want;
            if (d > FAR) want = TvScreen.OFF;
            else if (d > MID) want = TvScreen.STATIC;
            else if (d > NEAR) want = TvScreen.INTERFERENCE;
            else want = (pos.hashCode() & 1) == 0 ? TvScreen.FACE : TvScreen.SMILE;
            CrtTvBlock.setScreen(level, pos, state, want);
        }
    }
}
