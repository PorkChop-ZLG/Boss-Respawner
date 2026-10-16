package com.zonlong.bossrespawner.activation;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Counts and spends a rule's key items across a player's inventory.
 *
 * <p>A rule's {@code activation.amount} is the <em>total</em> requirement, and it is shown as such in the
 * Jade tooltip. It used to be compared against, and taken from, the single held stack, which made any
 * amount above the item's stack size impossible to pay: {@code amount: 100} could never be satisfied by a
 * stack capped at 64, no matter what else the player carried. Everything here therefore works on a list of
 * slots rather than one stack.
 *
 * <p>These methods are deliberately free of any {@code Player} or {@code Inventory} reference: callers pass
 * {@link #paymentOrder} the raw slot lists. That keeps the ordering and arithmetic unit-testable, which
 * matters because the check and the payment must agree exactly — if they disagree the cage would either
 * summon for free or refuse a player who has paid in full.
 */
public final class KeyItemPayment {

    private KeyItemPayment() {
    }

    /**
     * The slots a player pays from, in the order they are spent.
     *
     * <p>Main hand first (that is what the player is pointing with), then the offhand, then the rest of the
     * hotbar, then the main inventory. The held slot is included exactly once, so an amount can never be
     * paid twice out of the same stack.
     *
     * <p>Armour is excluded, and container contents (shulker boxes, bundles) are not opened — only the
     * slots themselves are counted.
     *
     * @param items    the 36 hotbar + main inventory slots
     * @param selected index of the held hotbar slot
     * @param offhand  the offhand slots
     */
    public static List<ItemStack> paymentOrder(List<ItemStack> items, int selected, List<ItemStack> offhand) {
        List<ItemStack> order = new ArrayList<>(items.size() + offhand.size());
        if (selected >= 0 && selected < items.size()) {
            order.add(items.get(selected));
        }
        order.addAll(offhand);
        for (int i = 0; i < items.size(); i++) {
            if (i != selected) {
                order.add(items.get(i));
            }
        }
        return order;
    }

    /** Total number of {@code item} sitting in the given slots. */
    public static int count(List<ItemStack> slots, Item item) {
        int total = 0;
        for (ItemStack stack : slots) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Whether the slots hold at least {@code amount} of {@code item}. */
    public static boolean canPay(List<ItemStack> slots, Item item, int amount) {
        return amount <= 0 || count(slots, item) >= amount;
    }

    /**
     * Removes up to {@code amount} of {@code item}, earliest slot first, leaving later slots untouched once
     * the requirement is met.
     *
     * @return the number actually removed; callers should treat a shortfall as an error, since
     *         {@link #canPay} must have passed first
     */
    public static int drain(List<ItemStack> slots, Item item, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int remaining = amount;
        for (ItemStack stack : slots) {
            if (remaining == 0) {
                break;
            }
            if (!stack.is(item)) {
                continue;
            }
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
        }
        return amount - remaining;
    }
}
