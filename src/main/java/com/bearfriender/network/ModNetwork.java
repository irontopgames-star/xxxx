package com.bearfriender.network;

import com.bearfriender.BearfrienderMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Canal de red del mod. Los paquetes nuevos (misiones, etc.) se registran aquí. */
public final class ModNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BearfrienderMod.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static int nextId = 0;

    public static void register() {
        CHANNEL.registerMessage(nextId++, CampaignSyncPacket.class, CampaignSyncPacket::encode,
                CampaignSyncPacket::decode, CampaignSyncPacket::handle);
    }

    private ModNetwork() {}
}
