package com.bearfriender.client.render;

import com.bearfriender.entity.BrokenTeddyEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class BrokenTeddyRenderer extends GeoEntityRenderer<BrokenTeddyEntity> {
    public BrokenTeddyRenderer(EntityRendererProvider.Context context) {
        super(context, new BrokenTeddyModel());
        this.shadowRadius = 0.25F;
    }
}
