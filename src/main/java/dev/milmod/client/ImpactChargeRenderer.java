package dev.milmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.milmod.entity.ImpactCharge;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

final class ImpactChargeRenderer extends EntityRenderer<ImpactCharge> {
    private final ItemRenderer items;

    ImpactChargeRenderer(EntityRendererProvider.Context context) {
        super(context);
        items = context.getItemRenderer();
    }

    @Override
    public ResourceLocation getTextureLocation(ImpactCharge charge) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(ImpactCharge charge, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0, 0.12, 0);
        pose.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partial, charge.yRotO, charge.getYRot()) + 180));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.rotLerp(partial, charge.xRotO, charge.getXRot())));
        pose.mulPose(Axis.ZP.rotationDegrees((charge.tickCount + partial) * 12));
        pose.scale(0.70F, 0.70F, 0.70F);
        items.renderStatic(charge.getItem(), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, charge.level(), charge.getId());
        pose.popPose();
        super.render(charge, yaw, partial, pose, buffers, light);
    }
}
