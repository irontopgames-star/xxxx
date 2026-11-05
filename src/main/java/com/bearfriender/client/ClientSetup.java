package com.bearfriender.client;

import com.bearfriender.BearfrienderMod;
import com.bearfriender.client.hud.CampaignHud;
import com.bearfriender.client.render.BearfrienderModel;
import com.bearfriender.client.render.BearfrienderRenderer;
import com.bearfriender.client.render.BrokenTeddyRenderer;
import com.bearfriender.registry.ModEntities;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registro de renderizadores y recarga de recursos. Solo se carga en el cliente. */
@Mod.EventBusSubscriber(modid = BearfrienderMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BEARFRIENDER.get(), BearfrienderRenderer::new);
        event.registerEntityRenderer(ModEntities.BROKEN_TEDDY.get(), BrokenTeddyRenderer::new);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("campaign_clock", CampaignHud::render);
    }

    /** Al recargar recursos (F3+T) se vuelve a leer la malla del oso. */
    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> BearfrienderModel.invalidate());
    }

    private ClientSetup() {}
}
