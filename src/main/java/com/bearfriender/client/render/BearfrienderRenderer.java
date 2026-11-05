package com.bearfriender.client.render;

import com.bearfriender.BearfrienderMod;
import com.bearfriender.entity.BearfrienderEntity;
import com.bearfriender.entity.ScreenMode;
import com.bearfriender.entity.anim.BearAnimator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Dibuja a Bearfriender: malla propia con esqueleto, animada y deformada en CPU.
 * La pantalla de la panza se dibuja aparte, con su propia textura y a plena luz.
 */
public class BearfrienderRenderer extends EntityRenderer<BearfrienderEntity> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(BearfrienderMod.MOD_ID, "textures/entity/bearfriender.png");
    private static final ResourceLocation SCREEN_OFF = screen("screen_off");
    private static final ResourceLocation SCREEN_FACE = screen("screen_face");
    private static final ResourceLocation SCREEN_SMILE = screen("screen_smile");
    private static final ResourceLocation[] SCREEN_STATIC = frames("screen_static_");
    private static final ResourceLocation[] SCREEN_INTERFERENCE = frames("screen_interference_");

    // Búferes reutilizados (el renderizado va en un único hilo)
    private Matrix4f[] world = new Matrix4f[0];
    private float[] mat = new float[0];
    private float[] sp = new float[0];
    private float[] sn = new float[0];

    public BearfrienderRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.7F;
    }

    private static ResourceLocation screen(String name) {
        return new ResourceLocation(BearfrienderMod.MOD_ID, "textures/entity/" + name + ".png");
    }

    private static ResourceLocation[] frames(String prefix) {
        ResourceLocation[] r = new ResourceLocation[4];
        for (int i = 0; i < 4; i++) r[i] = screen(prefix + i);
        return r;
    }

    @Override
    public ResourceLocation getTextureLocation(BearfrienderEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(BearfrienderEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        BearfrienderModel model = BearfrienderModel.get();
        if (model != null) {
            draw(model, entity, partialTick, poseStack, buffer, packedLight);
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private void draw(BearfrienderModel m, BearfrienderEntity entity, float partialTick, PoseStack poseStack,
                      MultiBufferSource buffer, int packedLight) {
        // ---- Animación ----
        BearAnimator animator = entity.animator();
        animator.update((entity.tickCount + partialTick) / 20.0F, locomotion(entity), m.animations, m.boneCount);
        float[] rot = animator.rot();
        applyHeadTracking(m, entity, partialTick, rot);

        // ---- Matrices de hueso y deformación ----
        ensureBuffers(m);
        computeBoneMatrices(m, rot, animator.pos(), animator.scl());
        skin(m);

        // ---- Dibujo ----
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        poseStack.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
        PoseStack.Pose pose = poseStack.last();

        VertexConsumer body = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        emit(body, m, 0, m.mainQuads, pose, packedLight, overlay);

        ScreenMode mode = entity.getScreenMode();
        int frame = (entity.tickCount / 2) & 3;
        ResourceLocation screenTex = switch (mode) {
            case OFF -> SCREEN_OFF;
            case STATIC -> SCREEN_STATIC[frame];
            case FACE -> SCREEN_FACE;
            case SMILE -> SCREEN_SMILE;
            case INTERFERENCE -> SCREEN_INTERFERENCE[frame];
        };
        int screenLight = mode == ScreenMode.OFF ? packedLight : LightTexture.FULL_BRIGHT;
        VertexConsumer screen = buffer.getBuffer(RenderType.entityCutoutNoCull(screenTex));
        emit(screen, m, m.mainQuads, m.screenQuads, pose, screenLight, overlay);
        poseStack.popPose();
    }

    /** Elige la animación de bucle según el estado de la IA y si se está moviendo. */
    private static String locomotion(BearfrienderEntity e) {
        if (e.isDeadOrDying()) return "idle";
        double dx = e.getX() - e.xo;
        double dz = e.getZ() - e.zo;
        boolean moving = dx * dx + dz * dz > 0.0004D;
        return switch (e.getBearState()) {
            case STUNNED -> "stunned";
            case HUNTING, ATTACKING, BOSS_PHASE -> moving ? "run" : "idle";
            case STALKING -> moving ? "stalk" : "idle";
            case HIDING -> moving ? "crawl" : "crouch";
            case INVESTIGATING, OBSERVING -> moving ? "walk" : "look_around";
            default -> moving ? "walk" : "idle";
        };
    }

    /** La cabeza y el cuello siguen la mirada de la entidad, sumada a la animación. */
    private static void applyHeadTracking(BearfrienderModel m, BearfrienderEntity e, float partialTick, float[] rot) {
        if (e.isDeadOrDying()) return;
        int head = m.bone("head");
        int neck3 = m.bone("neck_3");
        if (head < 0 || neck3 < 0) return;
        float headYaw = Mth.rotLerp(partialTick, e.yHeadRotO, e.yHeadRot);
        float bodyYaw = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot);
        float yaw = Mth.clamp(-Mth.wrapDegrees(headYaw - bodyYaw), -60.0F, 60.0F);
        float pitch = Mth.clamp(Mth.lerp(partialTick, e.xRotO, e.getXRot()), -40.0F, 40.0F);
        rot[head * 3 + 1] += yaw * 0.7F;
        rot[neck3 * 3 + 1] += yaw * 0.3F;
        rot[head * 3] += pitch * 0.8F;
        rot[neck3 * 3] += pitch * 0.2F;
    }

    private void ensureBuffers(BearfrienderModel m) {
        if (world.length != m.boneCount) {
            world = new Matrix4f[m.boneCount];
            for (int i = 0; i < world.length; i++) world[i] = new Matrix4f();
            mat = new float[m.boneCount * 16];
        }
        if (sp.length != m.vertexCount * 3) {
            sp = new float[m.vertexCount * 3];
            sn = new float[m.vertexCount * 3];
        }
    }

    /** world[b] = world[padre] * T(pivote+pos) * Rz * Ry * Rx * S * T(-pivote) */
    private void computeBoneMatrices(BearfrienderModel m, float[] rot, float[] pos, float[] scl) {
        for (int b = 0; b < m.boneCount; b++) {
            Matrix4f w = world[b];
            int p = m.parent[b];
            if (p < 0) w.identity(); else w.set(world[p]);
            int o = b * 3;
            float px = m.pivot[o], py = m.pivot[o + 1], pz = m.pivot[o + 2];
            w.translate(px + pos[o], py + pos[o + 1], pz + pos[o + 2])
                    .rotateZ(rot[o + 2] * Mth.DEG_TO_RAD)
                    .rotateY(rot[o + 1] * Mth.DEG_TO_RAD)
                    .rotateX(rot[o] * Mth.DEG_TO_RAD)
                    .scale(scl[o], scl[o + 1], scl[o + 2])
                    .translate(-px, -py, -pz);
            w.get(mat, b * 16);
        }
    }

    /** Deformación por pesos (hasta 4 huesos por vértice), en CPU. */
    private void skin(BearfrienderModel m) {
        for (int v = 0; v < m.vertexCount; v++) {
            float x = m.pos[v * 3], y = m.pos[v * 3 + 1], z = m.pos[v * 3 + 2];
            float nx = m.nrm[v * 3], ny = m.nrm[v * 3 + 1], nz = m.nrm[v * 3 + 2];
            float ox = 0, oy = 0, oz = 0, onx = 0, ony = 0, onz = 0;
            for (int k = 0; k < 4; k++) {
                float w = m.weight[v * 4 + k];
                if (w <= 0.0F) continue;
                int a = m.joint[v * 4 + k] * 16;
                ox += w * (mat[a] * x + mat[a + 4] * y + mat[a + 8] * z + mat[a + 12]);
                oy += w * (mat[a + 1] * x + mat[a + 5] * y + mat[a + 9] * z + mat[a + 13]);
                oz += w * (mat[a + 2] * x + mat[a + 6] * y + mat[a + 10] * z + mat[a + 14]);
                onx += w * (mat[a] * nx + mat[a + 4] * ny + mat[a + 8] * nz);
                ony += w * (mat[a + 1] * nx + mat[a + 5] * ny + mat[a + 9] * nz);
                onz += w * (mat[a + 2] * nx + mat[a + 6] * ny + mat[a + 10] * nz);
            }
            float len = Mth.sqrt(onx * onx + ony * ony + onz * onz);
            if (len > 1.0E-6F) {
                onx /= len;
                ony /= len;
                onz /= len;
            }
            sp[v * 3] = ox;
            sp[v * 3 + 1] = oy;
            sp[v * 3 + 2] = oz;
            sn[v * 3] = onx;
            sn[v * 3 + 1] = ony;
            sn[v * 3 + 2] = onz;
        }
    }

    private void emit(VertexConsumer vc, BearfrienderModel m, int firstQuad, int count, PoseStack.Pose pose,
                      int light, int overlay) {
        Matrix4f pm = pose.pose();
        for (int q = firstQuad; q < firstQuad + count; q++) {
            for (int k = 0; k < 4; k++) {
                int c = q * 4 + k;
                int v = m.cornerIdx[c] * 3;
                vc.vertex(pm, sp[v], sp[v + 1], sp[v + 2])
                        .color(255, 255, 255, 255)
                        .uv(m.cornerU[c], m.cornerV[c])
                        .overlayCoords(overlay)
                        .uv2(light)
                        .normal(pose.normal(), sn[v], sn[v + 1], sn[v + 2])
                        .endVertex();
            }
        }
    }
}
