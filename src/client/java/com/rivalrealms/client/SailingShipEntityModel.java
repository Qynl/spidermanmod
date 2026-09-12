package com.rivalrealms.client;

import com.rivalrealms.entity.SailingShipEntity;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Code-built sailing ship geometry shared by the merchant cog, the pirate
 * raider and the small sloop (which the renderer scales down):
 * <ul>
 *   <li>planked hull with raised fore and aft castles</li>
 *   <li>center mast with a horizontal yard, a hanging sail and a pennant</li>
 *   <li>animated sail billow and flag flutter while under way</li>
 * </ul>
 * The texture is 256x128; regions are painted by scripts/make_art.py to
 * match this UV layout (deck, hull sides, castles, mast, yard, canvas,
 * pennant).
 */
public final class SailingShipEntityModel extends EntityModel<SailingShipEntity> {
    private final ModelPart root;
    private final ModelPart sail;
    private final ModelPart flag;

    public SailingShipEntityModel(ModelPart root) {
        this.root = root;
        ModelPart mast = root.getChild("mast");
        this.sail = mast.getChild("sail");
        this.flag = mast.getChild("flag");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();

        root.addChild("hull",
                ModelPartBuilder.create().uv(0, 0)
                        .cuboid(-14.0f, 0.0f, -30.0f, 28.0f, 10.0f, 60.0f),
                ModelTransform.pivot(0.0f, 0.0f, 0.0f));
        root.addChild("castle_fore",
                ModelPartBuilder.create().uv(0, 70)
                        .cuboid(-9.0f, 0.0f, -8.0f, 18.0f, 8.0f, 10.0f),
                ModelTransform.pivot(0.0f, 10.0f, -27.0f));
        root.addChild("castle_aft",
                ModelPartBuilder.create().uv(56, 70)
                        .cuboid(-9.0f, 0.0f, -2.0f, 18.0f, 8.0f, 10.0f),
                ModelTransform.pivot(0.0f, 10.0f, 20.0f));

        ModelPartData mast = root.addChild("mast",
                ModelPartBuilder.create().uv(176, 0)
                        .cuboid(-1.5f, 0.0f, -1.5f, 3.0f, 30.0f, 3.0f),
                ModelTransform.pivot(0.0f, 9.0f, 0.0f));
        mast.addChild("yard",
                ModelPartBuilder.create().uv(188, 0)
                        .cuboid(-16.0f, -1.0f, -1.0f, 32.0f, 2.0f, 2.0f),
                ModelTransform.pivot(0.0f, 27.0f, 0.0f));
        mast.addChild("sail",
                ModelPartBuilder.create().uv(0, 96)
                        .cuboid(-16.0f, 0.0f, 0.0f, 32.0f, 24.0f, 1.0f),
                ModelTransform.pivot(0.0f, 4.0f, 0.0f));
        mast.addChild("flag",
                ModelPartBuilder.create().uv(70, 96)
                        .cuboid(0.0f, -4.0f, 0.0f, 12.0f, 7.0f, 1.0f),
                ModelTransform.pivot(0.0f, 30.0f, 0.0f));

        return TexturedModelData.of(modelData, 256, 128);
    }

    @Override
    public void setAngles(SailingShipEntity entity, float limbAngle, float limbDistance,
                          float animationProgress, float headYaw, float headPitch) {
        boolean underWay = entity.sailsSet();
        float wave = (float) Math.sin(animationProgress * 0.12) * (underWay ? 0.10f : 0.03f);
        this.sail.yaw = wave;
        this.flag.yaw = (float) Math.sin(animationProgress * 0.35) * 0.30f + 0.10f;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay) {
        this.root.render(matrices, vertices, light, overlay);
    }
}
