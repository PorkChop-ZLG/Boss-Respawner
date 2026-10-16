package com.zonlong.bossrespawner.activation;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers paying a rule's key items out of a player's inventory.
 *
 * <p>The bug being pinned: the requirement was compared against, and taken from, the single held stack, so
 * {@code amount: 100} could never be paid with stacks capped at 64. The first test below is that exact
 * scenario.
 */
class KeyItemPaymentTest {

    private static final int MAX_STACK = 64;

    @Test
    @DisplayName("the requirement is met by the inventory total, not by one stack")
    void amountAboveStackSizeIsPayable() {
        List<ItemStack> slots = List.of(
                new ItemStack(Items.STONE, MAX_STACK),
                new ItemStack(Items.STONE, MAX_STACK));

        assertEquals(128, KeyItemPayment.count(slots, Items.STONE));
        assertTrue(KeyItemPayment.canPay(slots, Items.STONE, 100),
                "100 must be payable from two stacks of 64");
    }

    @Test
    @DisplayName("count sums every matching slot and ignores the rest")
    void countSumsMatchingSlots() {
        List<ItemStack> slots = List.of(
                new ItemStack(Items.STONE, 10),
                new ItemStack(Items.DIRT, 64),
                new ItemStack(Items.STONE, 5),
                ItemStack.EMPTY);

        assertEquals(15, KeyItemPayment.count(slots, Items.STONE));
        assertEquals(64, KeyItemPayment.count(slots, Items.DIRT));
        assertEquals(0, KeyItemPayment.count(slots, Items.GOLD_INGOT));
    }

    @Test
    @DisplayName("canPay is exact at the boundary")
    void canPayBoundary() {
        List<ItemStack> slots = List.of(new ItemStack(Items.STONE, 64));

        assertTrue(KeyItemPayment.canPay(slots, Items.STONE, 64));
        assertFalse(KeyItemPayment.canPay(slots, Items.STONE, 65));
        assertTrue(KeyItemPayment.canPay(slots, Items.STONE, 0), "a zero requirement is always payable");
    }

    @Test
    @DisplayName("drain spreads across slots, earliest first, and stops once satisfied")
    void drainSpreadsAcrossSlots() {
        ItemStack first = new ItemStack(Items.STONE, MAX_STACK);
        ItemStack second = new ItemStack(Items.STONE, MAX_STACK);
        ItemStack third = new ItemStack(Items.STONE, MAX_STACK);
        List<ItemStack> slots = new ArrayList<>(List.of(first, second, third));

        assertEquals(100, KeyItemPayment.drain(slots, Items.STONE, 100));
        assertEquals(0, first.getCount(), "the first slot is emptied first");
        assertEquals(28, second.getCount(), "the second slot covers the remainder");
        assertEquals(MAX_STACK, third.getCount(), "later slots are left untouched");
    }

    @Test
    @DisplayName("drain skips slots holding something else")
    void drainSkipsOtherItems() {
        ItemStack dirt = new ItemStack(Items.DIRT, 64);
        ItemStack stone = new ItemStack(Items.STONE, 5);
        List<ItemStack> slots = new ArrayList<>(List.of(dirt, stone));

        assertEquals(5, KeyItemPayment.drain(slots, Items.STONE, 5));
        assertEquals(64, dirt.getCount(), "an unrelated stack must be untouched");
        assertTrue(stone.isEmpty());
    }

    @Test
    @DisplayName("drain reports a shortfall rather than quietly taking less")
    void drainReportsShortfall() {
        List<ItemStack> slots = new ArrayList<>(List.of(new ItemStack(Items.STONE, 3)));
        assertEquals(3, KeyItemPayment.drain(slots, Items.STONE, 10));
    }

    @Test
    @DisplayName("drain of nothing changes nothing")
    void drainOfZeroIsANoOp() {
        ItemStack stone = new ItemStack(Items.STONE, 5);
        List<ItemStack> slots = new ArrayList<>(List.of(stone));
        assertEquals(0, KeyItemPayment.drain(slots, Items.STONE, 0));
        assertEquals(5, stone.getCount());
    }

    @Test
    @DisplayName("payment order starts at the held slot and never lists it twice")
    void paymentOrderStartsAtHeldSlotWithoutDuplicatingIt() {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            items.add(new ItemStack(Items.STONE, i + 1));
        }
        ItemStack offhandStack = new ItemStack(Items.DIRT, 1);

        int selected = 3;
        List<ItemStack> order = KeyItemPayment.paymentOrder(items, selected, List.of(offhandStack));

        assertEquals(37, order.size(), "36 inventory slots plus the offhand");
        assertSame(items.get(selected), order.get(0), "the held slot is spent first");
        assertSame(offhandStack, order.get(1), "the offhand comes second");
        assertEquals(1, order.stream().filter(stack -> stack == items.get(selected)).count(),
                "the held slot must appear exactly once, or it would be paid for twice");
        assertEquals(36, order.stream().filter(items::contains).count(),
                "every inventory slot appears exactly once");
    }

    @Test
    @DisplayName("a held stack is not counted twice just because it is also a hotbar slot")
    void paymentOrderCountsTheHeldStackOnce() {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            items.add(ItemStack.EMPTY);
        }
        items.set(0, new ItemStack(Items.STONE, 5));

        List<ItemStack> order = KeyItemPayment.paymentOrder(items, 0, List.of());

        assertEquals(5, KeyItemPayment.count(order, Items.STONE),
                "counting the payment order must not double count the held stack");
    }

    @Test
    @DisplayName("a selected index outside the slot list is tolerated")
    void paymentOrderToleratesBadSelectedIndex() {
        List<ItemStack> items = List.of(new ItemStack(Items.STONE, 1));

        assertEquals(1, KeyItemPayment.paymentOrder(items, 99, List.of()).size());
        assertEquals(1, KeyItemPayment.paymentOrder(items, -1, List.of()).size());
    }
}
