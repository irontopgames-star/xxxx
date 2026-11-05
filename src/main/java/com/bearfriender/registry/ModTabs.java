package com.bearfriender.registry;

import com.bearfriender.BearfrienderMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Pestaña de creativo con todo lo del mod. */
public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BearfrienderMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.bearfriender"))
            .icon(() -> new ItemStack(ModBlocks.CRT_TV.get()))
            .displayItems((params, output) -> {
                output.accept(ModBlocks.CRT_TV.get());
                output.accept(ModBlocks.PLUSH_TEDDY.get());
                output.accept(ModBlocks.PLUSH_BUNNY.get());
                output.accept(ModBlocks.PLUSH_DUCK.get());
                output.accept(ModBlocks.BALLOON.get());
                output.accept(ModBlocks.BALLOON_STRING.get());
                output.accept(ModBlocks.TOY_BLOCKS.get());
                output.accept(ModItems.BEARFRIENDER_SPAWN_EGG.get());
                output.accept(ModItems.BROKEN_TEDDY_SPAWN_EGG.get());
            })
            .build());

    private ModTabs() {}
}
