package com.bearfriender.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Osito destruido: permanece sentado, invulnerable, y mira al jugador. El diálogo se añade en la Fase 6. */
public class BrokenTeddyEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Boolean> DATA_TALKING =
            SynchedEntityData.defineId(BrokenTeddyEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation SIT = RawAnimation.begin().thenLoop("sit");
    private static final RawAnimation TALK = RawAnimation.begin().thenLoop("talk");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public BrokenTeddyEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_TALKING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new LookAtPlayerGoal(this, Player.class, 6.0F));
    }

    public boolean isTalking() { return this.entityData.get(DATA_TALKING); }

    public void setTalking(boolean talking) {
        if (!this.level().isClientSide) this.entityData.set(DATA_TALKING, talking);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Talking", isTalking());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_TALKING, tag.getBoolean("Talking"));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<BrokenTeddyEntity>(this, "main", 5, this::animate));
    }

    private PlayState animate(AnimationState<BrokenTeddyEntity> state) {
        return state.setAndContinue(isTalking() ? TALK : SIT);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
