package com.bearfriender.network;

import com.bearfriender.client.ClientCampaignState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Estado de la campaña que el cliente necesita para dibujar su interfaz. Lo envía el servidor. */
public class CampaignSyncPacket {
    public final boolean active;
    public final boolean ended;
    public final int night;
    public final int extraLoops;
    public final int nightTicks;
    public final int nightDuration;
    public final int missionMask;

    public CampaignSyncPacket(boolean active, boolean ended, int night, int extraLoops, int nightTicks,
                              int nightDuration, int missionMask) {
        this.active = active;
        this.ended = ended;
        this.night = night;
        this.extraLoops = extraLoops;
        this.nightTicks = nightTicks;
        this.nightDuration = nightDuration;
        this.missionMask = missionMask;
    }

    public static void encode(CampaignSyncPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.active);
        buf.writeBoolean(p.ended);
        buf.writeVarInt(p.night);
        buf.writeVarInt(p.extraLoops);
        buf.writeVarInt(p.nightTicks);
        buf.writeVarInt(p.nightDuration);
        buf.writeVarInt(p.missionMask);
    }

    public static CampaignSyncPacket decode(FriendlyByteBuf buf) {
        return new CampaignSyncPacket(buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(CampaignSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCampaignState.update(msg)));
        ctx.get().setPacketHandled(true);
    }
}
