"""Build stub classes for the Yarn/Minecraft API surface the mod touches.

The stubs mirror exact member signatures from the Yarn 1.21.1 mappings, so a
`javac -proc:only` pass against them catches wrong symbol names, missing
constants and arity mistakes in mod code without needing the real game jar
(which this sandbox cannot download).

Run via scripts/apicheck.py.
"""
from pathlib import Path

STUBS = Path('/tmp/stubs/src')
CLASSES = {}


def add(fqcn, supers=(), members=(), iface=False):
    pkg, _, name = fqcn.rpartition('.')
    CLASSES.setdefault((pkg, name), {'supers': list(supers), 'members': list(members), 'iface': iface})


# ---- registry entries & pose (1.21.1 sound constants are split across these types)
add('net.minecraft.registry.entry.RegistryEntry', [], [
    'public static <T> net.minecraft.registry.entry.RegistryEntry<T> of(T value);',
], iface=True)
add('net.minecraft.registry.entry.RegistryEntry.Reference', ['net.minecraft.registry.entry.RegistryEntry'], [
    'public T value();',
])
add('net.minecraft.entity.EntityPose', [], [])

add('com.mojang.serialization.MapCodec', [], [])
add('com.mojang.serialization.Codec', [], [])
add('net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents', [], [
    'public static final net.fabricmc.fabric.api.event.Event AFTER;',
])
add('net.minecraft.enchantment.Enchantment', [], [])
add('net.minecraft.item.CrossbowItem', ['net.minecraft.item.RangedWeaponItem'], [
    'public static boolean isCharged(net.minecraft.item.ItemStack stack);',
])
add('net.minecraft.item.RangedWeaponItem', ['net.minecraft.item.Item'], [])
add('net.minecraft.entity.effect.StatusEffect', [], [])
add('net.minecraft.entity.effect.StatusEffects', [], [
    'public static final net.minecraft.registry.entry.RegistryEntry.Reference<net.minecraft.entity.effect.StatusEffect> SPEED;',
    'public static final net.minecraft.registry.entry.RegistryEntry.Reference<net.minecraft.entity.effect.StatusEffect> STRENGTH;',
    'public static final net.minecraft.registry.entry.RegistryEntry.Reference<net.minecraft.entity.effect.StatusEffect> WEAKNESS;',
    'public static final net.minecraft.registry.entry.RegistryEntry.Reference<net.minecraft.entity.effect.StatusEffect> RESISTANCE;',
])
add('net.minecraft.entity.effect.StatusEffectInstance', [], [
    'public StatusEffectInstance(net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect, int duration, int amplifier);',
])
add('net.minecraft.component.type.AttributeModifiersComponent', [], [
    'public static net.minecraft.component.type.AttributeModifiersComponent.Builder builder();',
])
add('net.minecraft.component.type.AttributeModifiersComponent.Builder', [], [
    'public net.minecraft.component.type.AttributeModifiersComponent.Builder add(net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute, net.minecraft.entity.attribute.EntityAttributeModifier modifier, net.minecraft.component.type.AttributeModifierSlot slot);',
    'public net.minecraft.component.type.AttributeModifiersComponent build();',
])
add('net.minecraft.component.type.AttributeModifierSlot', [], [])
add('net.minecraft.item.equipment.ArmorMaterials', [], [])
add('net.minecraft.registry.RegistryEntryLookup', [], [
    'public net.minecraft.registry.entry.RegistryEntry.Reference getOrThrow(net.minecraft.registry.RegistryKey key);',
], iface=True)
add('net.minecraft.registry.Registry', [], [
    'public net.minecraft.registry.entry.RegistryEntry.Reference entryOf(net.minecraft.registry.RegistryKey key);',
])
add('net.minecraft.registry.RegistryWrapper.Impl', ['net.minecraft.registry.Registry'], [
    'public net.minecraft.registry.Registry get(net.minecraft.registry.RegistryKey key);',
])
add('net.minecraft.registry.RegistryKeys', [], [
    'public static final net.minecraft.registry.RegistryKey ENCHANTMENT;',
])
add('net.minecraft.enchantment.Enchantments', [], [
    'public static final net.minecraft.registry.RegistryKey<net.minecraft.enchantment.Enchantment> PROTECTION;',
    'public static final net.minecraft.registry.RegistryKey<net.minecraft.enchantment.Enchantment> SHARPNESS;',
    'public static final net.minecraft.registry.RegistryKey<net.minecraft.enchantment.Enchantment> POWER;',
    'public static final net.minecraft.registry.RegistryKey<net.minecraft.enchantment.Enchantment> PIERCING;',
    'public static final net.minecraft.registry.RegistryKey<net.minecraft.enchantment.Enchantment> UNBREAKING;',
])

add('net.fabricmc.fabric.api.event.player.ServerBlockBreakEvents', [], [
    'public static final net.fabricmc.fabric.api.event.Event AFTER;',
])
add('net.fabricmc.fabric.api.event.Event', [], [], iface=True)
add('net.minecraft.village.TradedItem', [], [
    'public TradedItem(net.minecraft.item.ItemLike item);',
    'public TradedItem(net.minecraft.item.ItemLike item, int count);',
])
add('net.minecraft.village.TradeOffer', [], [
    'public TradeOffer(net.minecraft.village.TradedItem firstBuyItem, java.util.Optional<net.minecraft.village.TradedItem> secondBuyItem, net.minecraft.item.ItemStack sellItem, int maxUses, int merchantExperience, float priceMultiplier);',
])
add('net.minecraft.village.TradeOfferList', ['java.util.ArrayList'], [])
add('net.minecraft.village.Merchant', [], [], iface=True)
add('net.minecraft.village.SimpleMerchant', ['net.minecraft.village.Merchant'], [
    'public SimpleMerchant(net.minecraft.entity.player.PlayerEntity customer);',
    'public net.minecraft.village.TradeOfferList getOffers();',
    'public void setCustomer(net.minecraft.entity.player.PlayerEntity customer);',
    'public void sendOffers(net.minecraft.entity.player.PlayerEntity customer, net.minecraft.text.Text title, int levelProgress);',
])

# ---- net.minecraft.util
add('net.minecraft.util.Identifier', [], [
    'public static net.minecraft.util.Identifier of(String namespace, String path);',
    'public String getPath();', 'public String getNamespace();', 'public String toString();',
])
add('net.minecraft.util.math.BlockPos', ['net.minecraft.util.math.Vec3i'], [
    'public BlockPos(int x, int y, int z);',
    'public static BlockPos fromLong(long packed);',
    'public long asLong();',
    'public BlockPos add(int dx, int dy, int dz);',
    'public BlockPos up();', 'public BlockPos up(int n);',
    'public BlockPos down();', 'public BlockPos down(int n);',
    'public BlockPos north();', 'public BlockPos south();', 'public BlockPos west();', 'public BlockPos east();',
    'public BlockPos offset(net.minecraft.util.math.Direction d);',
    'public BlockPos offset(net.minecraft.util.math.Direction d, int n);',
    'public BlockPos toImmutable();',
    'public double getSquaredDistance(net.minecraft.util.math.Vec3i other);',
    'public net.minecraft.util.math.BlockPos mutate(java.util.function.UnaryOperator<net.minecraft.util.math.BlockPos> op);',
])
add('net.minecraft.util.math.Vec3i', [], [
    'public int getX();', 'public int getY();', 'public int getZ();',
])
add('net.minecraft.util.math.Vec3d', [], [
    'public Vec3d(double x, double y, double z);',
    'public net.minecraft.util.math.Vec3d add(net.minecraft.util.math.Vec3d o);',
    'public net.minecraft.util.math.Vec3d subtract(net.minecraft.util.math.Vec3d o);',
    'public net.minecraft.util.math.Vec3d multiply(double f);',
    'public net.minecraft.util.math.Vec3d normalize();',
    'public double lengthSquared();', 'public double length();',
    'public double distanceTo(net.minecraft.util.math.Vec3d o);',
    'public double squaredDistanceTo(net.minecraft.util.math.Vec3d o);',
    'public double dotProduct(net.minecraft.util.math.Vec3d o);',
    'public static net.minecraft.util.math.Vec3d fromPolar(float pitch, float yaw);',
])
add('net.minecraft.util.math.Box', [], [
    'public Box(double x0, double y0, double z0, double x1, double y1, double z1);',
    'public Box(net.minecraft.util.math.Vec3d min, net.minecraft.util.math.Vec3d max);',
    'public Box(net.minecraft.util.math.BlockPos pos);',
    'public net.minecraft.util.math.Box expand(double d);',
    'public net.minecraft.util.math.Box stretch(net.minecraft.util.math.Vec3d v);',
    'public net.minecraft.util.math.Vec3d getCenter();',
])
add('net.minecraft.util.math.MathHelper', [], [
    'public static float lerpAngleDegrees(float delta, float start, float end);',
    'public static float clamp(float v, float min, float max);',
    'public static int clamp(int v, int min, int max);',
    'public static double clamp(double v, double min, double max);',
    'public static int floor(double v);',
    'public static double atan2(double y, double x);',
    'public static float lerp(float delta, float start, float end);',
])
add('net.minecraft.util.math.Direction', [], [
    'public static final net.minecraft.util.math.Direction NORTH;',
    'public static final net.minecraft.util.math.Direction SOUTH;',
    'public static final net.minecraft.util.math.Direction EAST;',
    'public static final net.minecraft.util.math.Direction WEST;',
    'public net.minecraft.util.math.Direction getOpposite();',
    'public net.minecraft.util.math.Direction rotateYCounterclockwise();',
])
add('net.minecraft.util.math.RotationAxis', [], [
    'public static final net.minecraft.util.math.RotationAxis POSITIVE_Y;',
    'public static final net.minecraft.util.math.RotationAxis POSITIVE_X;',
    'public static final net.minecraft.util.math.RotationAxis POSITIVE_Z;',
    'public org.joml.Quaternionf rotationDegrees(float degrees);',
])
add('net.minecraft.util.math.random.Random', [], [
    'public int nextInt(int bound);', 'public float nextFloat();', 'public double nextDouble();',
])
add('net.minecraft.util.Formatting', [], [
    'public static final net.minecraft.util.Formatting RED;', 'public static final net.minecraft.util.Formatting GOLD;', 'public static final net.minecraft.util.Formatting GREEN;', 'public static final net.minecraft.util.Formatting GRAY;', 'public static final net.minecraft.util.Formatting WHITE;', 'public static final net.minecraft.util.Formatting AQUA;', 'public static final net.minecraft.util.Formatting DARK_RED;', 'public static final net.minecraft.util.Formatting YELLOW;',
    'public static final net.minecraft.util.Formatting LIGHT_PURPLE;',
    'public static final net.minecraft.util.Formatting DARK_GRAY;',
])
add('net.minecraft.util.Hand', [], [
    'public static final net.minecraft.util.Hand MAIN_HAND;',
    'public static final net.minecraft.util.Hand OFF_HAND;',
])
add('net.minecraft.util.ActionResult', [], [
    'public static final net.minecraft.util.ActionResult SUCCESS;',
    'public static final net.minecraft.util.ActionResult CONSUME;',
    'public static final net.minecraft.util.ActionResult PASS;',
    'public static final net.minecraft.util.ActionResult FAIL;',
])
add('net.minecraft.util.TypedActionResult', [], [
    'public static <T> net.minecraft.util.TypedActionResult<T> success(T result, boolean swing);',
    'public static <T> net.minecraft.util.TypedActionResult<T> fail(T result);',
])
add('net.minecraft.util.hit.HitResult', [], [
    'public net.minecraft.util.math.Vec3d getPos();',
])
add('net.minecraft.util.hit.EntityHitResult', ['net.minecraft.util.hit.HitResult'], [
    'public net.minecraft.entity.Entity getEntity();',
])
add('net.minecraft.util.hit.BlockHitResult', ['net.minecraft.util.hit.HitResult'], [
    'public net.minecraft.util.math.BlockPos getBlockPos();',
    'public net.minecraft.util.math.Direction getSide();',
])

# ---- text & sound
add('net.minecraft.text.Text', [], [
    'public static net.minecraft.text.Text literal(String s);',
    'public static net.minecraft.text.Text translatable(String key, java.lang.Object... args);',
    'public net.minecraft.text.MutableText formatted(net.minecraft.util.Formatting f);',
    'public String getString();',
], iface=True)
add('net.minecraft.sound.SoundCategory', [], [
    'public static final net.minecraft.sound.SoundCategory BLOCKS;',
    'public static final net.minecraft.sound.SoundCategory NEUTRAL;',
    'public static final net.minecraft.sound.SoundCategory PLAYERS;',
    'public static final net.minecraft.sound.SoundCategory HOSTILE;',
    'public static final net.minecraft.sound.SoundCategory RECORDS;',
])
add('net.minecraft.sound.SoundEvent', [], [])
add('net.minecraft.sound.SoundEvents', [], [
    # RegistryEntry.Reference constants (registerReference in 1.21.1)
    'public static final net.minecraft.registry.entry.RegistryEntry.Reference<net.minecraft.sound.SoundEvent> ENTITY_GENERIC_EXPLODE;',
    'public static final net.minecraft.registry.entry.RegistryEntry.Reference<net.minecraft.sound.SoundEvent> ENTITY_GENERIC_EAT;',
    # plain SoundEvent constants
    'public static final net.minecraft.sound.SoundEvent ENTITY_WITHER_SHOOT;',
    'public static final net.minecraft.sound.SoundEvent ENTITY_BOAT_PADDLE_WATER;',
    'public static final net.minecraft.sound.SoundEvent BLOCK_ANVIL_LAND;',
    'public static final net.minecraft.sound.SoundEvent BLOCK_CHAIN_HIT;',
    'public static final net.minecraft.sound.SoundEvent BLOCK_WOOD_BREAK;',
    'public static final net.minecraft.sound.SoundEvent ENTITY_FIREWORK_ROCKET_LAUNCH;',
    'public static final net.minecraft.sound.SoundEvent ENTITY_VILLAGER_YES;',
    'public static final net.minecraft.sound.SoundEvent ENTITY_VILLAGER_CELEBRATE;',
    'public static final net.minecraft.sound.SoundEvent ENTITY_ITEM_PICKUP;',
    'public static final net.minecraft.sound.SoundEvent ENTITY_PLAYER_ATTACK_SWEEP;',
    'public static final net.minecraft.sound.SoundEvent ENTITY_ARROW_HIT_PLAYER;',
    'public static final net.minecraft.sound.SoundEvent ITEM_CROSSBOW_SHOOT;',
    'public static final net.minecraft.sound.SoundEvent BLOCK_PISTON_EXTEND;', 'public static final net.minecraft.sound.SoundEvent ITEM_HOE_TILL;', 'public static final net.minecraft.sound.SoundEvent BLOCK_ANVIL_USE;', 'public static final net.minecraft.sound.SoundEvent ENTITY_RAVAGER_ROAR;', 'public static final net.minecraft.sound.SoundEvent ENTITY_RAVAGER_ATTACK;', 'public static final net.minecraft.sound.SoundEvent BLOCK_METAL_PLACE;',
    'public static final net.minecraft.sound.SoundEvent ENTITY_GENERIC_SPLASH;',
    'public static final net.minecraft.sound.SoundEvent BLOCK_FIRE_EXTINGUISH;',
    'public static final net.minecraft.sound.SoundEvent ENTITY_VILLAGER_NO;',
    'public static final net.minecraft.sound.SoundEvent ENTITY_WITHER_SPAWN;',
    'public static final net.minecraft.sound.SoundEvent ITEM_FLINTANDSTEEL_USE;',
    'public static final net.minecraft.sound.SoundEvent BLOCK_BELL_USE;',
    'public static final net.minecraft.sound.SoundEvent BLOCK_WOOD_PLACE;',
    'public static final net.minecraft.registry.entry.RegistryEntry.Reference<net.minecraft.sound.SoundEvent> BLOCK_NOTE_BLOCK_HARP;',
    'public static final net.minecraft.registry.entry.RegistryEntry.Reference<net.minecraft.sound.SoundEvent> BLOCK_NOTE_BLOCK_BASS;',
    'public static final net.minecraft.registry.entry.RegistryEntry.Reference<net.minecraft.sound.SoundEvent> BLOCK_NOTE_BLOCK_FLUTE;',
    'public static final net.minecraft.registry.entry.RegistryEntry.Reference<net.minecraft.sound.SoundEvent> BLOCK_NOTE_BLOCK_BELL;',
    'public static final net.minecraft.registry.entry.RegistryEntry.Reference<net.minecraft.sound.SoundEvent> BLOCK_NOTE_BLOCK_BASEDRUM;',
])

# ---- registry
add('net.minecraft.registry.Registries', [], [
    'public static final net.minecraft.registry.Registry<net.minecraft.block.Block> BLOCK;',
    'public static final net.minecraft.registry.Registry<net.minecraft.item.Item> ITEM;',
    'public static final net.minecraft.registry.Registry<net.minecraft.entity.EntityType<?>> ENTITY_TYPE;',
    'public static final net.minecraft.registry.Registry<net.minecraft.item.ItemGroup> ITEM_GROUP;',
    'public static final net.minecraft.registry.Registry<net.minecraft.sound.SoundEvent> SOUND_EVENT;',
])
add('net.minecraft.registry.Registry', [], [
    'public <V extends T> V register(net.minecraft.util.Identifier id, V value);',
])
add('net.minecraft.registry.RegistryKey', [], [
    'public static <T> net.minecraft.registry.RegistryKey<T> of(net.minecraft.registry.RegistryKey<? extends net.minecraft.registry.Registry<T>> registry, net.minecraft.util.Identifier value);',
])
add('net.minecraft.registry.RegistryKeys', [], [
    'public static final net.minecraft.registry.RegistryKey<net.minecraft.registry.Registry<net.minecraft.item.ItemGroup>> ITEM_GROUP;',
])
add('net.minecraft.registry.RegistryWrapper', [], [], iface=True)
add('net.minecraft.registry.RegistryWrapper.WrapperLookup', [], [], iface=True)

# ---- blocks
add('net.minecraft.block.AbstractBlock', [], [])
add('net.minecraft.block.AbstractBlock.Settings', [], [
    'public static net.minecraft.block.AbstractBlock.Settings create();',
    'public static net.minecraft.block.AbstractBlock.Settings copy(net.minecraft.block.Block block);',
    'public net.minecraft.block.AbstractBlock.Settings mapColor(net.minecraft.block.MapColor color);',
    'public net.minecraft.block.AbstractBlock.Settings instrument(net.minecraft.block.enums.NoteBlockInstrument instrument);',
    'public net.minecraft.block.AbstractBlock.Settings strength(float hardness, float resistance);',
    'public net.minecraft.block.AbstractBlock.Settings strength(float hardness);',
    'public net.minecraft.block.AbstractBlock.Settings requiresTool();',
    'public net.minecraft.block.AbstractBlock.Settings sounds(net.minecraft.sound.BlockSoundGroup group);',
    'public net.minecraft.block.AbstractBlock.Settings nonOpaque();',
    'public net.minecraft.block.AbstractBlock.Settings noCollision();',
])
add('net.minecraft.block.Block', ['net.minecraft.block.AbstractBlock'], [
    'public Block(net.minecraft.block.AbstractBlock.Settings settings);',
    'public net.minecraft.block.BlockState getDefaultState();',
    'public static net.minecraft.util.shape.VoxelShape createCuboidShape(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);',
    'protected void setDefaultState(net.minecraft.block.BlockState state);',
    'protected void appendProperties(net.minecraft.state.StateManager.Builder builder);',
    'public net.minecraft.block.BlockState getPlacementState(net.minecraft.item.ItemPlacementContext context);',
    'protected net.minecraft.util.shape.VoxelShape getOutlineShape(net.minecraft.block.BlockState state, net.minecraft.world.BlockView world, net.minecraft.util.math.BlockPos pos, net.minecraft.block.ShapeContext context);',
    'protected net.minecraft.util.ActionResult onUse(net.minecraft.block.BlockState state, net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos, net.minecraft.entity.player.PlayerEntity player, net.minecraft.util.hit.BlockHitResult hit);',
])
add('net.minecraft.block.Blocks', [], [
    'public static final net.minecraft.block.Block AIR;', 'public static final net.minecraft.block.Block WATER;',
    'public static final net.minecraft.block.Block STONE;', 'public static final net.minecraft.block.Block COBBLESTONE;',
    'public static final net.minecraft.block.Block STONE_BRICKS;', 'public static final net.minecraft.block.Block BRICKS;', 'public static final net.minecraft.block.Block BELL;', 'public static final net.minecraft.block.Block OAK_LEAVES;', 'public static final net.minecraft.block.Block DARK_OAK_SLAB;', 'public static final net.minecraft.block.Block WHITE_STAINED_GLASS_PANE;', 'public static final net.minecraft.block.Block COAL_ORE;', 'public static final net.minecraft.block.Block IRON_ORE;', 'public static final net.minecraft.block.Block TORCH;', 'public static final net.minecraft.block.Block ORANGE_TERRACOTTA;', 'public static final net.minecraft.block.Block SAND;', 'public static final net.minecraft.block.Block OAK_SLAB;', 'public static final net.minecraft.block.Block DIRT_PATH;', 'public static final net.minecraft.block.Block RED_CARPET;', 'public static final net.minecraft.block.Block GLOWSTONE;', 'public static final net.minecraft.block.Block COAL_BLOCK;', 'public static final net.minecraft.block.Block IRON_BARS;', 'public static final net.minecraft.block.Block STONE_BRICK_STAIRS;',
    'public static final net.minecraft.block.Block POLISHED_ANDESITE;', 'public static final net.minecraft.block.Block DEEPSLATE_TILES;',
    'public static final net.minecraft.block.Block DARK_OAK_PLANKS;', 'public static final net.minecraft.block.Block SPRUCE_PLANKS;',
    'public static final net.minecraft.block.Block OAK_PLANKS;', 'public static final net.minecraft.block.Block ACACIA_PLANKS;',
    'public static final net.minecraft.block.Block CUT_COPPER;',
    'public static final net.minecraft.block.Block WHITE_WOOL;', 'public static final net.minecraft.block.Block RED_WOOL;',
    'public static final net.minecraft.block.Block BLUE_WOOL;', 'public static final net.minecraft.block.Block YELLOW_WOOL;',
    'public static final net.minecraft.block.Block ORANGE_WOOL;', 'public static final net.minecraft.block.Block LIME_WOOL;',
    'public static final net.minecraft.block.Block BLACK_WOOL;', 'public static final net.minecraft.block.Block GRAY_WOOL;',
    'public static final net.minecraft.block.Block SPRUCE_LOG;', 'public static final net.minecraft.block.Block OAK_LOG;',
    'public static final net.minecraft.block.Block DARK_OAK_LOG;',
    'public static final net.minecraft.block.Block SPRUCE_STAIRS;', 'public static final net.minecraft.block.Block OAK_STAIRS;',
    'public static final net.minecraft.block.Block ACACIA_STAIRS;', 'public static final net.minecraft.block.Block DARK_OAK_STAIRS;',
    'public static final net.minecraft.block.Block SPRUCE_SLAB;',
    'public static final net.minecraft.block.Block GLASS_PANE;',
    'public static final net.minecraft.block.Block OAK_FENCE;', 'public static final net.minecraft.block.Block OAK_FENCE_GATE;',
    'public static final net.minecraft.block.Block CHEST;', 'public static final net.minecraft.block.Block BARREL;',
    'public static final net.minecraft.block.Block LANTERN;', 'public static final net.minecraft.block.Block CHAIN;',
    'public static final net.minecraft.block.Block CAMPFIRE;', 'public static final net.minecraft.block.Block FARMLAND;', 'public static final net.minecraft.block.Block GOLD_BLOCK;',
        'public class RenderLayer extends RenderPhase { public static RenderLayer getCutout(); public static RenderLayer getTranslucent(); }',
    'public static final net.minecraft.block.Block WHEAT;', 'public static final net.minecraft.block.Block COARSE_DIRT;', 'public static final net.minecraft.block.Block SOUL_LANTERN;', 'public static final net.minecraft.block.Block COBBLESTONE_SLAB;', 'public static final net.minecraft.block.Block STONE_BRICK_SLAB;', 'public static final net.minecraft.block.Block DEAD_BUSH;', 'public static final net.minecraft.block.Block MOSSY_COBBLESTONE;', 'public static final net.minecraft.block.Block PODZOL;', 'public static final net.minecraft.block.Block COBWEB;', 'public static final net.minecraft.block.Block GRASS_BLOCK;', 'public static final net.minecraft.block.Block CARVED_PUMPKIN;', 'public static final net.minecraft.block.Block WATER;', 'public static final net.minecraft.block.Block CARROTS;', 'public static final net.minecraft.block.Block POTATOES;', 'public static final net.minecraft.block.Block BEETROOTS;', 'public static final net.minecraft.block.Block BARREL;', 'public static final net.minecraft.block.Block LANTERN;',
    'public static final net.minecraft.block.Block MOSS_BLOCK;', 'public static final net.minecraft.block.Block MOSSY_STONE_BRICKS;',
    'public static final net.minecraft.block.Block MOSS_CARPET;', 'public static final net.minecraft.block.Block CRYING_OBSIDIAN;',
    'public static final net.minecraft.block.Block MAGMA_BLOCK;', 'public static final net.minecraft.block.Block RED_MUSHROOM;',
    'public static final net.minecraft.block.Block BROWN_MUSHROOM;', 'public static final net.minecraft.block.Block AZALEA;',
    'public static final net.minecraft.block.Block GLOW_LICHEN;', 'public static final net.minecraft.block.Block STRIPPED_OAK_LOG;',
    'public static final net.minecraft.block.Block RED_BED;',
    'public static final net.minecraft.block.Block GRAVEL;', 'public static final net.minecraft.block.Block RED_SANDSTONE;',
    'public static final net.minecraft.block.Block WHITE_CARPET;', 'public static final net.minecraft.block.Block CRAFTING_TABLE;',
    'public static final net.minecraft.block.Block FURNACE;', 'public static final net.minecraft.block.Block ANVIL;',
    'public static final net.minecraft.block.Block CAULDRON;', 'public static final net.minecraft.block.Block GRINDSTONE;',
    'public static final net.minecraft.block.Block COMPOSTER;', 'public static final net.minecraft.block.Block HAY_BLOCK;',
    'public static final net.minecraft.block.Block CANDLE;', 'public static final net.minecraft.block.Block BOOKSHELF;',
    'public static final net.minecraft.block.Block SCAFFOLDING;', 'public static final net.minecraft.block.Block COBBLESTONE_WALL;',
    'public static final net.minecraft.block.Block DIRT;',
    'public static final net.minecraft.block.Block LADDER;',
    'public static final net.minecraft.block.Block OAK_WOOD;',
])
add('net.minecraft.block.BlockState', ['net.minecraft.state.State'], [
    'public boolean isReplaceable();',
    'public boolean isAir();',
    'public net.minecraft.block.Block getBlock();',
])
add('net.minecraft.block.MapColor', [], [
    'public static final net.minecraft.block.MapColor DEEPSLATE_GRAY;',
    'public static final net.minecraft.block.MapColor STONE_GRAY;',
    'public static final net.minecraft.block.MapColor BROWN;',
    'public static final net.minecraft.block.MapColor SPRUCE_BROWN;',
    'public static final net.minecraft.block.MapColor ORANGE;',
    'public static final net.minecraft.block.MapColor LIGHT_BLUE;',
    'public static final net.minecraft.block.MapColor PURPLE;',
])
add('net.minecraft.block.enums.NoteBlockInstrument', [], [])
add('net.minecraft.sound.BlockSoundGroup', [], [])
add('net.minecraft.block.ShapeContext', [], [])
add('net.minecraft.item.ItemPlacementContext', [], [
    'public net.minecraft.util.math.Direction getHorizontalPlayerFacing();',
    'public net.minecraft.util.Hand getHand();',
])
add('net.minecraft.state.StateManager', [], [
    'public net.minecraft.state.StateManager.Builder appendProperties(net.minecraft.state.property.Property[] properties);',
])
add('net.minecraft.state.StateManager.Builder', [], [
    'public net.minecraft.state.StateManager.Builder add(net.minecraft.state.property.Property[] properties);',
])
add('net.minecraft.state.property.BooleanProperty', ['net.minecraft.state.property.Property'], [
    'public static net.minecraft.state.property.BooleanProperty of(String name);',
])
add('net.minecraft.block.CropBlock', [], [
    'public static final net.minecraft.state.property.IntProperty AGE;',
])
add('net.minecraft.block.FenceGateBlock', ['net.minecraft.block.HorizontalFacingBlock'], [])
add('net.minecraft.block.StairsBlock', ['net.minecraft.block.Block'], [])
add('net.minecraft.block.HorizontalFacingBlock', ['net.minecraft.block.Block'], [
    'public static final net.minecraft.state.property.DirectionProperty FACING;',
])
add('net.minecraft.state.State', [], [
    'public <T extends Comparable<T>> net.minecraft.state.State with(net.minecraft.state.property.Property<T> prop, T value);',
])
add('net.minecraft.state.property.Property', [], [])
add('net.minecraft.state.property.IntProperty', ['net.minecraft.state.property.Property'], [])
add('net.minecraft.state.property.DirectionProperty', ['net.minecraft.state.property.EnumProperty'], [])
add('net.minecraft.state.property.EnumProperty', ['net.minecraft.state.property.Property'], [])
add('net.minecraft.block.entity.BlockEntity', [], [])
add('net.minecraft.inventory.Inventory', [], [
    'public int size();',
    'public void setStack(int slot, net.minecraft.item.ItemStack stack);',
], iface=True)

# ---- nbt
add('net.minecraft.nbt.NbtCompound', [], [
    'public NbtCompound();',
    'public void putUuid(String key, java.util.UUID value);',
    'public java.util.UUID getUuid(String key);',
    'public boolean containsUuid(String key);',
    'public void putLong(String key, long value);', 'public long getLong(String key);',
    'public void putInt(String key, int value);', 'public int getInt(String key);',
    'public void putFloat(String key, float value);', 'public float getFloat(String key);',
    'public void putBoolean(String key, boolean value);', 'public boolean getBoolean(String key);',
    'public void putString(String key, String value);', 'public String getString(String key);',
    'public void putLongArray(String key, long[] value);', 'public long[] getLongArray(String key);',
    'public void put(String key, net.minecraft.nbt.NbtElement value);',
    'public boolean contains(String key);', 'public boolean contains(String key, int type);',
    'public net.minecraft.nbt.NbtList getList(String key, int type);',
    'public java.util.Set<String> getKeys();',
    'public byte getByte(String key, byte fallback);',
])
add('net.minecraft.nbt.NbtList', [], [
    'public int size();',
    'public net.minecraft.nbt.NbtCompound getCompound(int index);',
    'public void add(net.minecraft.nbt.NbtElement element);',
])
add('net.minecraft.component.ComponentType', [], [])
add('net.minecraft.component.type.NbtComponent', [], [
    'public static net.minecraft.component.type.NbtComponent of(net.minecraft.nbt.NbtCompound nbt);',
    'public net.minecraft.nbt.NbtCompound copyNbt();',
])
add('net.minecraft.component.DataComponentTypes', [], [
    'public static final net.minecraft.component.ComponentType<net.minecraft.component.type.NbtComponent> CUSTOM_DATA;',
])
add('net.minecraft.nbt.NbtElement', [], [
    'public static final int STRING_TYPE;',
    'public static final int COMPOUND_TYPE;',
])

# ---- items
add('net.minecraft.item.Item', [], [
    'public Item(net.minecraft.item.Item.Settings settings);',
])
add('net.minecraft.item.Item.Settings', [], [
    'public net.minecraft.item.Item.Settings maxCount(int max);',
    'public net.minecraft.item.Item.Settings maxDamage(int max);',
])
add('net.minecraft.item.ItemStack', [], [
    'public ItemStack(net.minecraft.item.Item item);',
    'public ItemStack(net.minecraft.item.Item item, int count);',
    'public boolean isEmpty();',
    'public void decrement(int n);',
    'public net.minecraft.item.Item getItem();',
    'public boolean isOf(net.minecraft.item.Item item);',
    'public void damage(int amount, net.minecraft.entity.LivingEntity entity, net.minecraft.entity.EquipmentSlot slot);',
    'public <T> T get(net.minecraft.component.ComponentType<T> type);',
    'public <T> T set(net.minecraft.component.ComponentType<T> type, T value);',
    'public net.minecraft.item.ItemStack copy();',
])
add('net.minecraft.item.Items', [], [
    'public static final net.minecraft.item.Item AIR;',
    'public static final net.minecraft.item.Item IRON_SWORD;', 'public static final net.minecraft.item.Item WOODEN_SWORD;', 'public static final net.minecraft.item.Item STONE_SWORD;', 'public static final net.minecraft.item.Item GOLDEN_SWORD;', 'public static final net.minecraft.item.Item DIAMOND_SWORD;', 'public static final net.minecraft.item.Item SHIELD;', 'public static final net.minecraft.item.Item LEATHER_HELMET;', 'public static final net.minecraft.item.Item LEATHER_CHESTPLATE;', 'public static final net.minecraft.item.Item LEATHER_LEGGINGS;', 'public static final net.minecraft.item.Item LEATHER_BOOTS;', 'public static final net.minecraft.item.Item CHAINMAIL_HELMET;', 'public static final net.minecraft.item.Item CHAINMAIL_CHESTPLATE;', 'public static final net.minecraft.item.Item CHAINMAIL_LEGGINGS;', 'public static final net.minecraft.item.Item CHAINMAIL_BOOTS;', 'public static final net.minecraft.item.Item IRON_HELMET;', 'public static final net.minecraft.item.Item IRON_CHESTPLATE;', 'public static final net.minecraft.item.Item IRON_LEGGINGS;', 'public static final net.minecraft.item.Item IRON_BOOTS;', 'public static final net.minecraft.item.Item DIAMOND_HELMET;', 'public static final net.minecraft.item.Item DIAMOND_CHESTPLATE;', 'public static final net.minecraft.item.Item DIAMOND_LEGGINGS;', 'public static final net.minecraft.item.Item DIAMOND_BOOTS;', 'public static final net.minecraft.item.Item GOLDEN_HELMET;', 'public static final net.minecraft.item.Item GOLDEN_BOOTS;', 'public static final net.minecraft.item.Item TORCH;', 'public static final net.minecraft.item.Item COMPASS;', 'public static final net.minecraft.item.Item EXPERIENCE_BOTTLE;', 'public static final net.minecraft.item.Item APPLE;',
    'public static final net.minecraft.item.Item IRON_HELMET;', 'public static final net.minecraft.item.Item IRON_CHESTPLATE;',
    'public static final net.minecraft.item.Item IRON_LEGGINGS;', 'public static final net.minecraft.item.Item IRON_BOOTS;',
    'public static final net.minecraft.item.Item LEATHER_HELMET;', 'public static final net.minecraft.item.Item LEATHER_CHESTPLATE;',
    'public static final net.minecraft.item.Item LEATHER_LEGGINGS;', 'public static final net.minecraft.item.Item LEATHER_BOOTS;',
    'public static final net.minecraft.item.Item GOLDEN_HELMET;', 'public static final net.minecraft.item.Item GOLDEN_BOOTS;',
    'public static final net.minecraft.item.Item CROSSBOW;', 'public static final net.minecraft.item.Item IRON_AXE;',
    'public static final net.minecraft.item.Item ARROW;',
    'public static final net.minecraft.item.Item GOLDEN_CARROT;', 'public static final net.minecraft.item.Item COOKED_BEEF;',
    'public static final net.minecraft.item.Item BREAD;', 'public static final net.minecraft.item.Item COOKED_SALMON;',
    'public static final net.minecraft.item.Item APPLE;',
    'public static final net.minecraft.item.Item IRON_INGOT;', 'public static final net.minecraft.item.Item COPPER_INGOT;',
    'public static final net.minecraft.item.Item GOLD_INGOT;', 'public static final net.minecraft.item.Item GOLD_NUGGET;',
    'public static final net.minecraft.item.Item IRON_NUGGET;', 'public static final net.minecraft.item.Item EMERALD;',
    'public static final net.minecraft.item.Item DIAMOND;', 'public static final net.minecraft.item.Item COAL;', 'public static final net.minecraft.item.Item WHEAT;',
    'public static final net.minecraft.item.Item WHEAT_SEEDS;', 'public static final net.minecraft.item.Item CARROT;',
    'public static final net.minecraft.item.Item POTATO;', 'public static final net.minecraft.item.Item BEETROOT_SEEDS;', 
    'public static final net.minecraft.item.Item COAL_BLOCK;', 'public static final net.minecraft.item.Item STICK;',
    'public static final net.minecraft.item.Item PAPER;', 'public static final net.minecraft.item.Item LEATHER;',
    'public static final net.minecraft.item.Item GLASS;', 'public static final net.minecraft.item.Item FLINT;',
    'public static final net.minecraft.item.Item REDSTONE;', 'public static final net.minecraft.item.Item GOLD_BLOCK;',
    'public static final net.minecraft.item.Item COPPER_BLOCK;',
    'public static final net.minecraft.item.Item FISHING_ROD;', 'public static final net.minecraft.item.Item AMETHYST_SHARD;',
    'public static final net.minecraft.item.Item OAK_PLANKS;', 'public static final net.minecraft.item.Item DARK_OAK_PLANKS;',
    'public static final net.minecraft.item.Item SPRUCE_PLANKS;',
    'public static final net.minecraft.item.Item COOKED_COD;', 'public static final net.minecraft.item.Item ROTTEN_FLESH;',
    'public static final net.minecraft.item.Item BONE;',
    'public static final net.minecraft.item.Item NETHERITE_CHESTPLATE;',
    'public static final net.minecraft.item.Item GOLDEN_CHESTPLATE;',
    'public static final net.minecraft.item.Item SPYGLASS;',
    'public static final net.minecraft.item.Item STRING;',
])
add('net.minecraft.item.BlockItem', ['net.minecraft.item.Item'], [
    'public BlockItem(net.minecraft.block.Block block, net.minecraft.item.Item.Settings settings);',
])
add('net.minecraft.item.SwordItem', ['net.minecraft.item.Item'], [
    'public SwordItem(net.minecraft.item.ToolMaterial material, net.minecraft.item.Item.Settings settings);',
])
add('net.minecraft.item.ToolMaterial', [], [], iface=True)
add('net.minecraft.item.HoeItem', ['net.minecraft.item.MiningToolItem'], [
    'public HoeItem(net.minecraft.item.ToolMaterial material, net.minecraft.item.Item.Settings settings);',
    'public net.minecraft.util.ActionResult useOnBlock(net.minecraft.item.ItemUsageContext context);',
])
add('net.minecraft.item.MiningToolItem', ['net.minecraft.item.ToolItem'], [])
add('net.minecraft.item.ToolItem', ['net.minecraft.item.Item'], [])
add('net.minecraft.item.ToolMaterials', ['net.minecraft.item.ToolMaterial'], [
    'public static final net.minecraft.item.ToolMaterials DIAMOND;',
    'public static final net.minecraft.item.ToolMaterials IRON;',
])
add('net.minecraft.item.ItemUsageContext', [], [
    'public net.minecraft.util.math.BlockPos getBlockPos();',
    'public net.minecraft.util.math.Direction getSide();',
    'public float getPlayerYaw();',
    'public net.minecraft.entity.player.PlayerEntity getPlayer();',
    'public net.minecraft.item.ItemStack getStack();',
    'public net.minecraft.world.World getWorld();',
])
add('net.minecraft.stat.Stats', [], [
    'public static net.minecraft.stat.Stat<net.minecraft.item.Item> USED;',
])
add('net.minecraft.stat.Stat', [], [])

# ---- entities
add('net.minecraft.entity.Entity', [], [
    'public void tick();',
    'public boolean damage(net.minecraft.entity.damage.DamageSource source, float amount);',
    'public boolean handleFallDamage(float fallDistance, float damageMultiplier, net.minecraft.entity.damage.DamageSource source);',
    'public boolean isPushable();',
    'public boolean canHit();',
    'public net.minecraft.util.ActionResult interact(net.minecraft.entity.player.PlayerEntity player, net.minecraft.util.Hand hand);',
    'public net.minecraft.entity.LivingEntity getControllingPassenger();',
    'public boolean isLogicalSideForUpdatingMovement();',
    'public net.minecraft.entity.Entity getFirstPassenger();',
    'public void move(net.minecraft.entity.MovementType type, net.minecraft.util.math.Vec3d velocity);',
    'public void setPosition(double x, double y, double z);',
    'public float getEyeHeight();',
    'public boolean startRiding(net.minecraft.entity.Entity vehicle);',
    'public boolean hasPassenger(net.minecraft.entity.Entity passenger);',
    'public net.minecraft.entity.Entity getVehicle();',
    'public net.minecraft.world.World getWorld();',
    'public double getX();', 'public double getY();', 'public double getZ();',
    'public net.minecraft.util.math.Vec3d getPos();',
    'public net.minecraft.util.math.Vec3d getVelocity();',
    'public void setVelocity(net.minecraft.util.math.Vec3d v);',
    'public net.minecraft.util.math.BlockPos getBlockPos();',
    'public float getYaw();', 'public void setYaw(float yaw);',
    'public float getPitch();', 'public void setPitch(float pitch);',
    'public boolean isAlive();', 'public void discard();',
    'public boolean isTouchingWater();',
    'public void refreshPositionAndAngles(double x, double y, double z, float yaw, float pitch);',
    'public boolean hasVehicle();',
    'public java.util.List<net.minecraft.entity.Entity> getPassengerList();',
    'public boolean startRiding(net.minecraft.entity.Entity vehicle, boolean force);',
    'public void stopRiding();',
    'public void setCustomName(net.minecraft.text.Text name);',
    'public boolean hasCustomName();',
    'public net.minecraft.text.Text getName();',
    'public void setCustomNameVisible(boolean visible);',
    'public void setNoGravity(boolean noGravity);',
    'public boolean isSpectator();',
    'public int age;',
    'public java.util.UUID getUuid();',
    'public net.minecraft.util.math.Box getBoundingBox();',
    'public void takeKnockback(double strength, double x, double z);',
    'public net.minecraft.entity.damage.DamageSources getDamageSources();',
    'public net.minecraft.item.ItemStack getPickBlockStack();',
    'public double getEyeY();',
    'public boolean isOnGround();',
    'public net.minecraft.registry.RegistryWrapper.Impl getRegistryManager();',
    'public boolean isDay();',
    'public boolean breakBlock(net.minecraft.util.math.BlockPos pos, boolean drop, net.minecraft.entity.Entity breakingEntity);',
    'public void swingHand(net.minecraft.util.Hand hand);',
    'public net.minecraft.util.math.Vec3d getPos();',
    'public float getEyeHeight(net.minecraft.entity.EntityPose pose);',
    'protected void updatePassengerPosition(net.minecraft.entity.Entity passenger, net.minecraft.entity.Entity.PositionUpdater positionUpdater);',
    'protected boolean canAddPassenger(net.minecraft.entity.Entity passenger);',
    'protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder);',
    'protected void readCustomDataFromNbt(net.minecraft.nbt.NbtCompound nbt);',
    'protected void writeCustomDataToNbt(net.minecraft.nbt.NbtCompound nbt);',
    'protected void fall(double heightDifference, boolean onGround, net.minecraft.block.BlockState state, net.minecraft.util.math.BlockPos landedPosition);',
    'protected void tickLerp();',
    'public void dropStack(net.minecraft.item.ItemStack stack);',
])
add('net.minecraft.entity.Entity.PositionUpdater', [], [], iface=True)
add('net.minecraft.entity.EntityType', [], [
    'public T create(net.minecraft.world.World world);',
    'public static final net.minecraft.entity.EntityType<net.minecraft.entity.vehicle.BoatEntity> BOAT;',
    'public static final net.minecraft.entity.EntityType<net.minecraft.entity.passive.DonkeyEntity> DONKEY;',
])
add('net.minecraft.entity.EntityType.Builder', [], [
    'public static <T extends net.minecraft.entity.Entity> net.minecraft.entity.EntityType.Builder<T> create(net.minecraft.entity.EntityType.EntityFactory<T> factory, net.minecraft.entity.SpawnGroup spawnGroup);',
    'public net.minecraft.entity.EntityType.Builder<T> dimensions(float width, float height);',
    'public net.minecraft.entity.EntityType.Builder<T> maxTrackingRange(int range);',
    'public net.minecraft.entity.EntityType.Builder<T> trackingTickInterval(int interval);',
    'public net.minecraft.entity.EntityType<T> build(String id);',
])
add('net.minecraft.entity.EntityType.EntityFactory', [], [
    'T create(net.minecraft.entity.EntityType<T> type, net.minecraft.world.World world);',
], iface=True)
add('net.minecraft.entity.SpawnGroup', [], [])
add('net.minecraft.entity.LivingEntity', ['net.minecraft.entity.Entity'], [
    'public net.minecraft.entity.attribute.EntityAttributeInstance getAttributeInstance(net.minecraft.entity.attribute.EntityAttribute attribute);',
    'public net.minecraft.entity.LivingEntity getAttacker();',
    'public net.minecraft.entity.LivingEntity getTarget();',
    'public void setTarget(net.minecraft.entity.LivingEntity target);',
    'public float getHealth();', 'public void setHealth(float health);',
    'public float getMaxHealth();', 'public void heal(float amount);', 'public boolean canSee(net.minecraft.entity.Entity entity);',
    'protected void dropEquipment(net.minecraft.server.world.ServerWorld world, net.minecraft.entity.damage.DamageSource source, boolean equipped);',
    'public void swingHand(net.minecraft.util.Hand hand, boolean fromServerPlayer);',
    'public net.minecraft.item.ItemStack getEquippedStack(net.minecraft.entity.EquipmentSlot slot);',
    'public void equipStack(net.minecraft.entity.EquipmentSlot slot, net.minecraft.item.ItemStack stack);',
    'public void setEquipmentDropChance(net.minecraft.entity.EquipmentSlot slot, float chance);',
    'public float getStandingEyeHeight();',
    'public net.minecraft.util.math.Vec3d getRotationVec(float tickDelta);',
    'public net.minecraft.item.ItemStack getOffHandStack();',

    'public boolean canHit();',
])
add('net.minecraft.entity.mob.PathAwareEntity', ['net.minecraft.entity.mob.MobEntity'], [
    'public static net.minecraft.entity.attribute.DefaultAttributeContainer.Builder createMobAttributes();',
])
add('net.minecraft.entity.mob.MobEntity', ['net.minecraft.entity.LivingEntity'], [
    'public net.minecraft.entity.ai.goal.GoalSelector getTargetSelector();',
    'public net.minecraft.entity.ai.pathing.EntityNavigation getNavigation();',
    'public net.minecraft.util.math.random.Random getRandom();',
    'public void setPersistent();',
    'public boolean canPickUpLoot();',
    'public void setCanPickUpLoot(boolean canPickUpLoot);',
    'protected void initGoals();',
    'public net.minecraft.world.World getWorld();',
    'public ActionResult interactMob(net.minecraft.entity.player.PlayerEntity player, net.minecraft.util.Hand hand);',
    'public int getExperiencePoints();',
    'public int experiencePoints;',
])
add('net.minecraft.entity.player.PlayerEntity', ['net.minecraft.entity.LivingEntity'], [
    'public boolean isCreative();',
    'public void sendMessage(net.minecraft.text.Text message, boolean overlay);',
    'public boolean shouldCancelInteraction();',
    'public net.minecraft.util.math.Vec3d getCameraPosVec(float tickDelta);',
    'public net.minecraft.item.ItemCooldownManager getItemCooldownManager();',
    'public void addVelocity(double x, double y, double z);',
    'public boolean velocityModified;',
    'public void incrementStat(net.minecraft.stat.Stat stat);',
])
add('net.minecraft.item.ItemCooldownManager', [], [
    'public boolean isCoolingDown(net.minecraft.item.Item item);',
    'public void set(net.minecraft.item.Item item, int duration);',
])
add('net.minecraft.server.network.ServerPlayerEntity', ['net.minecraft.entity.player.PlayerEntity'], [
    'public net.minecraft.server.world.ServerWorld getServerWorld();',
])
add('net.minecraft.entity.EquipmentSlot', [], [
    'public static final net.minecraft.entity.EquipmentSlot MAINHAND;',
    'public static final net.minecraft.entity.EquipmentSlot OFFHAND;',
    'public static final net.minecraft.entity.EquipmentSlot HEAD;',
    'public static final net.minecraft.entity.EquipmentSlot CHEST;',
    'public static final net.minecraft.entity.EquipmentSlot LEGS;',
    'public static final net.minecraft.entity.EquipmentSlot FEET;',
    'public static net.minecraft.entity.EquipmentSlot getSlotForHand(net.minecraft.util.Hand hand);',
])
add('net.minecraft.entity.attribute.EntityAttributeModifier', [], [
    'public EntityAttributeModifier(net.minecraft.util.Identifier id, double value, net.minecraft.entity.attribute.EntityAttributeModifier.Operation operation);',
])
add('net.minecraft.entity.attribute.EntityAttributeModifier.Operation', [], [], iface=True)
add('net.minecraft.entity.attribute.EntityAttributes', [], [
    'public static final net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> GENERIC_ATTACK_DAMAGE;',
    'public static final net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> GENERIC_ATTACK_SPEED;',
    'public static final net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> GENERIC_ARMOR;',
    'public static final net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> PLAYER_ENTITY_INTERACTION_RANGE;',
    'public static final net.minecraft.entity.attribute.EntityAttribute GENERIC_MAX_HEALTH;',
    'public static final net.minecraft.entity.attribute.EntityAttribute GENERIC_MOVEMENT_SPEED;',
    'public static final net.minecraft.entity.attribute.EntityAttribute GENERIC_ATTACK_DAMAGE;',
    'public static final net.minecraft.entity.attribute.EntityAttribute GENERIC_FOLLOW_RANGE;',
    'public static final net.minecraft.entity.attribute.EntityAttribute GENERIC_KNOCKBACK_RESISTANCE;',
])
add('net.minecraft.entity.attribute.EntityAttribute', [], [])
add('net.minecraft.entity.attribute.EntityAttributeInstance', [], [
    'public void setBaseValue(double value);',
])
add('net.minecraft.entity.attribute.DefaultAttributeContainer', [], [])
add('net.minecraft.entity.attribute.DefaultAttributeContainer.Builder', [], [
    'public net.minecraft.entity.attribute.DefaultAttributeContainer.Builder add(net.minecraft.entity.attribute.EntityAttribute attribute, double value);',
])
add('net.minecraft.entity.damage.DamageSource', [], [
    'public net.minecraft.entity.Entity getAttacker();',
])
add('net.minecraft.entity.damage.DamageSources', [], [
    'public net.minecraft.entity.damage.DamageSource playerAttack(net.minecraft.entity.player.PlayerEntity player);',
    'public net.minecraft.entity.damage.DamageSource thrown(net.minecraft.entity.Entity source, net.minecraft.entity.Entity attacker);',
])
add('net.minecraft.entity.ai.goal.GoalSelector', [], [
    'public void add(int priority, net.minecraft.entity.ai.goal.Goal goal);',
])
add('net.minecraft.entity.ai.goal.Goal', [], [])
add('net.minecraft.entity.ai.goal.SwimGoal', ['net.minecraft.entity.ai.goal.Goal'], [
    'public SwimGoal(net.minecraft.entity.mob.PathAwareEntity mob);',
])
add('net.minecraft.entity.ai.goal.MeleeAttackGoal', ['net.minecraft.entity.ai.goal.Goal'], [
    'public MeleeAttackGoal(net.minecraft.entity.mob.PathAwareEntity mob, double speed, boolean pauseWhenIdle);',
])
add('net.minecraft.entity.ai.goal.WanderAroundFarGoal', ['net.minecraft.entity.ai.goal.WanderAroundGoal'], [
    'public WanderAroundFarGoal(net.minecraft.entity.mob.PathAwareEntity mob, double speed);',
])
add('net.minecraft.entity.ai.goal.WanderAroundGoal', ['net.minecraft.entity.ai.goal.Goal'], [])
add('net.minecraft.entity.ai.goal.LookAroundGoal', ['net.minecraft.entity.ai.goal.Goal'], [
    'public LookAroundGoal(net.minecraft.entity.mob.MobEntity mob);',
])
add('net.minecraft.entity.ai.goal.LookAtEntityGoal', ['net.minecraft.entity.ai.goal.Goal'], [
    'public LookAtEntityGoal(net.minecraft.entity.mob.MobEntity mob, java.lang.Class<?> targetClass, float range);',
])
add('net.minecraft.entity.ai.goal.RevengeGoal', ['net.minecraft.entity.ai.goal.TrackTargetGoal'], [
    'public RevengeGoal(net.minecraft.entity.mob.PathAwareEntity mob);',
])
add('net.minecraft.entity.ai.goal.TrackTargetGoal', ['net.minecraft.entity.ai.goal.Goal'], [])
add('net.minecraft.entity.ai.goal.ProjectileAttackGoal', ['net.minecraft.entity.ai.goal.Goal'], [
    'public ProjectileAttackGoal(net.minecraft.entity.ai.RangedAttackMob mob, double mobSpeed, int intervalTicks, float maxShootRange);',
])
add('net.minecraft.entity.ai.RangedAttackMob', [], [
    'public void shootAt(net.minecraft.entity.LivingEntity target, float pullProgress);',
], iface=True)
add('net.minecraft.entity.ai.pathing.EntityNavigation', [], [
    'public void stop();',
    'public boolean startMovingTo(net.minecraft.entity.Entity entity, double speed);',
    'public boolean startMovingTo(double x, double y, double z, double speed);',
])
add('net.minecraft.entity.projectile.ArrowEntity', ['net.minecraft.entity.projectile.PersistentProjectileEntity'], [
    'public ArrowEntity(net.minecraft.world.World world, net.minecraft.entity.LivingEntity owner, net.minecraft.item.ItemStack stack, net.minecraft.item.ItemStack weapon);',
    'public void setDamage(double damage);',
    'public void setVelocity(double dx, double dy, double dz, float speed, float divergence);',
])
add('net.minecraft.entity.projectile.PersistentProjectileEntity', ['net.minecraft.entity.projectile.ProjectileEntity'], [])
add('net.minecraft.entity.projectile.ProjectileEntity', ['net.minecraft.entity.Entity'], [
    'public void setOwner(net.minecraft.entity.Entity owner);',
    'public net.minecraft.entity.Entity getOwner();',
    'public void setVelocity(net.minecraft.entity.player.PlayerEntity user, float pitch, float yaw, float roll, float speed, float divergence);',
])
add('net.minecraft.entity.projectile.thrown.ThrownItemEntity', ['net.minecraft.entity.projectile.thrown.ThrownEntity'], [
    'protected ThrownItemEntity(net.minecraft.entity.EntityType<? extends net.minecraft.entity.projectile.thrown.ThrownItemEntity> type, net.minecraft.world.World world);',
    'protected ThrownItemEntity(net.minecraft.entity.EntityType<? extends net.minecraft.entity.projectile.thrown.ThrownItemEntity> type, net.minecraft.entity.LivingEntity owner, net.minecraft.world.World world);',
    'public net.minecraft.item.ItemStack getItem();',
    'public void setItem(net.minecraft.item.ItemStack stack);',
    'protected abstract net.minecraft.item.Item getDefaultItem();',
    'protected void onEntityHit(net.minecraft.util.hit.EntityHitResult hitResult);',
    'protected void onCollision(net.minecraft.util.hit.HitResult hitResult);',
    'protected float getGravity();',
])
add('net.minecraft.entity.projectile.thrown.ThrownEntity', ['net.minecraft.entity.projectile.ProjectileEntity'], [
    'public void tick();',
])
add('net.minecraft.entity.projectile.ProjectileUtil', [], [
    'public static net.minecraft.util.hit.EntityHitResult raycast(net.minecraft.entity.Entity entity, net.minecraft.util.math.Vec3d min, net.minecraft.util.math.Vec3d max, net.minecraft.util.math.Box box, java.util.function.Predicate<net.minecraft.entity.Entity> predicate, double maxDistance);',
])
add('net.minecraft.entity.vehicle.BoatEntity', ['net.minecraft.entity.Entity'], [
    'public BoatEntity(net.minecraft.entity.EntityType<? extends BoatEntity> type, net.minecraft.world.World world);',
    'public void setVariant(net.minecraft.entity.vehicle.BoatEntity.Type variant);',
])
add('net.minecraft.entity.vehicle.BoatEntity.Type', [], [
    'public static final net.minecraft.entity.vehicle.BoatEntity.Type SPRUCE;',
])
add('net.minecraft.entity.data.DataTracker', [], [
])
add('net.minecraft.entity.data.DataTracker.Builder', [], [
    'public <T> void add(net.minecraft.entity.data.TrackedData<T> data, T value);',
])
add('net.minecraft.entity.data.TrackedData', [], [])
add('net.minecraft.entity.data.TrackedDataHandlerRegistry', [], [
    'public static final net.minecraft.entity.data.TrackedDataHandler<String> STRING;',
    'public static final net.minecraft.entity.data.TrackedDataHandler<Integer> INTEGER;',
    'public static final net.minecraft.entity.data.TrackedDataHandler<Float> FLOAT;',
    'public static final net.minecraft.entity.data.TrackedDataHandler<Boolean> BOOLEAN;',
])
add('net.minecraft.entity.data.TrackedDataHandler', [], [])

# ---- particles
add('net.minecraft.particle.ParticleTypes', [], [
    'public static final net.minecraft.particle.ParticleType CLOUD;',
    'public static final net.minecraft.particle.ParticleType EXPLOSION;',
    'public static final net.minecraft.particle.ParticleType LARGE_SMOKE;',
    'public static final net.minecraft.particle.ParticleType SMOKE;', 'public static final net.minecraft.particle.ParticleType LAVA;', 'public static final net.minecraft.particle.ParticleType ANGRY_VILLAGER;', 'public static final net.minecraft.particle.ParticleType NOTE;', 'public static final net.minecraft.particle.ParticleType HAPPY_VILLAGER;',
    'public static final net.minecraft.particle.ParticleType POOF;',
    'public static final net.minecraft.particle.ParticleType FLAME;',
    'public static final net.minecraft.particle.ParticleType CRIT;', 'public static final net.minecraft.particle.ParticleType HEART;', 'public static final net.minecraft.particle.ParticleType SMOKE;', 'public static final net.minecraft.particle.ParticleType LAVA;', 'public static final net.minecraft.particle.ParticleType ANGRY_VILLAGER;', 'public static final net.minecraft.particle.ParticleType NOTE;', 'public static final net.minecraft.particle.ParticleType HAPPY_VILLAGER;',
    'public static final net.minecraft.particle.ParticleType SPLASH;',
    'public static final net.minecraft.particle.ParticleType SWEEP_ATTACK;',
])
add('net.minecraft.particle.ParticleType', [], [])

# ---- world
add('net.minecraft.world.World', [], [
    'public final boolean isThundering();',
    'public boolean isClient;',
    'public long getTime();',
    'public net.minecraft.util.math.random.Random random;',
    'public net.minecraft.entity.damage.DamageSources getDamageSources();',
    'public void playSound(net.minecraft.entity.Entity source, net.minecraft.util.math.BlockPos pos, net.minecraft.sound.SoundEvent sound, net.minecraft.sound.SoundCategory category, float volume, float pitch);',
    'public void playSound(net.minecraft.entity.player.PlayerEntity except, double x, double y, double z, net.minecraft.sound.SoundEvent sound, net.minecraft.sound.SoundCategory category, float volume, float pitch);',
    'public void playSound(net.minecraft.entity.player.PlayerEntity except, double x, double y, double z, net.minecraft.registry.entry.RegistryEntry<net.minecraft.sound.SoundEvent> sound, net.minecraft.sound.SoundCategory category, float volume, float pitch);',
    'public boolean spawnEntity(net.minecraft.entity.Entity entity);',
    'public net.minecraft.entity.player.PlayerEntity getClosestPlayer(net.minecraft.entity.Entity entity, double maxDistance);',
    'public java.util.List<net.minecraft.entity.Entity> getOtherEntities(net.minecraft.entity.Entity except, net.minecraft.util.math.Box box, java.util.function.Predicate<? super net.minecraft.entity.Entity> predicate);',
    'public java.util.List<net.minecraft.entity.player.PlayerEntity> getPlayers();',
    'public net.minecraft.entity.player.PlayerEntity getPlayerByUuid(java.util.UUID uuid);',
    'public net.minecraft.fluid.FluidState getFluidState(net.minecraft.util.math.BlockPos pos);',
    'public net.minecraft.block.BlockState getBlockState(net.minecraft.util.math.BlockPos pos);',
    'public net.minecraft.block.entity.BlockEntity getBlockEntity(net.minecraft.util.math.BlockPos pos);',
    'public boolean setBlockState(net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState state, int flags);',
    'public int getBottomY();', 'public int getTopY();',
    'public net.minecraft.util.math.BlockPos getTopPosition(net.minecraft.world.Heightmap.Type type, net.minecraft.util.math.BlockPos pos);',
    'public net.minecraft.registry.RegistryKey<net.minecraft.world.World> getRegistryKey();',
    'public net.minecraft.world.explosion.Explosion createExplosion(net.minecraft.entity.Entity entity, double x, double y, double z, float power, net.minecraft.world.World.ExplosionSourceType sourceType);',
    'public <T extends net.minecraft.entity.Entity> java.util.List<T> getEntitiesByClass(java.lang.Class<T> clazz, net.minecraft.util.math.Box box, java.util.function.Predicate<? super T> predicate);',
    'public String getBiome(net.minecraft.util.math.BlockPos pos);',
    'public static final net.minecraft.registry.RegistryKey<net.minecraft.world.World> OVERWORLD;',
])
add('net.minecraft.world.World.ExplosionSourceType', [], [
    'public static final net.minecraft.world.World.ExplosionSourceType MOB;',
])
add('net.minecraft.world.Heightmap', [], [])
add('net.minecraft.world.Heightmap.Type', [], [
    'public static final net.minecraft.world.Heightmap.Type MOTION_BLOCKING_NO_LEAVES;',
    'public static final net.minecraft.world.Heightmap.Type MOTION_BLOCKING;',
    'public static final net.minecraft.world.Heightmap.Type WORLD_SURFACE;',
])
add('net.minecraft.server.world.ServerWorld', ['net.minecraft.world.World'], [
    'public net.minecraft.entity.Entity getEntity(java.util.UUID uuid);',
    'public void spawnParticles(net.minecraft.particle.ParticleType particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed);',
    'public net.minecraft.world.PersistentStateManager getPersistentStateManager();',
    'public long getSeed();',
    'public net.minecraft.server.MinecraftServer getServer();',
    'public net.minecraft.world.chunk.ChunkManager getChunkManager();',
    'public net.minecraft.world.chunk.Chunk getChunk(int chunkX, int chunkZ, net.minecraft.world.chunk.ChunkStatus leastStatus, boolean load);',
])
add('net.minecraft.world.chunk.ChunkManager', [], [
    'public boolean isChunkLoaded(int x, int z);',
])
add('net.minecraft.server.MinecraftServer', [], [
    'public java.lang.Iterable<net.minecraft.server.world.ServerWorld> getWorlds();',
    'public net.minecraft.server.PlayerManager getPlayerManager();',
])
add('net.minecraft.server.PlayerManager', [], [
    'public java.util.List<net.minecraft.server.network.ServerPlayerEntity> getPlayerList();',
    'public net.minecraft.server.network.ServerPlayerEntity getPlayer(java.util.UUID uuid);',
])
add('net.minecraft.world.PersistentState', [], [
    'public void markDirty();',
    'public net.minecraft.nbt.NbtCompound writeNbt(net.minecraft.nbt.NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup);',
])
add('net.minecraft.world.PersistentStateManager', [], [
    'public <T extends net.minecraft.world.PersistentState> T getOrCreate(net.minecraft.world.PersistentState.Type<T> type, String id);',
])
add('net.minecraft.world.PersistentState.Type', [], [])
add('net.minecraft.datafixer.DataFixTypes', [], [])
add('net.minecraft.registry.tag.FluidTags', [], [
    'public static final net.minecraft.registry.tag.TagKey WATER;',
])
add('net.minecraft.registry.RegistryKeys', [], [
    'public static final net.minecraft.registry.RegistryKey<net.minecraft.registry.Registry<net.minecraft.world.World>> WORLD;',
    'public static final net.minecraft.registry.RegistryKey<net.minecraft.registry.Registry<net.minecraft.item.Item>> ITEM;',
    'public static final net.minecraft.registry.RegistryKey<net.minecraft.registry.Registry<net.minecraft.recipe.RecipeEntry<?>>> RECIPE;',
])
add('net.minecraft.registry.tag.TagKey', [], [])
add('net.minecraft.fluid.FluidState', [], [
    'public boolean isIn(net.minecraft.registry.tag.TagKey tag);',
    'public boolean isEmpty();',
])
add('net.minecraft.world.biome.Biome', [], [])

# ---- brigadier / fabric
add('com.mojang.brigadier.CommandDispatcher', [], [
    'public void register(com.mojang.brigadier.builder.LiteralArgumentBuilder command);',
])
add('com.mojang.brigadier.builder.LiteralArgumentBuilder', [], [
    'public static com.mojang.brigadier.builder.LiteralArgumentBuilder literal(String name);',
    'public com.mojang.brigadier.builder.LiteralArgumentBuilder then(com.mojang.brigadier.builder.ArgumentBuilder arg);',
    'public com.mojang.brigadier.builder.LiteralArgumentBuilder requires(java.util.function.Predicate<S> requirement);',
    'public com.mojang.brigadier.builder.LiteralArgumentBuilder executes(com.mojang.brigadier.Command command);',
])
add('com.mojang.brigadier.builder.ArgumentBuilder', [], [
    'public com.mojang.brigadier.builder.ArgumentBuilder then(com.mojang.brigadier.builder.ArgumentBuilder arg);',
])
add('com.mojang.brigadier.builder.RequiredArgumentBuilder', ['com.mojang.brigadier.builder.ArgumentBuilder'], [
    'public static <S, T> com.mojang.brigadier.builder.RequiredArgumentBuilder<S, T> argument(String name, com.mojang.brigadier.arguments.ArgumentType<T> type);',
    'public com.mojang.brigadier.builder.RequiredArgumentBuilder<S, T> suggests(com.mojang.brigadier.suggestion.SuggestionProvider<S> provider);',
])
add('com.mojang.brigadier.arguments.IntegerArgumentType', [], [
    'public static com.mojang.brigadier.arguments.IntegerArgumentType integer(int min, int max);',
    'public static int getInteger(com.mojang.brigadier.context.CommandContext ctx, String name);',
])
add('com.mojang.brigadier.arguments.StringArgumentType', [], [
    'public static com.mojang.brigadier.arguments.StringArgumentType word();',
    'public static String getString(com.mojang.brigadier.context.CommandContext ctx, String name);',
])
add('com.mojang.brigadier.context.CommandContext', [], [
    'public S getSource();',
])
add('com.mojang.brigadier.Command', [], [
    'public int run(com.mojang.brigadier.context.CommandContext<S> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException;',
], iface=True)
add('com.mojang.brigadier.exceptions.CommandSyntaxException', ['java.lang.Exception'], [])
add('com.mojang.brigadier.suggestion.SuggestionsBuilder', [], [
    'public com.mojang.brigadier.suggestion.SuggestionsBuilder suggest(String text);',
    'public java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> buildFuture();',
])
add('net.minecraft.command.CommandRegistryAccess', [], [], iface=True)
add('net.minecraft.command.argument.EntityArgumentType', [], [
    'public static net.minecraft.command.argument.EntityArgumentType entity();',
    'public static net.minecraft.entity.Entity getEntity(com.mojang.brigadier.context.CommandContext ctx, String name);',
])
add('net.minecraft.server.command.CommandManager', [], [
    'public static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.server.command.ServerCommandSource> literal(String name);',
])
add('net.minecraft.server.command.ServerCommandSource', [], [
    'public boolean hasPermissionLevel(int level);',
    'public net.minecraft.server.world.ServerWorld getWorld();',
    'public net.minecraft.server.network.ServerPlayerEntity getPlayer();',
    'public void sendError(net.minecraft.text.Text message);',
    'public void sendFeedback(java.util.function.Supplier<net.minecraft.text.Text> feedback, boolean broadcast);',
])
add('net.minecraft.server.command.CommandManager.RegistrationEnvironment', [], [])
add('net.fabricmc.api.ModInitializer', [], ['public void onInitialize();'], iface=True)
add('net.fabricmc.api.ClientModInitializer', [], ['public void onInitializeClient();'], iface=True)
add('net.fabricmc.fabric.api.event.Event', [], [
    'public void register(java.util.function.Consumer<Object> listener);',
])
add('net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback', [], [
    'public static final net.fabricmc.fabric.api.event.Event EVENT;',
    'public void register(com.mojang.brigadier.CommandDispatcher dispatcher, net.minecraft.command.CommandRegistryAccess access, net.minecraft.server.command.CommandManager.RegistrationEnvironment env);',
], iface=True)
add('net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents', [], [
    'public static final net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted SERVER_STARTED;',
])
add('net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted', [], [
    'public void register(java.util.function.Consumer<net.minecraft.server.MinecraftServer> callback);',
], iface=True)
add('net.fabricmc.fabric.api.event.Event', [], [
    'public void register(Object listener);',
])
add('net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents', [], [
    'public static final net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndServerTick END_SERVER_TICK;',
])
add('net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndServerTick', [], [
    'public void register(java.util.function.Consumer<net.minecraft.server.MinecraftServer> callback);',
], iface=True)
add('net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents', [], [
    'public static final net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.ChunkLoad CHUNK_LOAD;',
])
add('net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.ChunkLoad', [], [
    'public void register(java.util.function.BiConsumer<net.minecraft.server.world.ServerWorld, net.minecraft.world.chunk.WorldChunk> callback);',
], iface=True)
add('net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry', [], [
    'public static void register(net.minecraft.entity.EntityType<? extends net.minecraft.entity.LivingEntity> type, net.minecraft.entity.attribute.DefaultAttributeContainer attributes);',
])
add('net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup', [], [
    'public static net.minecraft.item.ItemGroup.Builder builder();',
])
add('net.minecraft.item.ItemGroup', [], [])
add('net.minecraft.item.ItemGroup.Builder', [], [
    'public net.minecraft.item.ItemGroup.Builder displayName(net.minecraft.text.Text name);',
    'public net.minecraft.item.ItemGroup.Builder icon(java.util.function.Supplier<net.minecraft.item.ItemStack> icon);',
    'public net.minecraft.item.ItemGroup.Builder entries(net.minecraft.item.ItemGroup.EntryCollector collector);',
    'public net.minecraft.item.ItemGroup build();',
])
add('net.minecraft.item.ItemGroup.EntryCollector', [], [
    'public void accept(net.minecraft.item.ItemGroup.DisplayContext context, net.minecraft.util.collection.DefaultedList<net.minecraft.item.ItemStack> entries);',
], iface=True)
add('net.minecraft.util.collection.DefaultedList', [], [])

# ---- client
add('net.minecraft.client.MinecraftClient', [], [
    'public net.minecraft.client.network.ClientPlayerEntity player;',
])
add('net.minecraft.client.network.ClientPlayerEntity', ['net.minecraft.entity.player.PlayerEntity'], [
    'public net.minecraft.client.input.Input input;',
])
add('net.minecraft.client.input.Input', [], [
    'public boolean pressingForward;', 'public boolean pressingBack;',
    'public boolean pressingLeft;', 'public boolean pressingRight;',
    'public boolean jumping;', 'public boolean sneaking;',
])
add('net.minecraft.client.model.ModelData', [], [
    'public net.minecraft.client.model.ModelPartData getRoot();',
])
add('net.minecraft.client.model.ModelPartData', [], [
    'public net.minecraft.client.model.ModelPartData addChild(String name, net.minecraft.client.model.ModelPartBuilder builder, net.minecraft.client.model.ModelTransform transform);',
])
add('net.minecraft.client.model.ModelPartBuilder', [], [
    'public static net.minecraft.client.model.ModelPartBuilder create();',
    'public net.minecraft.client.model.ModelPartBuilder uv(int u, int v);',
    'public net.minecraft.client.model.ModelPartBuilder cuboid(float x, float y, float z, float w, float h, float d);',
])
add('net.minecraft.client.model.ModelTransform', [], [
    'public static net.minecraft.client.model.ModelTransform pivot(float x, float y, float z);',
])
add('net.minecraft.client.model.TexturedModelData', [], [
    'public static net.minecraft.client.model.TexturedModelData of(net.minecraft.client.model.ModelData data, int u, int v);',
])
add('net.minecraft.client.render.entity.model.EntityModel', ['net.minecraft.client.model.Model'], [
    'public abstract void setAngles(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch);',
])
add('net.minecraft.client.model.Model', [], [
    'public abstract void render(net.minecraft.client.util.math.MatrixStack matrices, net.minecraft.client.render.VertexConsumer vertices, int light, int overlay, int color);',
    'public net.minecraft.client.render.RenderLayer getLayer(net.minecraft.util.Identifier texture);',
])
add('net.minecraft.client.model.ModelPart', [], [
    'public void render(net.minecraft.client.util.math.MatrixStack matrices, net.minecraft.client.render.VertexConsumer vertices, int light, int overlay);',
    'public net.minecraft.client.model.ModelPart getChild(String name);',
    'public float pivotY;', 'public float roll;',
])
add('net.minecraft.client.render.entity.EntityRenderer', [], [
    'public void render(T entity, float yaw, float tickDelta, net.minecraft.client.util.math.MatrixStack matrices, net.minecraft.client.render.VertexConsumerProvider vertexConsumers, int light);',
    'public abstract net.minecraft.util.Identifier getTexture(T entity);',
])
add('net.minecraft.client.render.entity.EntityRendererFactory.Context', [], [
    'public net.minecraft.client.model.ModelPart getPart(net.minecraft.client.render.entity.model.EntityModelLayer layer);',
    'public net.minecraft.client.font.TextRenderer getTextRenderer();',
    'public net.minecraft.client.render.model.BakedModelManager getModelManager();',
    'public net.minecraft.client.render.entity.model.EntityModelLoader getModelLoader();',
])
add('net.minecraft.client.font.TextRenderer', [], [])
add('net.minecraft.client.render.entity.model.EntityModelLayer', [], [
    'public EntityModelLayer(net.minecraft.util.Identifier id, String layer);',
])
add('net.minecraft.client.render.entity.model.EntityModelLayers', [], [
    'public static final net.minecraft.client.render.entity.model.EntityModelLayers PLAYER;',
    'public static final net.minecraft.client.render.entity.model.EntityModelLayers PLAYER_INNER_ARMOR;',
    'public static final net.minecraft.client.render.entity.model.EntityModelLayers PLAYER_OUTER_ARMOR;',
])
add('net.minecraft.client.render.entity.model.BipedEntityModel', ['net.minecraft.client.model.EntityModel'], [
    'public BipedEntityModel(net.minecraft.client.model.ModelPart part);',
])
add('net.minecraft.client.render.entity.BipedEntityRenderer', ['net.minecraft.client.render.entity.LivingEntityRenderer'], [
    'public BipedEntityRenderer(net.minecraft.client.render.entity.EntityRendererFactory.Context ctx, net.minecraft.client.render.entity.model.BipedEntityModel model, float shadowRadius);',
])
add('net.minecraft.client.render.entity.LivingEntityRenderer', ['net.minecraft.client.render.entity.EntityRenderer'], [
    'protected boolean addFeature(net.minecraft.client.render.entity.feature.FeatureRenderer feature);',
])
add('net.minecraft.client.render.entity.feature.FeatureRenderer', [], [])
add('net.minecraft.client.render.model.BakedModelManager', [], [])
add('net.minecraft.client.render.entity.feature.ArmorFeatureRenderer', ['net.minecraft.client.render.entity.feature.FeatureRenderer'], [
    'public ArmorFeatureRenderer(net.minecraft.client.render.entity.BipedEntityRenderer renderer, net.minecraft.client.render.entity.model.BipedEntityModel innerArmor, net.minecraft.client.render.entity.model.BipedEntityModel outerArmor, net.minecraft.client.render.model.BakedModelManager manager);',
])
add('net.minecraft.client.render.entity.model.EntityModelLoader', [], [
    'public net.minecraft.client.model.ModelPart getPart(net.minecraft.client.render.entity.model.EntityModelLayer layer);',
])
add('net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer', ['net.minecraft.client.render.entity.feature.FeatureRenderer'], [
    'public HeldItemFeatureRenderer(net.minecraft.client.render.entity.LivingEntityRenderer renderer, net.minecraft.client.render.item.HeldItemRenderer heldItemRenderer);',
])
add('net.minecraft.client.render.item.HeldItemRenderer', [], [])
add('net.minecraft.client.render.entity.FlyingItemEntityRenderer', ['net.minecraft.client.render.entity.EntityRenderer'], [
    'public FlyingItemEntityRenderer(net.minecraft.client.render.entity.EntityRendererFactory.Context ctx);',
])
add('net.minecraft.client.render.VertexConsumerProvider', [], [
    'public net.minecraft.client.render.VertexConsumer getBuffer(net.minecraft.client.render.RenderLayer layer);',
])
add('net.minecraft.client.render.VertexConsumer', [], [])
add('net.minecraft.client.render.RenderLayer', [], [])
add('net.minecraft.client.render.OverlayTexture', [], [
    'public static final int DEFAULT_UV;',
])
add('net.minecraft.client.util.math.MatrixStack', [], [
    'public void push();', 'public void pop();',
    'public void translate(double x, double y, double z);',
    'public void scale(float x, float y, float z);',
    'public void multiply(org.joml.Quaternionf quaternion);',
])
add('net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry', [], [
    'public static <T extends net.minecraft.entity.Entity> void register(net.minecraft.entity.EntityType<T> type, java.util.function.Function<net.minecraft.client.render.entity.EntityRendererFactory.Context, net.minecraft.client.render.entity.EntityRenderer<? super T>> factory);',
])
add('net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry', [], [
    'public static void registerModelLayer(net.minecraft.client.render.entity.model.EntityModelLayer layer, java.util.function.Supplier<net.minecraft.client.model.TexturedModelData> provider);',
])
add('net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap', [], [
    'public static final net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap INSTANCE;',
    'public void putBlock(net.minecraft.block.Block block, net.minecraft.client.render.RenderLayer renderLayer);',
])
add('net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents', [], [
    'public static final net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndClientTick END_CLIENT_TICK;',
])
add('net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndClientTick', [], [
    'public void register(java.util.function.Consumer<net.minecraft.client.MinecraftClient> callback);',
], iface=True)
add('net.minecraft.entity.FallingBlockEntity', [], [
    'public FallingBlockEntity(net.minecraft.world.World world, double x, double y, double z, net.minecraft.block.BlockState blockState);',
])
add('net.minecraft.entity.LightningEntity', [], [
    'public LightningEntity(net.minecraft.entity.EntityType<? extends net.minecraft.entity.LightningEntity> type, net.minecraft.world.World world);',
    'public void refreshPositionAfterTeleport(double x, double y, double z);',
])
add('net.minecraft.block.BedBlock', [], [
    'public static final net.minecraft.state.property.EnumProperty<net.minecraft.block.enums.BedPart> PART;',
])
add('net.minecraft.block.enums.BedPart', [], [])
add('org.joml.Quaternionf', [], [])
add('org.slf4j.Logger', [], [
    'public void info(String msg);', 'public void info(String msg, Object arg);',
    'public void error(String msg);', 'public void error(String msg, Object arg, Object arg2);',
    'public void error(String msg, Object arg, Throwable t);', 'public void debug(String msg, Object arg);',
])
add('org.slf4j.LoggerFactory', [], [
    'public static org.slf4j.Logger getLogger(String name);',
])


# ---- additional classes referenced by mod code (layer-1 universe completeness)
add('net.minecraft.client.render.entity.EntityRendererFactory', [], [], iface=True)
add('net.minecraft.util.shape.VoxelShape', [], [])
add('net.minecraft.util.shape.VoxelShapes', [], [
    'public static net.minecraft.util.shape.VoxelShape union(net.minecraft.util.shape.VoxelShape a, net.minecraft.util.shape.VoxelShape b);',
])
add('net.minecraft.world.BlockView', [], [], iface=True)
add('net.minecraft.entity.MovementType', [], [
    'public static final net.minecraft.entity.MovementType SELF;',
])
add('org.jetbrains.annotations.Nullable', [], [], iface=True)
add('net.minecraft.block.LanternBlock', ['net.minecraft.block.Block'], [])
add('net.minecraft.block.LadderBlock', ['net.minecraft.block.Block'], [])
add('net.minecraft.util.math.ChunkPos', [], [
    'public ChunkPos(int x, int z);',
    'public int x;', 'public int z;',
    'public net.minecraft.util.math.BlockPos getStartPos();',
    'public net.minecraft.util.math.ChunkPos from(long packed);',
])
add('net.minecraft.world.chunk.WorldChunk', [], [
    'public net.minecraft.util.math.ChunkPos getPos();',
])
add('net.minecraft.state.State', [], [])
add('net.minecraft.server.world.ServerChunkManager', ['net.minecraft.world.chunk.ChunkManager'], [])

TEMPLATE = '''// AUTO-GENERATED API STUB (scripts/apicheck.py)
package {pkg};

public {kind} {name}{extends} {{
{members}
}}
'''


NULLABLE_SRC = '''// AUTO-GENERATED API STUB (scripts/apicheck.py)
package org.jetbrains.annotations;

public @interface Nullable {
}
'''


def emit():
    nullable = STUBS / 'org/jetbrains/annotations/Nullable.java'
    nullable.parent.mkdir(parents=True, exist_ok=True)
    nullable.write_text(NULLABLE_SRC)
    for (pkg, name), info in CLASSES.items():
        members = '\n'.join('    ' + m for m in info['members'])
        extends = (' extends ' + ', '.join(info['supers'])) if info['supers'] else ''
        kind = 'interface' if info['iface'] else 'class'
        content = TEMPLATE.format(pkg=pkg, name=name, extends=extends, members=members, kind=kind)
        path = STUBS / pkg.replace('.', '/') / (name + '.java')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
    print(f"wrote {len(CLASSES)} stub classes to {STUBS}")


if __name__ == '__main__':
    emit()

add('net.minecraft.world.chunk.ChunkStatus', [], [
    'public static final net.minecraft.world.chunk.ChunkStatus FULL;',
]);


# ---- additions for the Rival Realms III rewrite ------------------------------

def extend(fqcn, members):
    pkg, _, name = fqcn.rpartition('.')
    CLASSES.setdefault((pkg, name), {'supers': [], 'members': [], 'iface': False})
    CLASSES[(pkg, name)]['members'].extend(members)


add('net.minecraft.util.BlockRotation', [], [
    'public net.minecraft.util.math.Direction rotate(net.minecraft.util.math.Direction direction);',
])
add('net.minecraft.nbt.NbtString', [], [
    'public static net.minecraft.nbt.NbtString of(String value);',
])
add('net.minecraft.item.ItemGroup.Entries', [], [
    'public void add(net.minecraft.item.ItemStack stack);',
    'public void add(net.minecraft.item.ItemConvertible item);',
], iface=True)
add('net.minecraft.item.ItemGroup.DisplayContext', [], [])
extend('net.minecraft.item.ItemGroup.EntryCollector', [
    'public void accept(net.minecraft.item.ItemGroup.DisplayContext context, net.minecraft.item.ItemGroup.Entries entries);',
])
extend('net.minecraft.block.Block', [
    'public static final int NOTIFY_NEIGHBORS;',
    'public static final int NOTIFY_LISTENERS;',
    'public static final int NOTIFY_ALL;',
    'public net.minecraft.item.Item asItem();',
])
extend('net.minecraft.util.Formatting', [
    'public static final net.minecraft.util.Formatting ITALIC;',
])
extend('net.minecraft.item.Items', [
    'public static final net.minecraft.item.Item BOOK;',
    'public static final net.minecraft.item.Item FLINT_AND_STEEL;',
    'public static final net.minecraft.item.Item RAW_IRON;',
])
extend('net.minecraft.block.Blocks', [
    'public static final net.minecraft.block.Block IRON_BLOCK;',
    'public static final net.minecraft.block.Block SPRUCE_FENCE;',
    'public static final net.minecraft.block.Block DARK_OAK_FENCE;',
    'public static final net.minecraft.block.Block STONE_BRICK_WALL;',
    'public static final net.minecraft.block.Block RAW_IRON_BLOCK;',
])
extend('net.minecraft.sound.SoundEvents', [
    'public static final net.minecraft.sound.SoundEvent BLOCK_BARREL_OPEN;',
    'public static final net.minecraft.sound.SoundEvent BLOCK_NOTE_BLOCK_PLING;',
    'public static final net.minecraft.sound.SoundEvent ENTITY_HORSE_GALLOP;',
])
add('net.minecraft.block.SlabBlock', ['net.minecraft.block.Block'], [
    'public SlabBlock(net.minecraft.block.AbstractBlock.Settings settings);',
])
add('net.minecraft.block.FenceBlock', ['net.minecraft.block.Block'], [
    'public FenceBlock(net.minecraft.block.AbstractBlock.Settings settings);',
])


# ================= manhunt mod surface (1.21.1 yarn) =================

def extend(fqcn, members):
    pkg, _, name = fqcn.rpartition('.')
    entry = CLASSES.setdefault((pkg, name), {'supers': [], 'members': [], 'iface': False})
    entry['members'].extend(members)


extend('net.minecraft.entity.player.PlayerEntity', [
    'public PlayerEntity(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos, float yaw, com.mojang.authlib.GameProfile profile);',
    'public net.minecraft.entity.player.PlayerInventory getInventory();',
    'public net.minecraft.entity.player.HungerManager getHungerManager();',
    'public void attack(net.minecraft.entity.Entity target);',
    'public float getAttackCooldownProgress(float baseTime);',
    'public static net.minecraft.entity.attribute.DefaultAttributeContainer.Builder createPlayerAttributes();',
    'public abstract boolean isCreative();',
    'public abstract boolean isSpectator();',
])
add('net.minecraft.entity.player.PlayerInventory', [], [
    'public boolean insertStack(net.minecraft.item.ItemStack stack);',
    'public int count(net.minecraft.item.Item item);',
    'public int selectedSlot;',
    'public net.minecraft.item.ItemStack getStack(int slot);',
    'public void setStack(int slot, net.minecraft.item.ItemStack stack);',
    'public int size();',
    'public void writeNbt(net.minecraft.nbt.NbtList nbt);',
    'public void readNbt(net.minecraft.nbt.NbtList nbt);',
])
add('net.minecraft.entity.player.HungerManager', [], [
    'public int getFoodLevel();',
    'public void add(int hunger, float saturation);',
    'public void update(net.minecraft.entity.LivingEntity player);',
])
add('com.mojang.authlib.GameProfile', [], [
    'public GameProfile(java.util.UUID id, String name);',
])
add('net.minecraft.item.ArmorItem', ['net.minecraft.item.Item'], [
    'public net.minecraft.entity.EquipmentSlot getSlotType();',
])
add('net.minecraft.item.BowItem', ['net.minecraft.item.Item'], [])
add('net.minecraft.component.type.FoodComponent', [], [
    'public int nutrition();',
    'public float saturation();',
])
add('net.minecraft.component.DataComponentTypes', [], [
    'public static final net.minecraft.component.ComponentType<net.minecraft.component.type.FoodComponent> FOOD;',
])
add('net.minecraft.component.ComponentMap', [], [
    'public <T> T get(net.minecraft.component.ComponentType<T> type);',
], iface=True)
add('net.minecraft.component.ComponentType', [], [], iface=True)
extend('net.minecraft.item.Item', [
    'public net.minecraft.component.ComponentMap getComponents();',
])
add('net.minecraft.item.ItemUsageContext', [], [
    'public ItemUsageContext(net.minecraft.entity.player.PlayerEntity player, net.minecraft.util.Hand hand, net.minecraft.util.hit.BlockHitResult hit);',
])
extend('net.minecraft.item.ItemStack', [
    'public net.minecraft.registry.entry.RegistryEntry<net.minecraft.item.Item> getRegistryEntry();',
    'public float getMiningSpeedMultiplier(net.minecraft.block.BlockState state);',
    'public net.minecraft.util.ActionResult useOnBlock(net.minecraft.item.ItemUsageContext context);',
])
extend('net.minecraft.item.Item', [
    'public net.minecraft.item.FoodComponent getFoodComponent();',
])
add('net.minecraft.recipe.Recipe', [], [
    'public net.minecraft.item.ItemStack getResult(net.minecraft.registry.RegistryWrapper.WrapperLookup registries);',
    'public java.util.List<net.minecraft.recipe.Ingredient> getIngredients();',
], iface=True)
add('net.minecraft.recipe.RecipeEntry', [], [
    'public net.minecraft.recipe.Recipe<?> value();',
])
extend('net.minecraft.registry.Registry', [
]) if False else None
add('net.minecraft.recipe.Ingredient', [], [
    'public net.minecraft.item.ItemStack[] getMatchingStacks();',
])
add('net.minecraft.recipe.CraftingRecipe', ['net.minecraft.recipe.Recipe'], [], iface=True)
add('net.minecraft.recipe.SmeltingRecipe', ['net.minecraft.recipe.Recipe'], [])
add('net.minecraft.registry.RegistryKeys', [], [
    'public static final net.minecraft.registry.RegistryKey<net.minecraft.registry.Registry<net.minecraft.world.World>> WORLD;',
    'public static final net.minecraft.registry.RegistryKey<net.minecraft.registry.Registry<net.minecraft.item.Item>> ITEM;',
    'public static final net.minecraft.registry.RegistryKey<net.minecraft.registry.Registry<net.minecraft.recipe.RecipeEntry<?>>> RECIPE;',
])
add('net.minecraft.registry.tag.TagKey', [], [
    'public static <T> net.minecraft.registry.tag.TagKey<T> of(net.minecraft.registry.RegistryKey<?> registry, net.minecraft.util.Identifier id);',
])
add('net.minecraft.registry.RegistryKey', [], [
    'public static <T> net.minecraft.registry.RegistryKey<T> of(net.minecraft.registry.RegistryKey<?> registry, net.minecraft.util.Identifier id);',
])
extend('net.minecraft.registry.entry.RegistryEntry', [
    'public boolean isIn(net.minecraft.registry.tag.TagKey<?> tag);',
])
add('net.minecraft.registry.DynamicRegistryManager', [], [
    'public net.minecraft.registry.Registry<?> get(net.minecraft.registry.RegistryKey<?> key);',
])
extend('net.minecraft.entity.Entity', [
    'public void setYaw(float yaw);',
    'public void setPitch(float pitch);',
    'public float getYaw();',
    'public float getPitch();',
    'public net.minecraft.util.math.Vec3d getCameraPosVec(float tickDelta);',
    'public boolean velocityDirty;',
    'public net.minecraft.registry.DynamicRegistryManager getRegistryManager();',
])
extend('net.minecraft.entity.LivingEntity', [
    'public void travel(net.minecraft.util.math.Vec3d movementInput);',
    'public net.minecraft.item.ItemStack getEquippedStack(net.minecraft.entity.EquipmentSlot slot);',
    'public void equipStack(net.minecraft.entity.EquipmentSlot slot, net.minecraft.item.ItemStack stack);',
])
add('net.minecraft.entity.passive.PassiveEntity', ['net.minecraft.entity.MobEntity'], [])
add('net.minecraft.entity.passive.AnimalEntity', ['net.minecraft.entity.passive.PassiveEntity'], [])
add('net.minecraft.item.ArrowItem', ['net.minecraft.item.Item'], [
    'public net.minecraft.entity.projectile.PersistentProjectileEntity createArrow(net.minecraft.world.World world, net.minecraft.item.ItemStack stack, net.minecraft.entity.LivingEntity shooter, net.minecraft.item.ItemStack weapon);',
])
extend('net.minecraft.entity.projectile.ProjectileEntity', [
    'public void setOwner(net.minecraft.entity.Entity owner);',
])
extend('net.minecraft.entity.projectile.PersistentProjectileEntity', [
    'public void setVelocity(net.minecraft.entity.LivingEntity shooter, float pitch, float yaw, float roll, float speed, float divergence);',
])
extend('net.minecraft.util.math.BlockPos', [
    'public static java.lang.Iterable<net.minecraft.util.math.BlockPos> iterateRandomly(net.minecraft.util.math.random.Random random, int count, int x0, int y0, int z0, int x1, int y1, int z1);',
    'public net.minecraft.util.math.BlockPos toImmutable();',
    'public net.minecraft.util.math.BlockPos offset(net.minecraft.util.math.Direction direction);',
    'public net.minecraft.util.math.BlockPos up(int);',
    'public net.minecraft.util.math.BlockPos down();',
    'public static net.minecraft.util.math.BlockPos fromLong(long);',
    'public long asLong();',
    'public String toShortString();',
])
extend('net.minecraft.block.Block', [
    'public float getHardness();',
])
extend('net.minecraft.block.BlockState', [
    'public float getHardness(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos);',
    'public net.minecraft.block.SoundGroup getSoundGroup();',
    'public boolean isOf(net.minecraft.block.Block block);',
    'public boolean isIn(net.minecraft.registry.tag.TagKey<?> tag);',
    'public boolean isAir();',
    'public boolean isSolid();',
])
add('net.minecraft.block.SoundGroup', [], [
    'public net.minecraft.sound.SoundEvent getBreakSound();',
])
add('net.minecraft.block.BedBlock', ['net.minecraft.block.Block'], [])
add('net.minecraft.util.math.Direction.Type', [], [
    'public static final net.minecraft.util.math.Direction.Type HORIZONTAL;',
])
add('net.minecraft.util.math.MathHelper', [], [
    'public static float wrapDegrees(float value);',
])
extend('net.minecraft.world.World', [
    'public net.minecraft.entity.player.PlayerEntity getClosestPlayer(double x, double y, double z, double distance, java.util.function.Predicate<net.minecraft.entity.player.PlayerEntity> predicate);',
    'public net.minecraft.util.hit.HitResult raycast(net.minecraft.world.RaycastContext context);',
    'public boolean breakBlock(net.minecraft.util.math.BlockPos pos, boolean drop, net.minecraft.entity.Entity breaker);',
    'public boolean isChunkLoaded(net.minecraft.util.math.BlockPos pos);',
])
extend('net.minecraft.server.world.ServerWorld', [
    'public java.util.List<net.minecraft.server.network.ServerPlayerEntity> getPlayers();',
])
extend('net.minecraft.server.MinecraftServer', [
    'public java.lang.Iterable<net.minecraft.server.world.ServerWorld> getWorlds();',
    'public net.minecraft.server.world.ServerWorld getOverworld();',
    'public net.minecraft.server.world.ServerWorld getWorld(net.minecraft.registry.RegistryKey<?> key);',
])
extend('net.minecraft.sound.SoundEvents', [
    'public static final net.minecraft.sound.SoundEvent ENTITY_ITEM_PICKUP;',
    'public static final net.minecraft.sound.SoundEvent ENTITY_PLAYER_BURP;',
    'public static final net.minecraft.sound.SoundEvent BLOCK_ANVIL_USE;',
    'public static final net.minecraft.sound.SoundEvent BLOCK_STONE_PLACE;',
    'public static final net.minecraft.sound.SoundEvent ENTITY_ARROW_SHOOT;',
])
extend('net.minecraft.item.Items', [
    'public static final net.minecraft.item.Item OAK_PLANKS;',
    'public static final net.minecraft.item.Item CRAFTING_TABLE;',
    'public static final net.minecraft.item.Item FURNACE;',
    'public static final net.minecraft.item.Item STONE_SWORD;',
    'public static final net.minecraft.item.Item IRON_SWORD;',
    'public static final net.minecraft.item.Item IRON_HELMET;',
    'public static final net.minecraft.item.Item IRON_CHESTPLATE;',
    'public static final net.minecraft.item.Item IRON_LEGGINGS;',
    'public static final net.minecraft.item.Item IRON_BOOTS;',
    'public static final net.minecraft.item.Item BOW;',
    'public static final net.minecraft.item.Item ARROW;',
    'public static final net.minecraft.item.Item SHIELD;',
    'public static final net.minecraft.item.Item RED_BED;',
    'public static final net.minecraft.item.Item DIAMOND_SWORD;',
    'public static final net.minecraft.item.Item COBBLESTONE;',
    'public static final net.minecraft.item.Item COAL;',
    'public static final net.minecraft.item.Item RAW_IRON;',
    'public static final net.minecraft.item.Item DIAMOND;',
])
extend('net.minecraft.registry.Registries', [

])
extend('net.minecraft.nbt.NbtCompound', [
    'public java.util.Set<String> getKeys();',
    'public boolean containsUuid(String key);',
    'public java.util.UUID getUuid(String key);',
    'public void putUuid(String key, java.util.UUID value);',
    'public void putBoolean(String key, boolean value);',
    'public boolean getBoolean(String key);',
])
extend('net.minecraft.server.command.ServerCommandSource', [
    'public void sendError(net.minecraft.text.Text message);',
    'public net.minecraft.server.world.ServerWorld getWorld();',
    'public net.minecraft.server.MinecraftServer getServer();',
    'public net.minecraft.server.network.ServerPlayerEntity getPlayer();',
    'public boolean hasPermissionLevel(int level);',
    'public void sendFeedback(java.util.function.Supplier<net.minecraft.text.Text> message, boolean broadcast);',
])
extend('com.mojang.brigadier.context.CommandContext', [
    'public S getSource();',
]) if False else None
add('com.mojang.brigadier.context.CommandContext', [], [
    'public Object getSource();',
])
add('net.minecraft.client.render.entity.BipedEntityRenderer', ['net.minecraft.client.render.entity.LivingEntityRenderer'], [
    'public BipedEntityRenderer(net.minecraft.client.render.entity.EntityRendererFactory.Context context, net.minecraft.client.render.entity.model.BipedEntityModel model, float shadowRadius);',
])
add('net.minecraft.client.render.entity.LivingEntityRenderer', ['net.minecraft.client.render.entity.EntityRenderer'], [
    'public LivingEntityRenderer(net.minecraft.client.render.entity.EntityRendererFactory.Context ctx, net.minecraft.client.render.entity.model.EntityModel model, float shadowRadius);',
    'public boolean addFeature(net.minecraft.client.render.entity.feature.FeatureRenderer feature);',
])
add('net.minecraft.client.render.entity.feature.FeatureRendererContext', [], [], iface=True)
add('net.minecraft.client.render.entity.model.PlayerEntityModel', ['net.minecraft.client.render.entity.model.BipedEntityModel'], [
    'public PlayerEntityModel(net.minecraft.client.render.entity.model.ModelPart part);',
])
add('net.minecraft.client.render.entity.model.BipedEntityModel', ['net.minecraft.client.render.entity.model.EntityModel'], [
    'public BipedEntityModel(net.minecraft.client.render.entity.model.ModelPart part);',
])
add('net.minecraft.client.render.entity.model.EntityModel', [], [], iface=True)
add('net.minecraft.client.render.entity.feature.ArmorFeatureRenderer', ['net.minecraft.client.render.entity.feature.FeatureRenderer'], [
    'public ArmorFeatureRenderer(net.minecraft.client.render.entity.feature.FeatureRendererContext context, net.minecraft.client.render.entity.model.BipedEntityModel inner, net.minecraft.client.render.entity.model.BipedEntityModel outer, net.minecraft.client.render.model.BakedModelManager modelManager);',
])
add('net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer', ['net.minecraft.client.render.entity.feature.FeatureRenderer'], [
    'public HeldItemFeatureRenderer(net.minecraft.client.render.entity.feature.FeatureRendererContext context, net.minecraft.client.render.entity.HeldItemRenderer heldItemRenderer);',
])
extend('net.minecraft.client.render.entity.EntityRendererFactory.Context', [
    'public net.minecraft.client.render.entity.model.ModelPart getPart(net.minecraft.client.render.entity.model.EntityModelLayer layer);',
    'public net.minecraft.client.render.entity.HeldItemRenderer getHeldItemRenderer();',
    'public net.minecraft.client.render.model.BakedModelManager getModelManager();',
])

extend('net.minecraft.block.Blocks', [
    'public static final net.minecraft.block.Block BEDROCK;',
    'public static final net.minecraft.block.Block DEEPSLATE;',
    'public static final net.minecraft.block.Block DIAMOND_ORE;',
    'public static final net.minecraft.block.Block DEEPSLATE_DIAMOND_ORE;',
    'public static final net.minecraft.block.Block DEEPSLATE_IRON_ORE;',
    'public static final net.minecraft.block.Block DEEPSLATE_COAL_ORE;',
    'public static final net.minecraft.block.Block IRON_ORE;',
    'public static final net.minecraft.block.Block COAL_ORE;',
    'public static final net.minecraft.block.Block STONE;',
    'public static final net.minecraft.block.Block NETHER_PORTAL;',
    'public static final net.minecraft.block.Block WATER;',
])
add('net.minecraft.registry.tag.BlockTags', [], [
    'public static final net.minecraft.registry.tag.TagKey<net.minecraft.block.Block> LOGS;',
])
add('net.minecraft.entity.ItemEntity', ['net.minecraft.entity.Entity'], [
    'public net.minecraft.item.ItemStack getStack();',
    'public void setStack(net.minecraft.item.ItemStack stack);',
])

# ---- horror pass surface ----
extend('net.minecraft.entity.Entity', [
    'public net.minecraft.util.math.Vec3d getRotationVec(float tickDelta);',
    'public void sendMessage(net.minecraft.text.Text message);',
    'public net.minecraft.text.Text getName();',
])
extend('net.minecraft.entity.LivingEntity', [
    'public net.minecraft.util.math.Vec3d getEyePos();',
])
extend('net.minecraft.sound.SoundCategory', [
    'public static final net.minecraft.sound.SoundCategory HOSTILE;',
]) if 'net.minecraft.sound.SoundCategory' in ['.'.join(k) for k in CLASSES] else None
extend('net.minecraft.world.World', [
    'public long getTimeOfDay();',
])
add('net.minecraft.world.RaycastContext', [], [
    'public RaycastContext(net.minecraft.util.math.Vec3d start, net.minecraft.util.math.Vec3d end, net.minecraft.world.RaycastContext.ShapeType shapeType, net.minecraft.world.RaycastContext.FluidHandling fluid, net.minecraft.entity.Entity entity);',
])
add('net.minecraft.world.RaycastContext.ShapeType', [], [])
add('net.minecraft.world.RaycastContext.FluidHandling', [], [])

# ---- haunting behaviours surface ----
add('net.minecraft.state.property.BooleanProperty', ['net.minecraft.state.property.Property'], [])
add('net.minecraft.block.DoorBlock', ['net.minecraft.block.Block'], [
    'public static final net.minecraft.state.property.BooleanProperty OPEN;',
])
extend('net.minecraft.block.BlockState', [
    'public net.minecraft.block.BlockState with(net.minecraft.state.property.BooleanProperty property, boolean value);',
    'public boolean get(net.minecraft.state.property.BooleanProperty property);',
])
extend('net.minecraft.block.Blocks', [
    'public static final net.minecraft.block.Block OAK_DOOR;',
    'public static final net.minecraft.block.Block SPRUCE_DOOR;',
    'public static final net.minecraft.block.Block BIRCH_DOOR;',
    'public static final net.minecraft.block.Block TORCH;',
    'public static final net.minecraft.block.Block SOUL_TORCH;',
    'public static final net.minecraft.block.Block PAPER;',
])
extend('net.minecraft.world.World', [
    'public boolean setBlockState(net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState state);',
    'public boolean isNight();',
])
extend('net.minecraft.server.world.ServerWorld', [
    'public void setWeather(int clearTicks, int rainTicks, boolean raining, boolean thundering);',
])
extend('net.minecraft.entity.LivingEntity', [
    'public boolean isSleeping();',
])
extend('net.minecraft.sound.SoundCategory', [
    'public static final net.minecraft.sound.SoundCategory WEATHER;',
])
add('net.minecraft.component.type.LoreComponent', [], [
    'public LoreComponent(java.util.List<net.minecraft.text.Text> lines);',
])
extend('net.minecraft.component.DataComponentTypes', [
    'public static final net.minecraft.component.ComponentType<net.minecraft.text.Text> CUSTOM_NAME;',
    'public static final net.minecraft.component.ComponentType<net.minecraft.component.type.LoreComponent> LORE;',
])
extend('net.minecraft.item.ItemStack', [
    'public <T> void set(net.minecraft.component.ComponentType<T> type, T value);',
])
extend('net.minecraft.item.Items', [
    'public static final net.minecraft.item.Item PAPER;',
])
extend('net.minecraft.server.MinecraftServer', [
    'public net.minecraft.server.PlayerManager getPlayerManager();',
])
add('net.minecraft.server.PlayerManager', [], [
    'public net.minecraft.server.network.ServerPlayerEntity getPlayer(java.util.UUID uuid);',
])
add('net.minecraft.util.math.BlockPosIterator', [], [])
extend('net.minecraft.util.math.BlockPos', [
    'public static java.lang.Iterable<net.minecraft.util.math.BlockPos> iterate(net.minecraft.util.math.BlockPos min, net.minecraft.util.math.BlockPos max);',
])
add('net.fabricmc.fabric.api.event.Event', [], [
    'public void register(java.lang.Object listener);',
])
add('net.minecraft.network.message.SignedMessage', [], [
    'public String getContent();',
])
add('net.fabricmc.fabric.api.message.v1.ServerMessageEvents', [], [
    'public static final net.fabricmc.fabric.api.event.Event CHAT_MESSAGE;',
])
add('net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents', [], [
    'public static final net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AfterDeath AFTER_DEATH;',
], iface=True)
add('net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AfterDeath', [], [
    'public void register(java.lang.Object listener);',
], iface=True)

# ---- client dread layer surface ----
add('net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents', [], [
    'public static final net.fabricmc.fabric.api.event.Event END_CLIENT_TICK;',
], iface=True)
add('net.minecraft.client.render.RenderTickCounter', [], [], iface=True)
add('net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback', [], [
    'public static final net.fabricmc.fabric.api.event.Event EVENT;',
], iface=True)
add('net.minecraft.client.MinecraftClient', [], [
    'public static net.minecraft.client.MinecraftClient getInstance();',
    'public net.minecraft.client.network.ClientPlayerEntity getPlayer();',
    'public net.minecraft.client.world.ClientWorld getWorld();',
    'public net.minecraft.client.util.Window getWindow();',
])
add('net.minecraft.client.util.Window', [], [
    'public int getScaledWidth();',
    'public int getScaledHeight();',
])
add('net.minecraft.client.gui.DrawContext', [], [
    'public void fill(int x0, int y0, int x1, int y1, int color);',
])
add('net.minecraft.client.util.SkinTextures', [], [
    'public net.minecraft.util.Identifier texture();',
])
extend('net.minecraft.client.network.AbstractClientPlayerEntity', [
    'public net.minecraft.client.util.SkinTextures getSkinTextures();',
]) if ('net.minecraft.client.network', 'AbstractClientPlayerEntity') in CLASSES else add('net.minecraft.client.network.AbstractClientPlayerEntity', ['net.minecraft.entity.player.PlayerEntity'], [
    'public net.minecraft.client.util.SkinTextures getSkinTextures();',
])
extend('net.minecraft.entity.Entity', [
    'public void playSound(net.minecraft.sound.SoundEvent sound, float volume, float pitch);',
    'public net.minecraft.util.math.random.Random getRandom();',
])
extend('net.minecraft.client.render.entity.LivingEntityRenderer', [
    'public boolean shouldRenderName(net.minecraft.entity.Entity entity);',
])

add('net.minecraft.client.world.ClientWorld', ['net.minecraft.world.World'], [])
