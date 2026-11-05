package com.bearfriender.registry;

import com.bearfriender.BearfrienderMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sonidos propios del mod. Cada evento se define en assets/bearfriender/sounds.json y apunta a archivos .ogg
 * de assets/bearfriender/sounds/. Para cambiar un sonido basta con reemplazar su archivo (o editar sounds.json).
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, BearfrienderMod.MOD_ID);

    // Televisores
    public static final RegistryObject<SoundEvent> TV_ON = reg("tv_on");
    public static final RegistryObject<SoundEvent> TV_OFF = reg("tv_off");
    public static final RegistryObject<SoundEvent> TV_STATIC = reg("tv_static");
    public static final RegistryObject<SoundEvent> TV_FACE = reg("tv_face");
    public static final RegistryObject<SoundEvent> TV_SCARE_OFF = reg("tv_scare_off");
    public static final RegistryObject<SoundEvent> TV_VOICE = reg("tv_voice");
    // Bearfriender
    public static final RegistryObject<SoundEvent> BEAR_GROWL = reg("bear_growl");
    public static final RegistryObject<SoundEvent> BEAR_ROAR = reg("bear_roar");
    public static final RegistryObject<SoundEvent> BEAR_SCREAM = reg("bear_scream");
    // Ambiente de la instalación
    public static final RegistryObject<SoundEvent> DOOR_SLAM = reg("door_slam");
    public static final RegistryObject<SoundEvent> KNOCK = reg("knock");
    public static final RegistryObject<SoundEvent> STEP_HEAVY = reg("step_heavy");
    public static final RegistryObject<SoundEvent> BLACKOUT = reg("blackout");
    public static final RegistryObject<SoundEvent> LIGHT_ZAP = reg("light_zap");
    public static final RegistryObject<SoundEvent> EXIT_OPEN = reg("exit_open");

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(BearfrienderMod.MOD_ID, name)));
    }

    private ModSounds() {}
}
