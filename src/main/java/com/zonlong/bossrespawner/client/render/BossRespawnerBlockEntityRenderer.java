package com.zonlong.bossrespawner.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zonlong.bossrespawner.UniversalBossRespawner;
import com.zonlong.bossrespawner.block.BossRespawnerBlock;
import com.zonlong.bossrespawner.blockentity.BossRespawnerBlockEntity;
import com.zonlong.bossrespawner.client.model.BossRespawnerModel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BossRespawnerBlockEntityRenderer implements BlockEntityRenderer<BossRespawnerBlockEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UniversalBossRespawner.MODID, "textures/block/boss_respawner.png");
    private static final BossRespawnerModel MODEL = new BossRespawnerModel();

    private final EntityRenderDispatcher entityRenderer;
    private final ItemRenderer itemRenderer;

    public BossRespawnerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.entityRenderer = context.getEntityRenderer();
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(BossRespawnerBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 1.501F, 0.5F);
        poseStack.scale(1.0F, -1.0F, -1.0F);

        if (be.getBlockState().getValue(BossRespawnerBlock.LIT)) {
            AnimationState state = be.getAnimationState("opening");
            if (!state.isStarted()) {
                state.start(be.tickCount);
            }
            MODEL.animateOpening(state, be.tickCount + partialTick);
        } else {
            MODEL.animateOpening(be.getAnimationState("opening"), be.tickCount + partialTick);
        }

        MODEL.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
                packedLight, packedOverlay);
        poseStack.popPose();

        renderPreviewEntity(be, partialTick, poseStack, buffer, packedLight, packedOverlay);
        renderKeyItem(be, poseStack, buffer, packedLight, packedOverlay);
    }

    private void renderPreviewEntity(BossRespawnerBlockEntity be, float partialTick, PoseStack poseStack,
                                     MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Entity currentEntity = be.getDisplayEntity(Minecraft.getInstance().level);
        if (currentEntity == null) {
            return;
        }

        float scale = 0.53125F;
        float largest = Math.max(currentEntity.getBbWidth(), currentEntity.getBbHeight());
        if (largest > 1.0F) {
            scale /= largest;
        }

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.1F, 0.5F);
        poseStack.scale(scale, scale, scale);
        this.entityRenderer.render(currentEntity, 0.0D, 0.0D, 0.0D, 0.0F, partialTick,
                poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    private void renderKeyItem(BossRespawnerBlockEntity be, PoseStack poseStack,
                               MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (be.getKeyItemId() == null || be.getKeyItemId().isEmpty()) {
            return;
        }

        Item item = be.getCachedKeyItem();
        if (item == null) {
            return;
        }

        ItemStack stack = new ItemStack(item, be.getKeyAmount());
        if (stack.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5D, 1.15D, 0.5D);
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        if (be.getLevel() != null) {
            this.itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight,
                    OverlayTexture.NO_OVERLAY, poseStack, buffer, be.getLevel(), (int) be.getBlockPos().asLong());
        }
        poseStack.popPose();
    }
}
