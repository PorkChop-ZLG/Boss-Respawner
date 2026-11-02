package com.zonlong.bossrespawner.event;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the two Twilight Forest NBT contracts the settlement service reads.
 *
 * <p>Both are read-only: {@code HomePos} is the boss's own arena anchor (a {@code GlobalPos} written by
 * Twilight Forest's {@code EnforcedHomePoint}), and {@code MasterLich} marks a shadow clone. A wrong parse
 * here would either anchor the cage to the wrong place or hand out a cage for a clone, so the shapes are
 * tested directly rather than only through a running game.
 */
class TwilightSettlementServiceTest {

    @Test
    @DisplayName("a well-formed HomePos in the current dimension yields its block position")
    void homePosParses() {
        CompoundTag tag = new CompoundTag();
        tag.put("HomePos", homePos("minecraft:overworld", 10, 64, -20));

        assertEquals(new BlockPos(10, 64, -20),
                TwilightSettlementService.parseHomePos(tag, Level.OVERWORLD));
    }

    @Test
    @DisplayName("a HomePos in another dimension is ignored, matching Twilight Forest's own fallback")
    void homePosInAnotherDimensionIsIgnored() {
        CompoundTag tag = new CompoundTag();
        tag.put("HomePos", homePos("minecraft:overworld", 10, 64, -20));

        assertNull(TwilightSettlementService.parseHomePos(tag, Level.NETHER),
                "a cross-dimension home falls back to the death position");
    }

    @Test
    @DisplayName("missing or malformed HomePos is ignored")
    void malformedHomePosIsIgnored() {
        assertNull(TwilightSettlementService.parseHomePos(new CompoundTag(), Level.OVERWORLD));

        CompoundTag wrongLength = new CompoundTag();
        wrongLength.put("HomePos", homePos("minecraft:overworld", 1, 2, 3).copy());
        wrongLength.getCompound("HomePos").putIntArray("pos", new int[]{1, 2});
        assertNull(TwilightSettlementService.parseHomePos(wrongLength, Level.OVERWORLD));

        CompoundTag badDimension = new CompoundTag();
        CompoundTag home = new CompoundTag();
        home.putString("dimension", "###");
        home.putIntArray("pos", new int[]{1, 2, 3});
        badDimension.put("HomePos", home);
        assertNull(TwilightSettlementService.parseHomePos(badDimension, Level.OVERWORLD));
    }

    @Test
    @DisplayName("MasterLich marks a shadow clone; the real Lich has no such tag")
    void masterLichMarksAClone() {
        CompoundTag clone = new CompoundTag();
        clone.putUUID("MasterLich", UUID.randomUUID());
        assertTrue(TwilightSettlementService.hasMasterLich(clone));

        assertFalse(TwilightSettlementService.hasMasterLich(new CompoundTag()),
                "the real Lich does not write MasterLich");
    }

    private static CompoundTag homePos(String dimension, int x, int y, int z) {
        CompoundTag home = new CompoundTag();
        home.putString("dimension", dimension);
        home.putIntArray("pos", new int[]{x, y, z});
        return home;
    }
}
