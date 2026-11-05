package com.bearfriender.campaign;

import com.bearfriender.block.CrtTvBlock;
import com.bearfriender.block.TvControl;
import com.bearfriender.block.TvScreen;
import com.bearfriender.campaign.NightDefinition.ScheduledEvent;
import com.bearfriender.structure.FacilityData;
import com.bearfriender.structure.LightControl;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import com.bearfriender.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Efectos reales de los eventos programados de cada noche (sonidos, puertas, luces, televisores, apariciones). */
public final class CampaignEvents {
    /** Contexto de ejecución de un evento. */
    public record Ctx(ServerLevel level, FacilityData fac, CampaignData camp, List<ServerPlayer> players) {
        RandomSource rnd() { return level.getRandom(); }
    }

    private CampaignEvents() {}

    public static void run(Ctx ctx, ScheduledEvent ev) {
        if (ctx.players().isEmpty()) return;
        ServerPlayer target = ctx.players().get(ctx.rnd().nextInt(ctx.players().size()));
        switch (ev.type()) {
            case DOOR_SLAM -> doorSlam(ctx, target, ev.intensity());
            case DISTANT_KNOCKS -> distantKnocks(ctx, target, ev.intensity());
            case LIGHT_FLICKER -> lightFlicker(ctx, target, ev.intensity());
            case BLACKOUT -> blackout(ctx, 60 + ctx.rnd().nextInt(60));
            case TV_FLICKER -> tvFlicker(ctx, target, ev.intensity());
            case TV_VOICES -> tvVoices(ctx, target);
            case SHADOW -> BearDirector.requestFlash(ctx.level(), ctx.fac(), ctx.camp(), target,
                    ev.minDist() > 0 ? ev.minDist() : 8, ev.maxDist() > 0 ? ev.maxDist() : 20, ev.ticks() > 0 ? ev.ticks() : 80, 10);
            case TV_FACE_SCARE -> tvFaceScare(ctx, target);
            case TV_ALL_FACE -> tvAllFace(ctx, 120);
            case STEPS -> steps(ctx, target, 4 + ev.intensity() * 2);
            case HINT -> hint(ctx, ev.text());
        }
    }

    // ---------------------------------------------------------------- utilidades

    private static List<BlockPos> near(List<BlockPos> list, Vec3 from, double min, double max) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : list) {
            double d = from.distanceTo(Vec3.atCenterOf(p));
            if (d >= min && d <= max) out.add(p);
        }
        return out;
    }

    private static BlockPos pick(List<BlockPos> list, RandomSource rnd) {
        return list.isEmpty() ? null : list.get(rnd.nextInt(list.size()));
    }

    private static void sound(ServerLevel level, BlockPos pos, SoundEvent s, float vol, float pitch) {
        level.playSound(null, pos, s, SoundSource.HOSTILE, vol, pitch);
    }

    // ---------------------------------------------------------------- eventos

    /** Una puerta cercana se cierra de golpe. */
    private static void doorSlam(Ctx ctx, ServerPlayer target, int intensity) {
        List<BlockPos> doors = new ArrayList<>(near(ctx.fac().anchors("door"), target.position(), 5, 24));
        Collections.shuffle(doors, new java.util.Random(ctx.rnd().nextLong()));
        int slams = Math.max(1, intensity - 1);
        for (int i = 0; i < slams && i < doors.size(); i++) {
            BlockPos pos = doors.get(i);
            closeDoorPair(ctx, pos);
            sound(ctx.level(), pos, ModSounds.DOOR_SLAM.get(), 1.6F, 1.0F);
        }
    }

    private static void closeDoorPair(Ctx ctx, BlockPos pos) {
        for (BlockPos other : ctx.fac().anchors("door")) {
            if (other.distSqr(pos) > 2.5D) continue;               // la hoja contigua de la puerta doble
            BlockState s = ctx.level().getBlockState(other);
            if (s.getBlock() instanceof DoorBlock door && s.getValue(DoorBlock.OPEN)) {
                door.setOpen(null, ctx.level(), s, other, false);
            }
        }
    }

    /** Golpes lejanos, en secuencia. */
    private static void distantKnocks(Ctx ctx, ServerPlayer target, int intensity) {
        BlockPos at = pick(near(ctx.fac().anchors("door"), target.position(), 12, 40), ctx.rnd());
        if (at == null) return;
        int knocks = 2 + intensity;
        for (int i = 0; i < knocks; i++) {
            final float pitch = 0.9F + ctx.rnd().nextFloat() * 0.2F;
            Scheduler.after(i * 8 + ctx.rnd().nextInt(3),
                    () -> sound(ctx.level(), at, ModSounds.KNOCK.get(), 1.2F, pitch));
        }
    }

    /** Parpadeo de las luces cercanas. */
    private static void lightFlicker(Ctx ctx, ServerPlayer target, int intensity) {
        List<BlockPos> lamps = near(ctx.fac().anchors("lamp"), target.position(), 0, 16);
        if (lamps.isEmpty()) return;
        Collections.shuffle(lamps, new java.util.Random(ctx.rnd().nextLong()));
        List<BlockPos> chosen = lamps.subList(0, Math.min(lamps.size(), 2 + intensity * 2));
        int[] pattern = intensity >= 3 ? new int[]{2, 2, 3, 2, 2, 4, 2, 8} : new int[]{3, 3, 5, 2, 6};
        LightControl.flicker(ctx.level(), chosen, pattern);
        sound(ctx.level(), target.blockPosition(), ModSounds.LIGHT_ZAP.get(), 0.8F, 1.0F);
    }

    /** Apagón de toda la instalación; los televisores se apagan también. */
    private static void blackout(Ctx ctx, int ticks) {
        LightControl.blackout(ctx.level(), ctx.fac(), ticks);
        TvControl.releaseAll(ctx.level());
        for (ServerPlayer p : ctx.players()) {
            ctx.level().playSound(null, p.blockPosition(), ModSounds.BLACKOUT.get(), SoundSource.AMBIENT, 1.0F, 1.0F);
        }
    }

    /** Televisores cercanos se encienden solos un momento. */
    private static void tvFlicker(Ctx ctx, ServerPlayer target, int intensity) {
        List<BlockPos> tvs = new ArrayList<>(near(ctx.fac().anchors("tv"), target.position(), 0, 18));
        Collections.shuffle(tvs, new java.util.Random(ctx.rnd().nextLong()));
        for (int i = 0; i < tvs.size() && i < 1 + intensity; i++) {
            TvScreen s = ctx.rnd().nextBoolean() ? TvScreen.STATIC : TvScreen.INTERFERENCE;
            TvControl.hold(ctx.level(), tvs.get(i), s, 30 + ctx.rnd().nextInt(50), 0.0D);
        }
    }

    /** Un televisor emite risas distorsionadas. */
    private static void tvVoices(Ctx ctx, ServerPlayer target) {
        BlockPos tv = pick(near(ctx.fac().anchors("tv"), target.position(), 4, 24), ctx.rnd());
        if (tv == null) return;
        TvControl.hold(ctx.level(), tv, TvScreen.INTERFERENCE, 80, 0.0D);
        sound(ctx.level(), tv, ModSounds.TV_VOICE.get(), 1.2F, 0.95F + ctx.rnd().nextFloat() * 0.1F);
        Scheduler.after(30, () -> sound(ctx.level(), tv, ModSounds.TV_VOICE.get(), 1.0F, 0.9F));
    }

    /** Un televisor muestra su rostro y se apaga cuando el jugador se acerca. */
    private static void tvFaceScare(Ctx ctx, ServerPlayer target) {
        List<BlockPos> tvs = near(ctx.fac().anchors("tv"), target.position(), 6, 16);
        if (tvs.isEmpty()) tvs = near(ctx.fac().anchors("tv"), target.position(), 0, 30);
        BlockPos tv = pick(tvs, ctx.rnd());
        if (tv == null) return;
        TvControl.hold(ctx.level(), tv, TvScreen.FACE, 1200, 3.5D);
        sound(ctx.level(), tv, ModSounds.TV_FACE.get(), 1.0F, 1.0F);
    }

    /** Todos los televisores muestran su rostro a la vez. */
    public static void tvAllFace(Ctx ctx, int ticks) {
        for (BlockPos tv : ctx.fac().anchors("tv")) {
            if (ctx.level().isLoaded(tv)) TvControl.hold(ctx.level(), tv, TvScreen.FACE, ticks, 0.0D);
        }
        for (ServerPlayer p : ctx.players()) {
            ctx.level().playSound(null, p.blockPosition(), ModSounds.TV_FACE.get(), SoundSource.AMBIENT, 1.0F, 0.9F);
        }
    }

    /** Pasos en una habitación vacía. */
    private static void steps(Ctx ctx, ServerPlayer target, int count) {
        BlockPos at = pick(near(ctx.fac().anchors("hide_spot"), target.position(), 10, 36), ctx.rnd());
        if (at == null) return;
        for (int i = 0; i < count; i++) {
            Scheduler.after(i * 10, () -> sound(ctx.level(), at, ModSounds.STEP_HEAVY.get(), 1.0F, 1.0F));
        }
    }

    /** Mensaje discreto sobre la historia. */
    private static void hint(Ctx ctx, String text) {
        if (text == null || text.isEmpty()) return;
        for (ServerPlayer p : ctx.players()) {
            p.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
        }
    }
}
