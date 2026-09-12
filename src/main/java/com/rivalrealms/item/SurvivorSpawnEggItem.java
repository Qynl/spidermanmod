package com.rivalrealms.item;

import com.rivalrealms.entity.Archetype;
import com.rivalrealms.entity.ModEntities;
import com.rivalrealms.entity.SurvivorEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A real, textured spawn egg for a specific Rival Realms survivor culture.
 * Vanilla SpawnEggItem cannot express the culture loadout, so this item creates
 * the same server-authoritative survivor and assigns its archetype immediately.
 */
public final class SurvivorSpawnEggItem extends Item {
    private final Archetype archetype;

    public SurvivorSpawnEggItem(Settings settings) {
        this(settings, null);
    }

    public SurvivorSpawnEggItem(Settings settings, Archetype archetype) {
        super(settings);
        this.archetype = archetype;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        BlockPos spawnPos = context.getBlockPos().offset(context.getSide());
        SurvivorEntity survivor = ModEntities.SURVIVOR.create(world);
        if (survivor == null) {
            return ActionResult.FAIL;
        }

        Archetype selected = archetype == null
                ? Archetype.values()[world.random.nextInt(Archetype.values().length)]
                : archetype;
        survivor.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY(),
                spawnPos.getZ() + 0.5, context.getPlayerYaw(), 0.0f);
        survivor.setArchetype(selected);
        survivor.setCustomName(Text.literal(selected.randomName(world.random)));
        if (!world.spawnEntity(survivor)) {
            return ActionResult.FAIL;
        }

        PlayerEntity player = context.getPlayer();
        if (player == null || !player.isCreative()) {
            context.getStack().decrement(1);
        }
        return ActionResult.SUCCESS;
    }
}
