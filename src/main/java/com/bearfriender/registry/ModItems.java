package com.bearfriender.registry;

import com.bearfriender.BearfrienderMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, BearfrienderMod.MOD_ID);

    public static final RegistryObject<Item> BEARFRIENDER_SPAWN_EGG = ITEMS.register("bearfriender_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.BEARFRIENDER, 0x1A1A1A, 0x8B0000, new Item.Properties()));

    public static final RegistryObject<Item> BROKEN_TEDDY_SPAWN_EGG = ITEMS.register("broken_teddy_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.BROKEN_TEDDY, 0x6B5B45, 0x2A2A2A, new Item.Properties()));

    private ModItems() {}
}
