package com.bearfriender.registry;

import com.bearfriender.BearfrienderMod;
import com.bearfriender.entity.BearfrienderEntity;
import com.bearfriender.entity.BrokenTeddyEntity;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BearfrienderMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEvents {
    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.BEARFRIENDER.get(), BearfrienderEntity.createAttributes().build());
        event.put(ModEntities.BROKEN_TEDDY.get(), BrokenTeddyEntity.createAttributes().build());
    }

    private ModEvents() {}
}
