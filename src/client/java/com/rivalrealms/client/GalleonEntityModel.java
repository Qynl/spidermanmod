package com.rivalrealms.client;

import com.rivalrealms.entity.GalleonEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Hand-built galleon: a 24x14x96 hull with a sheer fore castle, a two-tier
 * stern castle, fore and main masts with yards and billowing canvas, a
 * crow's nest, bowsprit, swallow-tail flag and long pennant. 256x256 sheet.
 */
public class GalleonEntityModel extends EntityModel<GalleonEntity> {
    private final ModelPart root;
    private final ModelPart mainsail;
    private final ModelPart foresail;
    private final ModelPart flag;
    private final ModelPart pennant;

    public GalleonEntityModel(ModelPart root) {
        this.root = root;
        ModelPart mainmast = root.getChild("mainmast");
        ModelPart foremast = root.getChild("foremast");
        this.mainsail = mainmast.getChild("mainsail");
        this.foresail = foremast.getChild("foresail");
        this.flag = mainmast.getChild("flag");
        this.pennant = foremast.getChild("pennant");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();

        root.addChild("hull",
                ModelPartBuilder.create().uv(0, 0)
                        .cuboid(-12.0f, 0.0f, -48.0f, 24.0f, 14.0f, 96.0f),
                ModelTransform.pivot(0.0f, 0.0f, 0.0f));
        root.addChild("castle_fore",
                ModelPartBuilder.create().uv(0, 110)
                        .cuboid(-7.0f, 0.0f, -8.0f, 14.0f, 7.0f, 16.0f),
                ModelTransform.pivot(0.0f, 14.0f, -40.0f));
        ModelPartData castleAft = root.addChild("castle_aft",
                ModelPartBuilder.create().uv(60, 110)
                        .cuboid(-8.0f, 0.0f, -10.0f, 16.0f, 10.0f, 20.0f),
                ModelTransform.pivot(0.0f, 14.0f, 28.0f));
        castleAft.addChild("aft_top",
                ModelPartBuilder.create().uv(140, 110)
                        .cuboid(-5.0f, 0.0f, -6.0f, 10.0f, 6.0f, 12.0f),
                ModelTransform.pivot(0.0f, 10.0f, 2.0f));
        root.addChild("bowsprit",
                ModelPartBuilder.create().uv(192, 146)
                        .cuboid(-1.0f, -1.0f, -30.0f, 2.0f, 2.0f, 30.0f),
                ModelTransform.pivot(0.0f, 16.0f, -46.0f));

        ModelPartData mainmast = root.addChild("mainmast",
                ModelPartBuilder.create().uv(0, 140)
                        .cuboid(-2.0f, 0.0f, -2.0f, 4.0f, 46.0f, 4.0f),
                ModelTransform.pivot(0.0f, 13.0f, 2.0f));
        mainmast.addChild("mainyard",
                ModelPartBuilder.create().uv(40, 140)
                        .cuboid(-22.0f, -1.0f, -1.0f, 44.0f, 2.0f, 2.0f),
                ModelTransform.pivot(0.0f, 42.0f, 0.0f));
        mainmast.addChild("mainsail",
                ModelPartBuilder.create().uv(0, 196)
                        .cuboid(-20.0f, 0.0f, 0.0f, 40.0f, 30.0f, 1.0f),
                ModelTransform.pivot(0.0f, 14.0f, 0.0f));
        mainmast.addChild("nest",
                ModelPartBuilder.create().uv(200, 150)
                        .cuboid(-3.0f, 0.0f, -3.0f, 6.0f, 4.0f, 6.0f),
                ModelTransform.pivot(0.0f, 28.0f, 0.0f));
        mainmast.addChild("flag",
                ModelPartBuilder.create().uv(0, 232)
                        .cuboid(0.0f, -4.0f, 0.0f, 14.0f, 9.0f, 1.0f),
                ModelTransform.pivot(0.0f, 45.0f, 0.0f));

        ModelPartData foremast = root.addChild("foremast",
                ModelPartBuilder.create().uv(20, 140)
                        .cuboid(-1.5f, 0.0f, -1.5f, 3.0f, 36.0f, 3.0f),
                ModelTransform.pivot(0.0f, 13.0f, -14.0f));
        foremast.addChild("foreyard",
                ModelPartBuilder.create().uv(140, 140)
                        .cuboid(-15.0f, -1.0f, -1.0f, 30.0f, 2.0f, 2.0f),
                ModelTransform.pivot(0.0f, 33.0f, 0.0f));
        foremast.addChild("foresail",
                ModelPartBuilder.create().uv(90, 196)
                        .cuboid(-14.0f, 0.0f, 0.0f, 28.0f, 22.0f, 1.0f),
                ModelTransform.pivot(0.0f, 12.0f, 0.0f));
        foremast.addChild("pennant",
                ModelPartBuilder.create().uv(40, 232)
                        .cuboid(0.0f, -3.0f, 0.0f, 22.0f, 6.0f, 1.0f),
                ModelTransform.pivot(0.0f, 35.0f, 0.0f));

        return TexturedModelData.of(modelData, 256, 256);
    }

    @Override
    public void setAngles(GalleonEntity entity, float limbAngle, float limbDistance,
                          float animationProgress, float headYaw, float headPitch) {
        boolean underWay = entity.sailsSet();
        float billow = underWay ? 0.10f : 0.03f;
        this.mainsail.yaw = (float) Math.sin(animationProgress * 0.10) * billow;
        this.foresail.yaw = (float) Math.sin(animationProgress * 0.10 + 0.9) * billow;
        this.flag.yaw = (float) Math.sin(animationProgress * 0.35) * 0.30f + 0.10f;
        this.pennant.yaw = (float) Math.sin(animationProgress * 0.30 + 0.4) * 0.34f + 0.08f;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        this.root.render(matrices, vertices, light, overlay, color);
    }
}
