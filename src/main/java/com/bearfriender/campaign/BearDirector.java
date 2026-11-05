package com.bearfriender.campaign;

import com.bearfriender.entity.BearfrienderEntity;
import com.bearfriender.entity.ScreenMode;
import com.bearfriender.entity.ai.BearState;
import com.bearfriender.registry.ModEntities;
import com.bearfriender.structure.FacilityData;
import com.bearfriender.structure.FacilityLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Garantiza que existe UN solo Bearfriender por campaña y gestiona sus apariciones "de escena".
 * Mientras no está en escena espera, inmóvil y sin IA, en una cámara sellada. La IA de caza llega en la Fase 8.
 */
public final class BearDirector {
    private static int flashToken;

    private BearDirector() {}

    private static AABB bounds(FacilityData fac) {
        BlockPos min = fac.min();
        BlockPos max = fac.max();
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    public static BlockPos holdPos(FacilityData fac) {
        BlockPos p = fac.anchor("bear_hold");
        if (p != null) return p;
        List<BlockPos> hide = fac.anchors("hide_spot");
        return hide.isEmpty() ? null : hide.get(hide.size() - 1);
    }

    /** Devuelve el oso de la campaña, creándolo si no existe. Devuelve null si su zona aún no está cargada. */
    public static BearfrienderEntity ensureBear(ServerLevel level, FacilityData fac, CampaignData camp) {
        UUID id = camp.getBearId();
        if (id != null) {
            Entity e = level.getEntity(id);
            if (e instanceof BearfrienderEntity b && b.isAlive()) {
                camp.setBear(id, b.blockPosition());
                return b;
            }
            if (!level.isLoaded(camp.getBearPos())) return null;      // todavía no se ha cargado: no duplicar
        }
        discardStray(level, fac, null);
        BlockPos hold = holdPos(fac);
        if (hold == null || !level.isLoaded(hold)) return null;
        BearfrienderEntity bear = ModEntities.BEARFRIENDER.get().create(level);
        if (bear == null) return null;
        bear.moveTo(hold.getX() + 0.5, hold.getY() + 0.07, hold.getZ() + 0.5, 0.0F, 0.0F);
        bear.setNoAi(true);
        bear.setInvulnerable(true);
        bear.setBearState(BearState.DORMANT);
        bear.setScreenMode(ScreenMode.STATIC);
        level.addFreshEntity(bear);
        camp.setBear(bear.getUUID(), hold);
        return bear;
    }

    /** Elimina los Bearfriender de la instalación, salvo el indicado (si hay alguno). */
    public static void discardStray(ServerLevel level, FacilityData fac, UUID keep) {
        for (BearfrienderEntity b : level.getEntitiesOfClass(BearfrienderEntity.class, bounds(fac))) {
            if (keep == null || !b.getUUID().equals(keep)) b.discard();
        }
    }

    /** Mantenimiento periódico: un único oso vivo. */
    public static void maintain(ServerLevel level, FacilityData fac, CampaignData camp) {
        BearfrienderEntity bear = ensureBear(level, fac, camp);
        if (bear != null) discardStray(level, fac, bear.getUUID());
    }

    public static void removeAll(ServerLevel level, FacilityData fac, CampaignData camp) {
        flashToken++;
        discardStray(level, fac, null);
        camp.clearBear();
    }

    /** Devuelve al oso a su cámara sellada, inmóvil y oculto. */
    public static void hide(ServerLevel level, FacilityData fac, CampaignData camp) {
        flashToken++;
        BearfrienderEntity bear = ensureBear(level, fac, camp);
        BlockPos hold = holdPos(fac);
        if (bear == null || hold == null) return;
        bear.moveTo(hold.getX() + 0.5, hold.getY() + 0.07, hold.getZ() + 0.5, 0.0F, 0.0F);
        bear.setNoAi(true);
        bear.setInvulnerable(true);
        bear.setBearState(BearState.DORMANT);
        bear.setScreenMode(ScreenMode.STATIC);
    }

    private static void face(BearfrienderEntity bear, Vec3 target) {
        double dx = target.x - bear.getX();
        double dz = target.z - bear.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        bear.setYRot(yaw);
        bear.yBodyRot = yaw;
        bear.setYHeadRot(yaw);
    }

    /** ¿Caben 4 bloques de alto sin colisión (el oso mide 3) en esa celda? */
    private static boolean clearFor(ServerLevel level, BlockPos feet) {
        for (int dy = 0; dy < 4; dy++) {
            BlockPos p = feet.above(dy);
            BlockState st = level.getBlockState(p);
            if (!st.getCollisionShape(level, p).isEmpty()) return false;
        }
        return true;
    }

    /** Todos los sitios donde puede aparecer: rejilla de las salas y pasillos, más las anclas de escondite. */
    private static List<BlockPos> candidateSpots(ServerLevel level, FacilityData fac) {
        List<BlockPos> out = new ArrayList<>();
        for (FacilityLayout.Room r : FacilityLayout.ROOMS) {
            if (r.id().equals("bear_hold")) continue;
            for (int x = r.x0(); x <= r.x1(); x += 2) {
                for (int z = r.z0(); z <= r.z1(); z += 2) {
                    BlockPos p = fac.origin().offset(x, r.yFloor() + 1, z);
                    if (level.isLoaded(p) && clearFor(level, p)) out.add(p);
                }
            }
        }
        for (BlockPos p : fac.anchors("hide_spot")) if (clearFor(level, p)) out.add(p);
        for (BlockPos p : fac.anchors("peek_spot")) if (clearFor(level, p)) out.add(p);
        return out;
    }

    /**
     * Aparición breve a distancia: el oso surge donde el jugador puede verlo, inmóvil, y desaparece
     * al cabo de unos ticks o si el jugador se acerca demasiado. Si no hay un sitio en el rango pedido,
     * se amplía el rango poco a poco. Devuelve false si no hay ningún sitio con línea de visión.
     */
    public static boolean flash(ServerLevel level, FacilityData fac, CampaignData camp, ServerPlayer target,
                                double minDist, double maxDist, int ticks) {
        BearfrienderEntity bear = ensureBear(level, fac, camp);
        if (bear == null) return false;
        if (bear.getBearState() != BearState.DORMANT) return false;       // ya está en escena

        List<BlockPos> spots = candidateSpots(level, fac);
        double[][] ranges = {{minDist, maxDist}, {minDist * 0.6, maxDist * 1.4}, {4, 45}};
        Vec3 eye = target.getEyePosition();
        BlockPos spot = null;
        for (double[] range : ranges) {
            List<BlockPos> visible = new ArrayList<>();
            for (BlockPos p : spots) {
                double dx = p.getX() + 0.5 - target.getX();
                double dz = p.getZ() + 0.5 - target.getZ();
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < range[0] || d > range[1] || Math.abs(p.getY() - target.getBlockY()) > 3) continue;
                Vec3 head = new Vec3(p.getX() + 0.5, p.getY() + 2.6, p.getZ() + 0.5);
                ClipContext cc = new ClipContext(eye, head, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target);
                if (level.clip(cc).getType() == HitResult.Type.MISS) visible.add(p);
            }
            if (!visible.isEmpty()) {
                spot = visible.get(level.getRandom().nextInt(visible.size()));
                break;
            }
        }
        if (spot == null) return false;

        bear.moveTo(spot.getX() + 0.5, spot.getY() + 0.07, spot.getZ() + 0.5, 0.0F, 0.0F);
        face(bear, target.position());
        bear.setNoAi(true);
        bear.setInvulnerable(true);
        bear.setBearState(BearState.OBSERVING);
        bear.setScreenMode(ScreenMode.STATIC);

        final int token = ++flashToken;
        watch(level, fac, camp, target, token, ticks);
        return true;
    }

    /** Pide una aparición y, si ahora no hay dónde, lo vuelve a intentar cada 2 segundos (hasta 10 veces). */
    public static void requestFlash(ServerLevel level, FacilityData fac, CampaignData camp, ServerPlayer target,
                                    double minDist, double maxDist, int ticks, int attemptsLeft) {
        if (flash(level, fac, camp, target, minDist, maxDist, ticks) || attemptsLeft <= 0) return;
        Scheduler.after(40, () -> {
            if (target.isAlive()) {
                requestFlash(level, fac, camp, target, minDist, maxDist, ticks, attemptsLeft - 1);
            }
        });
    }

    private static void watch(ServerLevel level, FacilityData fac, CampaignData camp, ServerPlayer target,
                              int token, int remaining) {
        Scheduler.after(5, () -> {
            if (token != flashToken) return;                       // otra aparición o un reinicio lo reemplazó
            BearfrienderEntity bear = ensureBear(level, fac, camp);
            if (bear == null) return;
            boolean tooClose = target.isAlive() && bear.distanceTo(target) < 6.0F;
            int left = remaining - 5;
            if (left <= 0 || tooClose || !target.isAlive()) {
                hide(level, fac, camp);
            } else {
                face(bear, target.position());
                watch(level, fac, camp, target, token, left);
            }
        });
    }

    /** Coloca al oso en la sala central para el combate final (la IA del jefe llega en la Fase 9). */
    public static void placeBoss(ServerLevel level, FacilityData fac, CampaignData camp) {
        flashToken++;
        BearfrienderEntity bear = ensureBear(level, fac, camp);
        BlockPos c = fac.anchor("boss_center");
        if (bear == null || c == null) return;
        bear.moveTo(c.getX() + 0.5, c.getY() + 0.07, c.getZ() + 0.5, 0.0F, 0.0F);
        bear.setNoAi(true);
        bear.setInvulnerable(false);                                 // en el combate final sí puede recibir daño
        bear.setBearState(BearState.BOSS_PHASE);
        bear.setScreenMode(ScreenMode.FACE);
    }
}
