package com.bearfriender;

import com.bearfriender.config.BearfrienderConfig;
import com.bearfriender.network.ModNetwork;
import com.bearfriender.registry.ModBlocks;
import com.bearfriender.registry.ModEntities;
import com.bearfriender.registry.ModItems;
import com.bearfriender.registry.ModSounds;
import com.bearfriender.registry.ModTabs;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(BearfrienderMod.MOD_ID)
public class BearfrienderMod {
    public static final String MOD_ID = "bearfriender";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BearfrienderMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, BearfrienderConfig.SPEC);

        ModBlocks.BLOCKS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModTabs.TABS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(ModNetwork::register));

        LOGGER.info("Bearfriender: Five Nights of Terror inicializado.");
    }
}
