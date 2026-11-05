package com.bearfriender.client.render;

import com.bearfriender.BearfrienderMod;
import com.bearfriender.entity.BrokenTeddyEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class BrokenTeddyModel extends GeoModel<BrokenTeddyEntity> {
    private static final ResourceLocation MODEL =
            new ResourceLocation(BearfrienderMod.MOD_ID, "geo/entity/broken_teddy.geo.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(BearfrienderMod.MOD_ID, "textures/entity/broken_teddy.png");
    private static final ResourceLocation ANIMATIONS =
            new ResourceLocation(BearfrienderMod.MOD_ID, "animations/entity/broken_teddy.animation.json");

    @Override
    public ResourceLocation getModelResource(BrokenTeddyEntity entity) { return MODEL; }

    @Override
    public ResourceLocation getTextureResource(BrokenTeddyEntity entity) { return TEXTURE; }

    @Override
    public ResourceLocation getAnimationResource(BrokenTeddyEntity entity) { return ANIMATIONS; }

    @Override
    public void setCustomAnimations(BrokenTeddyEntity entity, long instanceId, AnimationState<BrokenTeddyEntity> state) {
        CoreGeoBone head = getAnimationProcessor().getBone("head");
        if (head == null) return;
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        // Movimiento limitado: el osito está sentado y solo gira la cabeza un poco.
        head.setRotX(head.getRotX() + Mth.clamp(data.headPitch(), -25F, 25F) * Mth.DEG_TO_RAD);
        head.setRotY(head.getRotY() + Mth.clamp(data.netHeadYaw(), -45F, 45F) * Mth.DEG_TO_RAD);
    }
}
