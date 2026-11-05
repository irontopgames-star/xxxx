package com.bearfriender.registry;

import com.bearfriender.BearfrienderMod;
import com.bearfriender.block.BalloonBlock;
import com.bearfriender.block.BalloonStringBlock;
import com.bearfriender.block.CrtTvBlock;
import com.bearfriender.block.PlushBlock;
import com.bearfriender.block.ToyBlocksBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, BearfrienderMod.MOD_ID);

    public static final RegistryObject<Block> CRT_TV = block("crt_tv", CrtTvBlock::new);
    public static final RegistryObject<Block> PLUSH_TEDDY = block("plush_teddy", PlushBlock::new);
    public static final RegistryObject<Block> PLUSH_BUNNY = block("plush_bunny", PlushBlock::new);
    public static final RegistryObject<Block> PLUSH_DUCK = block("plush_duck", PlushBlock::new);
    public static final RegistryObject<Block> BALLOON = block("balloon", BalloonBlock::new);
    public static final RegistryObject<Block> BALLOON_STRING = block("balloon_string", BalloonStringBlock::new);
    public static final RegistryObject<Block> TOY_BLOCKS = block("toy_blocks", ToyBlocksBlock::new);

    /** Registra el bloque y su objeto. */
    private static RegistryObject<Block> block(String name, Supplier<Block> factory) {
        RegistryObject<Block> reg = BLOCKS.register(name, factory);
        ModItems.ITEMS.register(name, () -> new BlockItem(reg.get(), new Item.Properties()));
        return reg;
    }

    private ModBlocks() {}
}
