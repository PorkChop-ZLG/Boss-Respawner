package com.zonlong.bossrespawner.placement;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Membership semantics of the pre-parsed block-id sets.
 *
 * <p>These checks matter because the whole point of parsing into {@code Set<ResourceLocation>} is that
 * lookups match <em>by value</em>. A list keyed on identity (which is how {@code ResourceKey} behaves, for
 * example) would silently never match the registry's own instances, and the block protection would quietly
 * stop working.
 */
class PlacementResolverTest {

    @Test
    @DisplayName("an empty list never matches, so rules without block lists pay nothing")
    void emptySetNeverMatches() {
        assertFalse(PlacementResolver.isListed(ResourceLocation.parse("minecraft:chest"), Set.of()));
    }

    @Test
    @DisplayName("a missing block id never matches")
    void nullBlockIdNeverMatches() {
        assertFalse(PlacementResolver.isListed(null, Set.of(ResourceLocation.parse("minecraft:chest"))));
    }

    @Test
    @DisplayName("matching is by value, not by object identity")
    void matchingIsByValueNotIdentity() {
        ResourceLocation listed = ResourceLocation.parse("twilightforest:dark_chest");
        ResourceLocation lookedUp = ResourceLocation.parse("twilightforest:dark_chest");
        assertFalse(listed == lookedUp, "the two instances must be distinct for this test to mean anything");

        assertTrue(PlacementResolver.isListed(lookedUp, Set.of(listed)));
    }

    @Test
    @DisplayName("a different id does not match")
    void otherIdsDoNotMatch() {
        assertFalse(PlacementResolver.isListed(
                ResourceLocation.parse("minecraft:chest"),
                Set.of(ResourceLocation.parse("twilightforest:dark_chest"))));
    }

    @Test
    @DisplayName("lookup does not depend on list position or size")
    void lookupIsSetBased() {
        Set<ResourceLocation> many = Set.of(
                ResourceLocation.parse("twilightforest:canopy_chest"),
                ResourceLocation.parse("twilightforest:canopy_trapped_chest"),
                ResourceLocation.parse("twilightforest:dark_chest"),
                ResourceLocation.parse("twilightforest:dark_trapped_chest"),
                ResourceLocation.parse("twilightforest:mangrove_chest"),
                ResourceLocation.parse("twilightforest:skull_chest"),
                ResourceLocation.parse("twilightforest:sorting_chest"),
                ResourceLocation.parse("twilightforest:twilight_oak_chest"));

        assertTrue(PlacementResolver.isListed(ResourceLocation.parse("twilightforest:skull_chest"), many));
        assertTrue(PlacementResolver.isListed(ResourceLocation.parse("twilightforest:twilight_oak_chest"), many));
        assertFalse(PlacementResolver.isListed(ResourceLocation.parse("twilightforest:time_chest"), many));
    }
}
