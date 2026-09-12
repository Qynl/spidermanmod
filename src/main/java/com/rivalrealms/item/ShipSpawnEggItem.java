package com.rivalrealms.item;

import com.rivalrealms.entity.Archetype;
import com.rivalrealms.entity.ModEntities;
import com.rivalrealms.entity.SailingShipEntity;
import com.rivalrealms.entity.SurvivorEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Deployable kit for the two living-world ships. Each kit raises a fully
 * crewed vessel: the merchant cog sails with its trader crew, the pirate
 * raider hunts with a Freebooter crew already on deck.
 */
public final class ShipSpawnEggItem extends Item {
    private final EntityType<? extends SailingShipEntity> shipType;
    private final boolean pirate;

    public ShipSpawnEggItem(Settings settings, EntityType<? extends SailingShipEntity> shipType) {
        this(settings, shipType, false);
    }

    public ShipSpawnEggItem(Settings settings, EntityType<? extends SailingShipEntity> shipType, boolean pirate) {
        super(settings);
        this.shipType = shipType;
        this.pirate = pirate;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        BlockPos spawnPos = context.getBlockPos().offset(context.getSide());
        SailingShipEntity ship = shipType.create(world);
        if (ship == null) {
            return ActionResult.FAIL;
        }
        ship.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY() + 0.2,
                spawnPos.getZ() + 0.5, context.getPlayerYaw(), 0.0f);
        if (!world.spawnEntity(ship)) {
            return ActionResult.FAIL;
        }
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.SPLASH,
                    ship.getX(), ship.getY() + 0.5, ship.getZ(), 12, 1.2, 0.3, 1.2, 0.04);
            spawnCrew(serverWorld, ship);
        }
        PlayerEntity player = context.getPlayer();
        if (player != null) {
            player.sendMessage(Text.literal(pirate ? "A Freebooter raider is afloat. Board at your own risk."
                    : "The jewelry trader is afloat with her crew."), true);
        }
        if (player == null || !player.isCreative()) {
            context.getStack().decrement(1);
        }
        return ActionResult.SUCCESS;
    }

    private void spawnCrew(ServerWorld world, SailingShipEntity ship) {
        Archetype culture = pirate ? Archetype.PIRATE : Archetype.KNIGHT;
        String[] roles = pirate
                ? new String[]{"Captain", "Gunner", "Sailor"}
                : new String[]{"Merchant", "Jeweler", "Sailor"};
        List<SurvivorEntity> crew = new ArrayList<>();
        for (int i = 0; i < roles.length; i++) {
            SurvivorEntity member = ModEntities.SURVIVOR.create(world);
            if (member == null) {
                continue;
            }
            member.refreshPositionAndAngles(ship.getX(), ship.getY() + 1.0, ship.getZ(),
                    world.random.nextFloat() * 360.0f, 0.0f);
            member.setArchetype(culture);
            member.assignWorker(ship.getBlockPos(), null, culture.faction(),
                    com.rivalrealms.world.SettlementRole.byId(roles[i].toLowerCase(java.util.Locale.ROOT)));
            member.setCustomName(Text.literal(roles[i] + " · " + culture.title()));
            if (!world.spawnEntity(member)) {
                continue;
            }
            crew.add(member);
        }
        ship.boardCrew(crew);
        world.playSound(null, ship.getX(), ship.getY(), ship.getZ(), SoundEvents.ENTITY_BOAT_PADDLE_WATER,
                SoundCategory.NEUTRAL, 0.8f, 1.0f);
    }
}
