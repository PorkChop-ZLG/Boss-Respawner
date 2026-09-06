package com.zonlong.bossrespawner.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zonlong.bossrespawner.UniversalBossRespawner;
import com.zonlong.bossrespawner.client.model.BossRespawnerModel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

@OnlyIn(Dist.CLIENT)
public class BossRespawnerItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UniversalBossRespawner.MODID, "textures/block/boss_respawner.png");
    private static final BossRespawnerModel MODEL = new BossRespawnerModel();

    public BossRespawnerItemRenderer(BlockEntityRenderDispatcher dispatcher, net.minecraft.client.model.geom.EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 1.50F, 0.5F);
        poseStack.scale(1.0F, -1.0F, -1.0F);
        MODEL.root().getAllParts().forEach(part -> part.resetPose());
        MODEL.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
                packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    public static final class ClientExtensions implements IClientItemExtensions {
        public static final ClientExtensions INSTANCE = new ClientExtensions();
        private static BossRespawnerItemRenderer renderer;

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (renderer == null) {
                renderer = new BossRespawnerItemRenderer(
                        Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                        Minecraft.getInstance().getEntityModels());
            }
            return renderer;
        }
    }
}
