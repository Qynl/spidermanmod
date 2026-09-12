package com.rivalrealms.client;

import com.rivalrealms.entity.AirshipEntity;
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
 * Code-built zeppelin geometry, all in model units (16 units = 1 block):
 * <ul>
 *   <li>streamlined envelope with tapered nose and tail caps</li>
 *   <li>tail fins above and below the envelope</li>
 *   <li>two engine pods with visible spinning propellers</li>
 *   <li>open gondola with benches under the envelope</li>
 * </ul>
 * The texture is 128x128 and its regions are painted to match this exact
 * UV layout by scripts/make_art.py.
 */
public final class AirshipEntityModel extends EntityModel<AirshipEntity> {
    private final ModelPart root;
    private final ModelPart propLeft;
    private final ModelPart propRight;

    public AirshipEntityModel(ModelPart root) {
        this.root = root;
        this.propLeft = root.getChild("pods").getChild("pod_left").getChild("prop_left");
        this.propRight = root.getChild("pods").getChild("pod_right").getChild("prop_right");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();

        ModelPartData balloon = root.addChild("balloon",
                ModelPartBuilder.create().uv(0, 0)
                        .cuboid(-8.0f, 0.0f, -20.0f, 16.0f, 20.0f, 40.0f),
                ModelTransform.pivot(0.0f, 36.0f, 0.0f));
        balloon.addChild("nose",
                ModelPartBuilder.create().uv(0, 62)
                        .cuboid(-6.0f, 3.0f, -6.0f, 12.0f, 16.0f, 6.0f),
                ModelTransform.pivot(0.0f, 0.0f, -20.0f));
        balloon.addChild("tail_cap",
                ModelPartBuilder.create().uv(38, 62)
                        .cuboid(-6.0f, 3.0f, 0.0f, 12.0f, 16.0f, 6.0f),
                ModelTransform.pivot(0.0f, 0.0f, 20.0f));
        balloon.addChild("fin_top",
                ModelPartBuilder.create().uv(78, 62)
                        .cuboid(-1.0f, -12.0f, -5.0f, 2.0f, 12.0f, 10.0f),
                ModelTransform.pivot(0.0f, 1.0f, 16.0f));
        balloon.addChild("fin_bottom",
                ModelPartBuilder.create().uv(104, 62)
                        .cuboid(-1.0f, 0.0f, -5.0f, 2.0f, 12.0f, 10.0f),
                ModelTransform.pivot(0.0f, 19.0f, 16.0f));

        ModelPartData pods = root.addChild("pods",
                ModelPartBuilder.create(),
                ModelTransform.pivot(0.0f, 0.0f, 0.0f));
        ModelPartData podLeft = pods.addChild("pod_left",
                ModelPartBuilder.create().uv(0, 88)
                        .cuboid(-3.0f, -5.0f, -3.0f, 6.0f, 10.0f, 6.0f),
                ModelTransform.pivot(-8.5f, 40.0f, 14.0f));
        podLeft.addChild("prop_left",
                ModelPartBuilder.create().uv(52, 88)
                        .cuboid(-1.0f, -7.0f, -1.0f, 2.0f, 14.0f, 2.0f),
                ModelTransform.pivot(0.0f, 0.0f, -4.5f));
        ModelPartData podRight = pods.addChild("pod_right",
                ModelPartBuilder.create().uv(26, 88)
                        .cuboid(-3.0f, -5.0f, -3.0f, 6.0f, 10.0f, 6.0f),
                ModelTransform.pivot(8.5f, 40.0f, 14.0f));
        podRight.addChild("prop_right",
                ModelPartBuilder.create().uv(62, 88)
                        .cuboid(-1.0f, -7.0f, -1.0f, 2.0f, 14.0f, 2.0f),
                ModelTransform.pivot(0.0f, 0.0f, -4.5f));

        root.addChild("gondola",
                ModelPartBuilder.create().uv(72, 86)
                        .cuboid(-5.0f, 0.0f, -9.0f, 10.0f, 8.0f, 18.0f),
                ModelTransform.pivot(0.0f, 0.0f, 0.0f));

        return TexturedModelData.of(modelData, 128, 128);
    }

    @Override
    public void setAngles(AirshipEntity entity, float limbAngle, float limbDistance,
                          float animationProgress, float headYaw, float headPitch) {
        float speed = entity.isPowered() ? 0.85f : 0.12f;
        float spin = animationProgress * speed;
        this.propLeft.roll = spin;
        this.propRight.roll = -spin;
        // Idle bobbing so a moored ship still feels alive, plus the pilot's bank.
        this.root.pivotY = (float) Math.sin(animationProgress * 0.08) * 0.6f;
        this.root.roll = (float) Math.toRadians(entity.getBank());
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay) {
        this.root.render(matrices, vertices, light, overlay);
    }
}
