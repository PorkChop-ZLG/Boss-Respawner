package com.zonlong.bossrespawner.client.model;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BossRespawnerModel extends HierarchicalModel<Entity> {
    private final ModelPart root;

    public BossRespawnerModel() {
        this(createBodyLayer().bakeRoot());
    }

    public BossRespawnerModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();

        PartDefinition root = partDefinition.addOrReplaceChild("root",
                CubeListBuilder.create().texOffs(33, 0)
                        .addBox(-4.0F, -18.0F, -4.0F, 8.0F, 14.0F, 8.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition bone = root.addOrReplaceChild("bone",
                CubeListBuilder.create(),
                PartPose.ZERO);

        bone.addOrReplaceChild("one",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-8.0F, -18.0F, 0.0F, 8.0F, 18.0F, 8.0F),
                PartPose.ZERO);
        bone.addOrReplaceChild("two",
                CubeListBuilder.create().texOffs(0, 0).mirror()
                        .addBox(0.0F, -18.0F, 0.0F, 8.0F, 18.0F, 8.0F),
                PartPose.ZERO);
        bone.addOrReplaceChild("three",
                CubeListBuilder.create().texOffs(0, 27).mirror()
                        .addBox(0.0F, -18.0F, -8.0F, 8.0F, 18.0F, 8.0F),
                PartPose.ZERO);
        bone.addOrReplaceChild("four",
                CubeListBuilder.create().texOffs(0, 27)
                        .addBox(-8.0F, -18.0F, -8.0F, 8.0F, 18.0F, 8.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("bone2",
                CubeListBuilder.create()
                        .texOffs(33, 23).addBox(-4.0F, -4.0F, -6.0F, 8.0F, 4.0F, 6.0F)
                        .texOffs(33, 41).addBox(-3.0F, -4.0F, -7.0F, 6.0F, 0.0F, 1.0F),
                PartPose.offset(0.0F, -18.0F, 7.0F));

        root.addOrReplaceChild("bone3",
                CubeListBuilder.create().texOffs(33, 34)
                        .addBox(-4.0F, -4.0F, 1.0F, 8.0F, 4.0F, 2.0F),
                PartPose.offset(0.0F, -18.0F, -7.0F));

        return LayerDefinition.create(meshDefinition, 128, 128);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
    }

    public void animateOpening(AnimationState animationState, float ageInTicks) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        this.animate(animationState, BossRespawnerAnimations.OPENING, ageInTicks, 1.0F);
    }
}
