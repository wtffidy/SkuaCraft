package io.github.wtffidy.skuacraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.keksuccino.rinku.RinkuBrowser;
import io.github.wtffidy.skuacraft.block.SkuaRackBlock;
import io.github.wtffidy.skuacraft.block.entity.SkuaRackBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class SkuaRackRenderer implements BlockEntityRenderer<SkuaRackBlockEntity> {
    private static final float SCREEN_LEFT = 1.8F / 16.0F;
    private static final float SCREEN_RIGHT = 7.0F / 16.0F;
    private static final float SCREEN_BOTTOM = 13.65F / 16.0F;
    private static final float SCREEN_TOP = 14.95F / 16.0F;
    private static final float SCREEN_FRONT = 13.505F / 16.0F;

    public SkuaRackRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SkuaRackBlockEntity rack, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        String accountId = rack.getSelectedAccountId();
        if (accountId.isBlank()) {
            return;
        }

        RinkuBrowser browser = SkuaPlayerBrowserSession.getBrowser(accountId);
        if (browser == null || !browser.isTextureReady()) {
            return;
        }

        ResourceLocation texture = browser.getTextureIdentifier();
        if (texture == null) {
            return;
        }

        poseStack.pushPose();
        Direction facing = rack.getBlockState().getValue(SkuaRackBlock.FACING);
        float rotation = switch (facing) {
            case EAST -> 270.0F;
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case NORTH -> 180.0F;
            default -> 0.0F;
        };

        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        poseStack.translate(-0.5D, -0.5D, -0.5D);

        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(texture));

        // This quad replaces the static AQW artwork inside the supplied rack model's front display.
        addVertex(consumer, pose, normal, SCREEN_LEFT, SCREEN_BOTTOM, SCREEN_FRONT, 0.0F, 1.0F, packedOverlay, packedLight);
        addVertex(consumer, pose, normal, SCREEN_RIGHT, SCREEN_BOTTOM, SCREEN_FRONT, 1.0F, 1.0F, packedOverlay, packedLight);
        addVertex(consumer, pose, normal, SCREEN_RIGHT, SCREEN_TOP, SCREEN_FRONT, 1.0F, 0.0F, packedOverlay, packedLight);
        addVertex(consumer, pose, normal, SCREEN_LEFT, SCREEN_TOP, SCREEN_FRONT, 0.0F, 0.0F, packedOverlay, packedLight);

        poseStack.popPose();
    }

    private static void addVertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
                                  float x, float y, float z, float u, float v,
                                  int packedOverlay, int packedLight) {
        consumer.vertex(pose, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(packedOverlay)
                .uv2(packedLight)
                .normal(normal, 0.0F, 0.0F, 1.0F)
                .endVertex();
    }
}
