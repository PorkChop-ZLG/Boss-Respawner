package com.zonlong.bossrespawner.compat.jade;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.zonlong.bossrespawner.UniversalBossRespawner;
import com.zonlong.bossrespawner.blockentity.BossRespawnerBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IDisplayHelper;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

/**
 * Appends the Boss Respawner's configured contents to Jade's tooltip.
 *
 * <p>Two lines are <em>appended</em> below Jade's own block-name line:
 *
 * <ol>
 *   <li>{@code 复活：<boss name in gold>}</li>
 *   <li>{@code <half-scale item icon> <item name> ×<amount>} — the amount is omitted when only one item
 *       is needed</li>
 * </ol>
 *
 * <p>Everything is client-side. The block entity already syncs {@code entityTypeId}, {@code keyItemId} and
 * {@code keyAmount} to the client through {@code getUpdateTag()}, so no {@link
 * snownee.jade.api.IServerDataProvider} is needed and the tooltip works on any multiplayer server.
 *
 * <p>No mob model is ever rendered: this provider registers no icon override ({@code registerBlockIcon})
 * and creates no display entity.
 *
 * <h2>Following Jade's own chest listing</h2>
 *
 * <p>The item line copies {@code snownee.jade.addon.universal.ItemStorageProvider}, which is what draws the
 * contents of a chest:
 *
 * <ul>
 *   <li>the icon is {@link IElementHelper#smallItem} — a half-scale item in a 10×9 box, the size the chest
 *       listing uses;</li>
 *   <li>the name and the amount share <em>one</em> text run, so they cannot drift apart or disagree;</li>
 *   <li>the name goes through {@link IDisplayHelper#stripColor} so it inherits the theme's normal colour
 *       rather than the item's rarity colour;</li>
 *   <li>the icon narrates the stack ({@code clearCachedMessage}) while the text is silent
 *       ({@code message(null)}), so a screen reader announces the item once instead of twice.</li>
 * </ul>
 *
 * <p>The icon deliberately holds a single item: a count badge cannot express more than a full stack, and
 * pairing a badge clamped to 64 with a printed {@code ×100} showed two different numbers for one
 * requirement. See {@link #amountToShow(int)}.
 *
 * <h2>Colouring rule (do not "improve" this)</h2>
 *
 * <p>Apart from the boss name, nothing here sets a colour. Jade's {@code TextElement} draws text with
 * {@code IThemeHelper.getNormalColor()} as the default, so unstyled components follow the player's theme.
 * Wrapping a line in {@code IThemeHelper.title(...)} would paint it in the theme's <em>title</em> colour
 * instead — a different palette slot (white by default, where normal is grey). The boss name is the one
 * deliberate exception: it is hard-coded gold so it stands out from the rest of the tooltip.
 *
 * <h2>Translation rule</h2>
 *
 * <p>Every label is passed as {@link Component#translatable}, never as a raw {@code String}. Jade's theme
 * helpers treat a non-{@code MutableComponent} argument as <em>literal text</em>, so handing them a
 * translation key prints the key itself on screen.
 */
public final class BossRespawnerJadeProvider implements IBlockComponentProvider {

    public static final BossRespawnerJadeProvider INSTANCE = new BossRespawnerJadeProvider();

    /** Provider id. Unique within the block-component registry, which is all Jade requires. */
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(UniversalBossRespawner.MODID, "cage_contents");

    /**
     * Gap between the half-scale icon and its text. Jade's chest listing gets a similar gap from the
     * amount-column padding it inserts; a single line needs an explicit spacer instead.
     */
    private static final int ICON_TEXT_GAP = 2;

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
     * Appends {@code 复活：<name>}. The name is the entity type's translated description, in gold — plain
     * text, never a rendered model.
     */
    private static void appendBossLine(ITooltip tooltip, BossRespawnerBlockEntity cage) {
        Component bossName = resolveBossName(cage.getEntityTypeId());
        if (bossName != null) {
            tooltip.add(Component.translatable("boss_respawner.jade.boss", bossName));
        }
    }

    /**
     * Appends {@code <half-scale icon> <name> ×<amount>} on a single line, with no leading label — the icon
     * already says what the line is about, exactly as in Jade's chest listing.
     */
    private static void appendKeyItemLine(ITooltip tooltip, BossRespawnerBlockEntity cage) {
        Item item = cage.getCachedKeyItem();
        if (item == null || item == Items.AIR) {
            return;
        }

        ItemStack stack = new ItemStack(item, 1);
        int amount = amountToShow(Math.max(1, cage.getKeyAmount()));

        // One text run for the name and the amount: they are two halves of the same statement.
        MutableComponent text = IDisplayHelper.get().stripColor(stack.getHoverName());
        if (amount > 0) {
            text.append(" ").append(Component.translatable("boss_respawner.jade.amount", amount));
        }

        IElementHelper elements = IElementHelper.get();
        List<IElement> line = new ArrayList<>(3);
        line.add(elements.smallItem(stack).clearCachedMessage());
        line.add(elements.spacer(ICON_TEXT_GAP, 0));
        line.add(elements.text(text).message(null));
        tooltip.add(line);
    }

    /**
     * The exact amount to print after the item name, or {@code 0} when no number is needed because one item
     * is the only possibility.
     *
     * <p>The value is never clamped. Clamping was the bug: it belongs to a count badge, which this line no
     * longer uses, so any clamp would understate what the cage actually consumes.
     */
    static int amountToShow(int required) {
        return required > 1 ? required : 0;
    }

    /**
     * Resolves a configured entity-type id for display, styled gold.
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
            return gold(entityTypeId);
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        return type != null ? type.getDescription().copy().withStyle(ChatFormatting.YELLOW) : gold(entityTypeId);
    }

    /** {@code §e}: the boss name is meant to stand out, so it does not follow the theme. */
    private static Component gold(String text) {
        return Component.literal(text).withStyle(ChatFormatting.YELLOW);
    }
}
