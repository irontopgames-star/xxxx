package com.bearfriender.registry;

import com.bearfriender.BearfrienderMod;
import com.bearfriender.entity.BearfrienderEntity;
import com.bearfriender.entity.BrokenTeddyEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, BearfrienderMod.MOD_ID);

    /** Modelo de ~3 bloques de alto; hitbox 0,9 x 3,0 (los techos de la instalación deben tener ≥ 4 bloques). */
    public static final RegistryObject<EntityType<BearfrienderEntity>> BEARFRIENDER = ENTITIES.register("bearfriender",
            () -> EntityType.Builder.of(BearfrienderEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 3.0F)
                    .clientTrackingRange(12)
                    .fireImmune()
                    .build(BearfrienderMod.MOD_ID + ":bearfriender"));

    public static final RegistryObject<EntityType<BrokenTeddyEntity>> BROKEN_TEDDY = ENTITIES.register("broken_teddy",
            () -> EntityType.Builder.of(BrokenTeddyEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.8F)
                    .clientTrackingRange(8)
                    .build(BearfrienderMod.MOD_ID + ":broken_teddy"));

    private ModEntities() {}
}
