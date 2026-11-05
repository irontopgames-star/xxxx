package com.bearfriender.entity;

import com.bearfriender.block.TvProximity;
import com.bearfriender.entity.ai.BearState;
import com.bearfriender.entity.anim.BearAnimator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Bearfriender. La animación se calcula en el cliente a partir de datos sincronizados
 * (estado, pantalla) y de eventos de acción enviados por el servidor.
 */
public class BearfrienderEntity extends Monster {
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(BearfrienderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SCREEN =
            SynchedEntityData.defineId(BearfrienderEntity.class, EntityDataSerializers.INT);

    /** Los eventos de entidad 100+ordinal indican una animación de acción. */
    private static final byte EVENT_ACTION_BASE = 100;
    /** Ticks que el cadáver permanece visible (la animación de muerte dura 3 s). */
    private static final int DEATH_TICKS = 70;

    private BearAnimator animator;   // solo se usa en el cliente

    public BearfrienderEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
    }

    /** Atributos base. La campaña los modulará por noche en la Fase 5. */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_STATE, BearState.OBSERVING.ordinal());
        this.entityData.define(DATA_SCREEN, ScreenMode.STATIC.ordinal());
    }

    @Override
    protected void registerGoals() {
        // Comportamiento mínimo; la máquina de estados real sustituye esto en la Fase 8.
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 32.0F));
    }

    // ---- Estado y pantalla (solo se modifican en el servidor) ----

    public BearState getBearState() { return BearState.byId(this.entityData.get(DATA_STATE)); }

    public void setBearState(BearState state) {
        if (!this.level().isClientSide) this.entityData.set(DATA_STATE, state.ordinal());
    }

    public ScreenMode getScreenMode() { return ScreenMode.byId(this.entityData.get(DATA_SCREEN)); }

    public void setScreenMode(ScreenMode mode) {
        if (!this.level().isClientSide) this.entityData.set(DATA_SCREEN, mode.ordinal());
    }

    @Override
    public void tick() {
        super.tick();
        // Los televisores cercanos reaccionan (cada medio segundo)
        if (!this.level().isClientSide && this.tickCount % 10 == 0) {
            TvProximity.update(this);
        }
    }

    // ---- Animación ----

    /** Dispara una animación de una sola ejecución en todos los clientes que ven la entidad. */
    public void playAction(BearAction action) {
        if (!this.level().isClientSide) {
            this.level().broadcastEntityEvent(this, (byte) (EVENT_ACTION_BASE + action.ordinal()));
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        int index = id - EVENT_ACTION_BASE;
        if (index >= 0 && index < BearAction.values().length) {
            animator().triggerAction(BearAction.values()[index].animName);
        } else {
            super.handleEntityEvent(id);
        }
    }

    /** Reproductor de animaciones; solo debe usarse en el cliente. */
    public BearAnimator animator() {
        if (animator == null) animator = new BearAnimator();
        return animator;
    }

    // ---- Comportamiento general ----

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dims) { return 2.6F; }

    @Override
    protected boolean shouldDespawnInPeaceful() { return false; }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) { return false; }

    /** Las animaciones (arrastrarse, brazos abiertos) salen de la hitbox: se amplía la caja de visibilidad. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(2.5D);
    }

    @Override
    public void die(DamageSource source) {
        playAction(BearAction.DEATH);
        super.die(source);
    }

    /** Deja el cadáver el tiempo que dura la animación de muerte antes de retirarlo. */
    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (this.deathTime >= DEATH_TICKS && !this.level().isClientSide() && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("BearState", getBearState().ordinal());
        tag.putInt("ScreenMode", getScreenMode().ordinal());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_STATE, tag.getInt("BearState"));
        this.entityData.set(DATA_SCREEN, tag.getInt("ScreenMode"));
    }
}
