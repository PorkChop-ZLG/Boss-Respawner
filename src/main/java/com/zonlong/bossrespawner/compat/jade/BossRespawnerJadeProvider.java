package com.zonlong.bossrespawner.compat.jade;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.zonlong.bossrespawner.UniversalBossRespawner;
import com.zonlong.bossrespawner.blockentity.BossRespawnerBlockEntity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

/**
 * Appends the Boss Respawner's configured contents to Jade's tooltip.
 *
 * <p>Two lines are <em>appended</em> below Jade's own block-name line:
 *
 * <ol>
 *   <li>{@code 重生Boss：<translated entity name>}</li>
 *   <li>{@code 重生物品：<item icon with a count badge> <translated item name>}</li>
 * </ol>
 *
 * <p>Everything is client-side. The block entity already syncs {@code entityTypeId}, {@code keyItemId} and
 * {@code keyAmount} to the client through {@code getUpdateTag()}, so no {@link
 * snownee.jade.api.IServerDataProvider} is needed and the tooltip works on any multiplayer server.
 *
 * <p>No mob model is ever rendered: this provider registers no icon override ({@code registerBlockIcon})
 * and creates no display entity. It only contributes text and an item element.
 */
public final class BossRespawnerJadeProvider implements IBlockComponentProvider {

    public static final BossRespawnerJadeProvider INSTANCE = new BossRespawnerJadeProvider();

    /** Provider id. Unique within the block-component registry, which is all Jade requires. */
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(UniversalBossRespawner.MODID, "cage_contents");

    /** Horizontal gap between a label, the item icon and the item name on the same line. */
    private static final int ELEMENT_GAP = 4;

    private BossRespawnerJadeProvider() {
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof BossRespawnerBlockEntity cage)) {
            return;
        }
        // Both lines are optional: a cage with no configuration yet, or one whose configuration points at
        // content that is no longer installed, must degrade quietly instead of rendering something wrong.
        appendBossLine(tooltip, cage);
        appendKeyItemLine(tooltip, cage);
    }

    /**
     * Appends {@code 重生Boss：<name>}. The name is the entity type's translated description — plain text,
     * never a rendered model.
     */
    private static void appendBossLine(ITooltip tooltip, BossRespawnerBlockEntity cage) {
        Component bossName = resolveBossName(cage.getEntityTypeId());
        if (bossName != null) {
            tooltip.add(IThemeHelper.get().title(
                    Component.translatable("boss_respawner.jade.boss", bossName)));
        }
    }

    /**
     * Appends {@code 重生物品：<icon> <name>} on a single line.
     *
     * <p>The icon's count badge comes from NeoForge's item decorators via Jade's {@code ItemStackElement},
     * so the required amount needs no custom text of its own — except beyond a full stack, where the badge
     * cannot express it and the exact number is spelled out.
     */
    private static void appendKeyItemLine(ITooltip tooltip, BossRespawnerBlockEntity cage) {
        Item item = cage.getCachedKeyItem();
        if (item == null || item == Items.AIR) {
            return;
        }

        int required = Math.max(1, cage.getKeyAmount());
        int badgeCount = Math.min(required, item.getDefaultMaxStackSize());
        ItemStack stack = new ItemStack(item, badgeCount);

        IElementHelper elements = IElementHelper.get();
        List<IElement> line = new ArrayList<>(7);
        line.add(elements.text(title("boss_respawner.jade.key_item")));
        line.add(elements.spacer(ELEMENT_GAP, 0));
        line.add(elements.item(stack));
        if (required > badgeCount) {
            line.add(elements.spacer(ELEMENT_GAP, 0));
            line.add(elements.text(title(Component.translatable("boss_respawner.jade.amount", required))));
        }
        line.add(elements.spacer(ELEMENT_GAP, 0));
        line.add(elements.text(title(stack.getHoverName())));
        tooltip.add(line);
    }

    /**
     * Resolves a configured entity-type id for display.
     *
     * @return {@code null} when nothing is configured (so the line is skipped entirely), otherwise the
     *         translated entity name — or the raw id, so a rule pointing at a removed mod is still
     *         diagnosable in game rather than silently blank.
     */
    @Nullable
    private static Component resolveBossName(String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(entityTypeId);
        if (id == null) {
            return Component.literal(entityTypeId);
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        return type != null ? type.getDescription() : Component.literal(entityTypeId);
    }

    /**
     * Colours a component with Jade's configured theme rather than a hard-coded {@code \u00a76}, so the
     * tooltip follows whatever theme the player selected.
     *
     * <p>Note that {@link IThemeHelper#title} strips existing colours first, so an item's rarity colour is
     * intentionally flattened to the theme's title colour on this line.
     */
    private static Component title(Object componentOrString) {
        return IThemeHelper.get().title(componentOrString);
    }
}
