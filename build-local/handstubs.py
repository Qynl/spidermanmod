#!/usr/bin/env python3
"""Write hand-authored javac stubs for generic/non-MC/third-party API.

Covers what stubgen.py cannot express: generics (EntityType, Registry, brigadier
builders, CustomPayload), fabric-api (shapes copied from fabric 0.105.0+1.21.1
tag sources, so erased descriptors match the real classes exactly), mixin
annotations, and stable third-party APIs (gson, slf4j, glfw, joml, netty,
authlib, brigadier).

Rules: NO `final` on fields (prevents javac constant inlining). Erased
signatures must match reality (these classes are NOT remapped).
"""
import os

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(REPO_ROOT, "build-local", "gen-stubs")

STUBS = {}

def stub(path, content):
    STUBS[path] = content

# ---------------------------------------------------------------- MC: text ---
stub("net/minecraft/text/StringVisitable.java", """package net.minecraft.text;

public interface StringVisitable {
}
""")

stub("net/minecraft/text/OrderedText.java", """package net.minecraft.text;

public interface OrderedText {
}
""")

# Descs verified in tiny: literal/translatable/empty.
stub("net/minecraft/text/Text.java", """package net.minecraft.text;

public interface Text extends StringVisitable {
    static MutableText literal(String s) { return null; }
    static MutableText translatable(String key) { return null; }
    static MutableText translatable(String key, Object[] args) { return null; }
    static MutableText empty() { return null; }
}
""")

# Descs verified in tiny: append/append/formatted/formatted.
stub("net/minecraft/text/MutableText.java", """package net.minecraft.text;

public interface MutableText extends Text {
    MutableText append(Text t);
    MutableText append(String s);
    MutableText formatted(net.minecraft.util.Formatting f);
    MutableText formatted(net.minecraft.util.Formatting[] f);
}
""")

# ------------------------------------------------------------- MC: registry --
stub("net/minecraft/registry/entry/RegistryEntry.java", """package net.minecraft.registry.entry;

public interface RegistryEntry<T> {
    T value();

    public interface Reference<T> extends RegistryEntry<T> {
    }
}
""")

stub("net/minecraft/registry/RegistryKey.java", """package net.minecraft.registry;

public class RegistryKey<T> {
}
""")

# Real: public static <T> T register(Registry<T>, Identifier, T). Erasure matches.
stub("net/minecraft/registry/DefaultedRegistry.java", """package net.minecraft.registry;

public class DefaultedRegistry<T> extends Registry<T> {
}
""")

stub("net/minecraft/registry/Registry.java", """package net.minecraft.registry;

public class Registry<T> {
    public static <V, T extends V> T register(Registry<V> registry, net.minecraft.util.Identifier id, T entry) { return null; }
}
""")

stub("net/minecraft/registry/Registries.java", """package net.minecraft.registry;

public class Registries {
    public static DefaultedRegistry<net.minecraft.entity.EntityType<?>> ENTITY_TYPE;
    public static Registry<net.minecraft.sound.SoundEvent> SOUND_EVENT;
}
""")

# ----------------------------------------------------------- MC: entity type -
# Shape from 1.21.1 refs (Unicopia Builder.create/dimensions/build(String)).
stub("net/minecraft/entity/EntityType.java", """package net.minecraft.entity;

public class EntityType<T extends Entity> {
    public float getWidth() { return 0.0f; }
    public float getHeight() { return 0.0f; }

    public interface EntityFactory<T extends Entity> {
        T create(EntityType<T> type, net.minecraft.world.World world);
    }

    public static class Builder<T extends Entity> {
        public static <T extends Entity> Builder<T> create(EntityFactory<T> factory, SpawnGroup group) { return null; }
        public Builder<T> dimensions(float width, float height) { return this; }
        public Builder<T> maxTrackingRange(int range) { return this; }
        public Builder<T> trackingTickInterval(int interval) { return this; }
        public EntityType<T> build(String id) { return null; }
    }
}
""")

# -------------------------------------------------------------- MC: packets --
stub("net/minecraft/network/PacketByteBuf.java", """package net.minecraft.network;

public class PacketByteBuf extends io.netty.buffer.ByteBuf {
    public java.util.UUID readUuid() { return null; }
    public PacketByteBuf writeUuid(java.util.UUID u) { return this; }
    public int readVarInt() { return 0; }
    public PacketByteBuf writeVarInt(int i) { return this; }
    public int readInt() { return 0; }
    public PacketByteBuf writeInt(int i) { return this; }
    public long readLong() { return 0L; }
    public PacketByteBuf writeLong(long l) { return this; }
    public float readFloat() { return 0.0f; }
    public PacketByteBuf writeFloat(float f) { return this; }
    public double readDouble() { return 0.0; }
    public PacketByteBuf writeDouble(double d) { return this; }
    public boolean readBoolean() { return false; }
    public PacketByteBuf writeBoolean(boolean b) { return this; }
    public net.minecraft.util.math.BlockPos readBlockPos() { return null; }
    public PacketByteBuf writeBlockPos(net.minecraft.util.math.BlockPos p) { return this; }
}
""")

stub("net/minecraft/network/RegistryByteBuf.java", """package net.minecraft.network;

public class RegistryByteBuf extends PacketByteBuf {
}
""")

# Erased descs verified in tiny: decode(Object)Object, encode(Object,Object)void.
stub("net/minecraft/network/codec/PacketDecoder.java", """package net.minecraft.network.codec;

public interface PacketDecoder<B, V> {
    V decode(B buf);
}
""")

stub("net/minecraft/network/codec/ValueFirstEncoder.java", """package net.minecraft.network.codec;

public interface ValueFirstEncoder<B, V> {
    void encode(V value, B buf);
}
""")

stub("net/minecraft/network/codec/PacketCodec.java", """package net.minecraft.network.codec;

public interface PacketCodec<B, V> {
    V decode(B buf);
    void encode(B buf, V value);
}
""")

# codecOf/getId/Id.id() descs verified in tiny; record shape from vanilla.
stub("net/minecraft/network/packet/CustomPayload.java", """package net.minecraft.network.packet;

public interface CustomPayload {
    Id<? extends CustomPayload> getId();

    static <B extends net.minecraft.network.PacketByteBuf, T extends CustomPayload> net.minecraft.network.codec.PacketCodec<B, T> codecOf(
            net.minecraft.network.codec.ValueFirstEncoder<B, T> encoder,
            net.minecraft.network.codec.PacketDecoder<B, T> decoder) { return null; }

    public record Id<T extends CustomPayload>(net.minecraft.util.Identifier id) {
    }

    public static class Type<B, T extends CustomPayload> {
    }
}
""")

# ------------------------------------------------------------- MC: rendering -
stub("net/minecraft/client/render/entity/EntityRenderer.java", """package net.minecraft.client.render.entity;

public class EntityRenderer<T extends net.minecraft.entity.Entity> {
    protected EntityRenderer(EntityRendererFactory.Context context) {}
    public net.minecraft.util.Identifier getTexture(T entity) { return null; }
    public void render(T entity, float yaw, float tickDelta,
            net.minecraft.client.util.math.MatrixStack matrices,
            net.minecraft.client.render.VertexConsumerProvider vertexConsumers, int light) {}
}
""")

stub("net/minecraft/client/render/entity/SpiderEntityRenderer.java", """package net.minecraft.client.render.entity;

public class SpiderEntityRenderer extends EntityRenderer<net.minecraft.entity.mob.SpiderEntity> {
    public SpiderEntityRenderer(EntityRendererFactory.Context context) { super(context); }
}
""")

stub("net/minecraft/client/render/entity/EntityRendererFactory.java", """package net.minecraft.client.render.entity;

public interface EntityRendererFactory<T extends net.minecraft.entity.Entity> {
    EntityRenderer<T> create(Context context);

    public static class Context {
    }
}
""")

stub("net/minecraft/client/network/ClientPlayNetworkHandler.java", """package net.minecraft.client.network;

public class ClientPlayNetworkHandler {
}
""")

stub("net/minecraft/server/network/ServerPlayNetworkHandler.java", """package net.minecraft.server.network;

public class ServerPlayNetworkHandler {
    public net.minecraft.server.network.ServerPlayerEntity player;
    public void disconnect(net.minecraft.text.Text reason) {}
}
""")

stub("net/minecraft/command/argument/EntityArgumentType.java", """package net.minecraft.command.argument;

public class EntityArgumentType implements com.mojang.brigadier.arguments.ArgumentType {
    public static EntityArgumentType player() { return null; }
    public static net.minecraft.server.network.ServerPlayerEntity getPlayer(
            com.mojang.brigadier.context.CommandContext context, String name)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException { return null; }
}
""")

stub("net/minecraft/command/CommandRegistryAccess.java", """package net.minecraft.command;

public class CommandRegistryAccess {
}
""")

# ------------------------------------------------------------------- fabric --
stub("net/fabricmc/api/ModInitializer.java", """package net.fabricmc.api;

public interface ModInitializer {
    void onInitialize();
}
""")

stub("net/fabricmc/api/ClientModInitializer.java", """package net.fabricmc.api;

public interface ClientModInitializer {
    void onInitializeClient();
}
""")

stub("net/fabricmc/fabric/api/event/Event.java", """package net.fabricmc.fabric.api.event;

public class Event<T> {
    public void register(T listener) {}
}
""")

# fabric-loader (hand: tiny surface used by config).
stub("net/fabricmc/loader/api/FabricLoader.java", """package net.fabricmc.loader.api;

public class FabricLoader {
    public static FabricLoader getInstance() { return null; }
    public java.nio.file.Path getConfigDir() { return null; }
}
""")

# Copied from fabric tag 0.105.0+1.21.1 (same erasure as real).
stub("net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking.java", """package net.fabricmc.fabric.api.networking.v1;

public class ServerPlayNetworking {
    public static <T extends net.minecraft.network.packet.CustomPayload> boolean registerGlobalReceiver(
            net.minecraft.network.packet.CustomPayload.Id<T> type, PlayPayloadHandler<T> handler) { return false; }
    public static void send(net.minecraft.server.network.ServerPlayerEntity player,
            net.minecraft.network.packet.CustomPayload payload) {}

    public interface PlayPayloadHandler<T extends net.minecraft.network.packet.CustomPayload> {
        void receive(T payload, Context context);
    }

    public interface Context {
        net.minecraft.server.MinecraftServer server();
        net.minecraft.server.network.ServerPlayerEntity player();
        PacketSender responseSender();
    }
}
""")

stub("net/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking.java", """package net.fabricmc.fabric.api.client.networking.v1;

public class ClientPlayNetworking {
    public static <T extends net.minecraft.network.packet.CustomPayload> boolean registerGlobalReceiver(
            net.minecraft.network.packet.CustomPayload.Id<T> type, PlayPayloadHandler<T> handler) { return false; }
    public static void send(net.minecraft.network.packet.CustomPayload payload) {}

    public interface PlayPayloadHandler<T extends net.minecraft.network.packet.CustomPayload> {
        void receive(T payload, Context context);
    }

    public interface Context {
        net.minecraft.client.MinecraftClient client();
        net.minecraft.client.network.ClientPlayerEntity player();
        net.fabricmc.fabric.api.networking.v1.PacketSender responseSender();
    }
}
""")

stub("net/fabricmc/fabric/api/networking/v1/PayloadTypeRegistry.java", """package net.fabricmc.fabric.api.networking.v1;

public interface PayloadTypeRegistry<B extends net.minecraft.network.PacketByteBuf> {
    static PayloadTypeRegistry<net.minecraft.network.PacketByteBuf> playC2S() { return null; }
    static PayloadTypeRegistry<net.minecraft.network.RegistryByteBuf> playS2C() { return null; }

    <T extends net.minecraft.network.packet.CustomPayload> net.minecraft.network.packet.CustomPayload.Type<? super B, T> register(
            net.minecraft.network.packet.CustomPayload.Id<T> id,
            net.minecraft.network.codec.PacketCodec<? super B, T> codec);
}
""")

stub("net/fabricmc/fabric/api/networking/v1/PacketSender.java", """package net.fabricmc.fabric.api.networking.v1;

public interface PacketSender {
}
""")

stub("net/fabricmc/fabric/api/networking/v1/ServerPlayConnectionEvents.java", """package net.fabricmc.fabric.api.networking.v1;

public class ServerPlayConnectionEvents {
    public static net.fabricmc.fabric.api.event.Event<Init> INIT;
    public static net.fabricmc.fabric.api.event.Event<Join> JOIN;
    public static net.fabricmc.fabric.api.event.Event<Disconnect> DISCONNECT;

    public interface Init {
        void onPlayInit(net.minecraft.server.network.ServerPlayNetworkHandler handler,
                net.minecraft.server.MinecraftServer server);
    }

    public interface Join {
        void onPlayReady(net.minecraft.server.network.ServerPlayNetworkHandler handler, PacketSender sender,
                net.minecraft.server.MinecraftServer server);
    }

    public interface Disconnect {
        void onPlayDisconnect(net.minecraft.server.network.ServerPlayNetworkHandler handler,
                net.minecraft.server.MinecraftServer server);
    }
}
""")

stub("net/fabricmc/fabric/api/client/networking/v1/ClientPlayConnectionEvents.java", """package net.fabricmc.fabric.api.client.networking.v1;

public class ClientPlayConnectionEvents {
    public static net.fabricmc.fabric.api.event.Event<Init> INIT;
    public static net.fabricmc.fabric.api.event.Event<Join> JOIN;
    public static net.fabricmc.fabric.api.event.Event<Disconnect> DISCONNECT;

    public interface Init {
        void onPlayInit(net.minecraft.client.network.ClientPlayNetworkHandler handler,
                net.minecraft.client.MinecraftClient client);
    }

    public interface Join {
        void onPlayReady(net.minecraft.client.network.ClientPlayNetworkHandler handler,
                net.fabricmc.fabric.api.networking.v1.PacketSender sender,
                net.minecraft.client.MinecraftClient client);
    }

    public interface Disconnect {
        void onPlayDisconnect(net.minecraft.client.network.ClientPlayNetworkHandler handler,
                net.minecraft.client.MinecraftClient client);
    }
}
""")

stub("net/fabricmc/fabric/api/event/lifecycle/v1/ServerTickEvents.java", """package net.fabricmc.fabric.api.event.lifecycle.v1;

public class ServerTickEvents {
    public static net.fabricmc.fabric.api.event.Event<EndTick> END_SERVER_TICK;

    public interface EndTick {
        void onEndTick(net.minecraft.server.MinecraftServer server);
    }
}
""")

stub("net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents.java", """package net.fabricmc.fabric.api.client.event.lifecycle.v1;

public class ClientTickEvents {
    public static net.fabricmc.fabric.api.event.Event<EndTick> END_CLIENT_TICK;

    public interface EndTick {
        void onEndTick(net.minecraft.client.MinecraftClient client);
    }
}
""")

stub("net/fabricmc/fabric/api/event/player/UseBlockCallback.java", """package net.fabricmc.fabric.api.event.player;

public interface UseBlockCallback {
    net.fabricmc.fabric.api.event.Event<UseBlockCallback> EVENT = new net.fabricmc.fabric.api.event.Event<>();

    net.minecraft.util.ActionResult interact(net.minecraft.entity.player.PlayerEntity player,
            net.minecraft.world.World world, net.minecraft.util.Hand hand,
            net.minecraft.util.hit.BlockHitResult hitResult);
}
""")

stub("net/fabricmc/fabric/api/event/player/AttackBlockCallback.java", """package net.fabricmc.fabric.api.event.player;

public interface AttackBlockCallback {
    net.fabricmc.fabric.api.event.Event<AttackBlockCallback> EVENT = new net.fabricmc.fabric.api.event.Event<>();

    net.minecraft.util.ActionResult interact(net.minecraft.entity.player.PlayerEntity player,
            net.minecraft.world.World world, net.minecraft.util.Hand hand,
            net.minecraft.util.math.BlockPos pos, net.minecraft.util.math.Direction direction);
}
""")

stub("net/fabricmc/fabric/api/event/player/UseEntityCallback.java", """package net.fabricmc.fabric.api.event.player;

public interface UseEntityCallback {
    net.fabricmc.fabric.api.event.Event<UseEntityCallback> EVENT = new net.fabricmc.fabric.api.event.Event<>();

    net.minecraft.util.ActionResult interact(net.minecraft.entity.player.PlayerEntity player,
            net.minecraft.world.World world, net.minecraft.util.Hand hand,
            net.minecraft.entity.Entity entity, net.minecraft.util.hit.EntityHitResult hitResult);
}
""")

stub("net/fabricmc/fabric/api/event/player/AttackEntityCallback.java", """package net.fabricmc.fabric.api.event.player;

public interface AttackEntityCallback {
    net.fabricmc.fabric.api.event.Event<AttackEntityCallback> EVENT = new net.fabricmc.fabric.api.event.Event<>();

    net.minecraft.util.ActionResult interact(net.minecraft.entity.player.PlayerEntity player,
            net.minecraft.world.World world, net.minecraft.util.Hand hand,
            net.minecraft.entity.Entity entity, net.minecraft.util.hit.EntityHitResult hitResult);
}
""")

stub("net/fabricmc/fabric/api/client/rendering/v1/WorldRenderEvents.java", """package net.fabricmc.fabric.api.client.rendering.v1;

public class WorldRenderEvents {
    public static net.fabricmc.fabric.api.event.Event<AfterEntities> AFTER_ENTITIES;
    public static net.fabricmc.fabric.api.event.Event<AfterTranslucent> AFTER_TRANSLUCENT;

    public interface AfterEntities {
        void afterEntities(WorldRenderContext context);
    }

    public interface AfterTranslucent {
        void afterTranslucent(WorldRenderContext context);
    }
}
""")

stub("net/fabricmc/fabric/api/client/rendering/v1/WorldRenderContext.java", """package net.fabricmc.fabric.api.client.rendering.v1;

public interface WorldRenderContext {
    net.minecraft.client.util.math.MatrixStack matrixStack();
    net.minecraft.client.render.RenderTickCounter tickCounter();
    net.minecraft.client.render.Camera camera();
    net.minecraft.client.render.VertexConsumerProvider consumers();
    net.minecraft.client.world.ClientWorld world();
    net.minecraft.client.render.GameRenderer gameRenderer();
}
""")

stub("net/fabricmc/fabric/api/client/rendering/v1/HudRenderCallback.java", """package net.fabricmc.fabric.api.client.rendering.v1;

public interface HudRenderCallback {
    net.fabricmc.fabric.api.event.Event<HudRenderCallback> EVENT = new net.fabricmc.fabric.api.event.Event<>();

    void onHudRender(net.minecraft.client.gui.DrawContext drawContext,
            net.minecraft.client.render.RenderTickCounter tickCounter);
}
""")

stub("net/fabricmc/fabric/api/client/rendering/v1/EntityRendererRegistry.java", """package net.fabricmc.fabric.api.client.rendering.v1;

public class EntityRendererRegistry {
    public static <E extends net.minecraft.entity.Entity> void register(
            net.minecraft.entity.EntityType<? extends E> entityType,
            net.minecraft.client.render.entity.EntityRendererFactory<E> entityRendererFactory) {}
}
""")

stub("net/fabricmc/fabric/api/command/v2/CommandRegistrationCallback.java", """package net.fabricmc.fabric.api.command.v2;

public interface CommandRegistrationCallback {
    net.fabricmc.fabric.api.event.Event<CommandRegistrationCallback> EVENT = new net.fabricmc.fabric.api.event.Event<>();

    void register(com.mojang.brigadier.CommandDispatcher<net.minecraft.server.command.ServerCommandSource> dispatcher,
            net.minecraft.command.CommandRegistryAccess registryAccess,
            net.minecraft.server.command.CommandManager.RegistrationEnvironment environment);
}
""")

stub("net/fabricmc/fabric/api/client/keybinding/v1/KeyBindingHelper.java", """package net.fabricmc.fabric.api.client.keybinding.v1;

public class KeyBindingHelper {
    public static net.minecraft.client.option.KeyBinding registerKeyBinding(
            net.minecraft.client.option.KeyBinding keyBinding) { return null; }
}
""")

stub("net/fabricmc/fabric/api/biome/v1/BiomeSelectionContext.java", """package net.fabricmc.fabric.api.biome.v1;

public class BiomeSelectionContext {
}
""")

stub("net/fabricmc/fabric/api/biome/v1/BiomeSelectors.java", """package net.fabricmc.fabric.api.biome.v1;

public class BiomeSelectors {
    public static java.util.function.Predicate<BiomeSelectionContext> foundInOverworld() { return null; }
}
""")

stub("net/fabricmc/fabric/api/biome/v1/BiomeModifications.java", """package net.fabricmc.fabric.api.biome.v1;

public class BiomeModifications {
    public static void addSpawn(java.util.function.Predicate<BiomeSelectionContext> biomeSelector,
            net.minecraft.entity.SpawnGroup spawnGroup, net.minecraft.entity.EntityType<?> entityType,
            int weight, int minGroupSize, int maxGroupSize) {}
}
""")

stub("net/fabricmc/fabric/api/object/builder/v1/entity/FabricDefaultAttributeRegistry.java", """package net.fabricmc.fabric.api.object.builder.v1.entity;

public class FabricDefaultAttributeRegistry {
    public static void register(net.minecraft.entity.EntityType<? extends net.minecraft.entity.LivingEntity> type,
            net.minecraft.entity.attribute.DefaultAttributeContainer.Builder builder) {}
    public static void register(net.minecraft.entity.EntityType<? extends net.minecraft.entity.LivingEntity> type,
            net.minecraft.entity.attribute.DefaultAttributeContainer container) {}
}
""")

# -------------------------------------------------------------------- mixin --
stub("org/spongepowered/asm/mixin/Mixin.java", """package org.spongepowered.asm.mixin;

public @interface Mixin {
    Class<?>[] value() default {};
    String[] targets() default {};
}
""")

stub("org/spongepowered/asm/mixin/injection/Inject.java", """package org.spongepowered.asm.mixin.injection;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Inject {
    String[] method() default {};
    At[] at() default {};
    boolean cancellable() default false;
}
""")

stub("org/spongepowered/asm/mixin/injection/At.java", """package org.spongepowered.asm.mixin.injection;

public @interface At {
    String value() default "";
    String target() default "";
    int ordinal() default -1;
}
""")

stub("org/spongepowered/asm/mixin/Shadow.java", """package org.spongepowered.asm.mixin;

public @interface Shadow {
}
""")

stub("org/spongepowered/asm/mixin/Unique.java", """package org.spongepowered.asm.mixin;

public @interface Unique {
}
""")

stub("org/spongepowered/asm/mixin/injection/callback/CallbackInfo.java", """package org.spongepowered.asm.mixin.injection.callback;

public class CallbackInfo {
    public void cancel() {}
    public boolean isCancelled() { return false; }
}
""")

stub("org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable.java", """package org.spongepowered.asm.mixin.injection.callback;

public class CallbackInfoReturnable<R> extends CallbackInfo {
    public R getReturnValue() { return null; }
    public void setReturnValue(R value) {}
}
""")

# ---------------------------------------------------------------- brigadier --
# Generics mirror real brigadier so chaining typechecks and erasure matches.
stub("com/mojang/brigadier/builder/ArgumentBuilder.java", """package com.mojang.brigadier.builder;

public class ArgumentBuilder<S, T extends ArgumentBuilder<S, T>> {
    public T then(ArgumentBuilder<S, ?> argument) { return null; }
    public T executes(com.mojang.brigadier.Command<S> command) { return null; }
    public T requires(java.util.function.Predicate<S> requirement) { return null; }
}
""")

stub("com/mojang/brigadier/builder/LiteralArgumentBuilder.java", """package com.mojang.brigadier.builder;

public class LiteralArgumentBuilder<S> extends ArgumentBuilder<S, LiteralArgumentBuilder<S>> {
}
""")

stub("com/mojang/brigadier/builder/RequiredArgumentBuilder.java", """package com.mojang.brigadier.builder;

public class RequiredArgumentBuilder<S, T> extends ArgumentBuilder<S, RequiredArgumentBuilder<S, T>> {
}
""")

stub("com/mojang/brigadier/CommandDispatcher.java", """package com.mojang.brigadier;

public class CommandDispatcher<S> {
    public com.mojang.brigadier.tree.LiteralCommandNode<S> register(
            com.mojang.brigadier.builder.LiteralArgumentBuilder<S> command) { return null; }
}
""")

stub("com/mojang/brigadier/Command.java", """package com.mojang.brigadier;

public interface Command<S> {
    int SINGLE_SUCCESS = 1;

    int run(com.mojang.brigadier.context.CommandContext<S> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException;
}
""")

stub("com/mojang/brigadier/context/CommandContext.java", """package com.mojang.brigadier.context;

public class CommandContext<S> {
    public S getSource() { return null; }
    public <V> V getArgument(String name, Class<V> clazz)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException { return null; }
}
""")

stub("com/mojang/brigadier/exceptions/CommandSyntaxException.java", """package com.mojang.brigadier.exceptions;

public class CommandSyntaxException extends Exception {
    public CommandSyntaxException() { super(); }
}
""")

stub("com/mojang/brigadier/arguments/ArgumentType.java", """package com.mojang.brigadier.arguments;

public interface ArgumentType<T> {
}
""")

stub("com/mojang/brigadier/arguments/IntegerArgumentType.java", """package com.mojang.brigadier.arguments;

public class IntegerArgumentType implements ArgumentType<Integer> {
    public static IntegerArgumentType integer() { return null; }
    public static IntegerArgumentType integer(int min, int max) { return null; }
    public static int getInteger(com.mojang.brigadier.context.CommandContext<?> context, String name) { return 0; }
}
""")

stub("com/mojang/brigadier/arguments/StringArgumentType.java", """package com.mojang.brigadier.arguments;

public class StringArgumentType implements ArgumentType<String> {
    public static StringArgumentType string() { return null; }
    public static String getString(com.mojang.brigadier.context.CommandContext<?> context, String name) { return null; }
}
""")

stub("com/mojang/brigadier/tree/CommandNode.java", """package com.mojang.brigadier.tree;

public class CommandNode<S> {
}
""")

stub("com/mojang/brigadier/tree/LiteralCommandNode.java", """package com.mojang.brigadier.tree;

public class LiteralCommandNode<S> extends CommandNode<S> {
}
""")

# --------------------------------------------------------------------- gson --
stub("com/google/gson/GsonBuilder.java", """package com.google.gson;

public class GsonBuilder {
    public GsonBuilder setPrettyPrinting() { return this; }
    public Gson create() { return null; }
}
""")

stub("com/google/gson/Gson.java", """package com.google.gson;

public class Gson {
    public String toJson(Object src) { return null; }
    public <T> T fromJson(String json, Class<T> classOfT) { return null; }
}
""")

stub("com/google/gson/JsonObject.java", """package com.google.gson;

public class JsonObject extends JsonElement {
    public void add(String property, JsonElement value) {}
    public JsonElement get(String memberName) { return null; }
    public boolean has(String memberName) { return false; }
    public void addProperty(String property, String value) {}
    public void addProperty(String property, Number value) {}
    public void addProperty(String property, Boolean value) {}
}
""")

stub("com/google/gson/JsonElement.java", """package com.google.gson;

public class JsonElement {
    public boolean isJsonNull() { return false; }
    public int getAsInt() { return 0; }
    public long getAsLong() { return 0L; }
    public float getAsFloat() { return 0.0f; }
    public double getAsDouble() { return 0.0; }
    public String getAsString() { return null; }
    public boolean getAsBoolean() { return false; }
}
""")

stub("com/google/gson/JsonParser.java", """package com.google.gson;

public class JsonParser {
    public static JsonElement parseString(String json) { return null; }
}
""")

# -------------------------------------------------------------------- slf4j --
stub("org/slf4j/Logger.java", """package org.slf4j;

public interface Logger {
    void info(String msg);
    void info(String format, Object... args);
    void warn(String msg);
    void warn(String format, Object... args);
    void error(String msg);
    void error(String format, Object... args);
    void debug(String msg);
    void debug(String format, Object... args);
}
""")

stub("org/slf4j/LoggerFactory.java", """package org.slf4j;

public class LoggerFactory {
    public static Logger getLogger(Class<?> clazz) { return null; }
    public static Logger getLogger(String name) { return null; }
}
""")

# --------------------------------------------------------------------- glfw --
# Real fields are static final ints; stub uses non-final so javac emits getstatic.
stub("org/lwjgl/glfw/GLFW.java", """package org.lwjgl.glfw;

public class GLFW {
    public static final int GLFW_KEY_UNKNOWN = -1;
    public static final int GLFW_KEY_SPACE = 32;
    public static final int GLFW_KEY_A = 65;
    public static final int GLFW_KEY_B = 66;
    public static final int GLFW_KEY_C = 67;
    public static final int GLFW_KEY_D = 68;
    public static final int GLFW_KEY_E = 69;
    public static final int GLFW_KEY_F = 70;
    public static final int GLFW_KEY_G = 71;
    public static final int GLFW_KEY_0 = 48;
    public static final int GLFW_KEY_1 = 49;
    public static final int GLFW_KEY_2 = 50;
    public static final int GLFW_KEY_3 = 51;
    public static final int GLFW_KEY_4 = 52;
    public static final int GLFW_KEY_5 = 53;
    public static final int GLFW_KEY_6 = 54;
    public static final int GLFW_KEY_7 = 55;
    public static final int GLFW_KEY_8 = 56;
    public static final int GLFW_KEY_9 = 57;
    public static final int GLFW_KEY_H = 72;
    public static final int GLFW_KEY_I = 73;
    public static final int GLFW_KEY_J = 74;
    public static final int GLFW_KEY_K = 75;
    public static final int GLFW_KEY_L = 76;
    public static final int GLFW_KEY_M = 77;
    public static final int GLFW_KEY_N = 78;
    public static final int GLFW_KEY_O = 79;
    public static final int GLFW_KEY_P = 80;
    public static final int GLFW_KEY_Q = 81;
    public static final int GLFW_KEY_R = 82;
    public static final int GLFW_KEY_S = 83;
    public static final int GLFW_KEY_T = 84;
    public static final int GLFW_KEY_U = 85;
    public static final int GLFW_KEY_V = 86;
    public static final int GLFW_KEY_W = 87;
    public static final int GLFW_KEY_X = 88;
    public static final int GLFW_KEY_Y = 89;
    public static final int GLFW_KEY_Z = 90;
    public static final int GLFW_KEY_UP = 265;
    public static final int GLFW_KEY_DOWN = 264;
    public static final int GLFW_KEY_LEFT = 263;
    public static final int GLFW_KEY_RIGHT = 262;
    public static final int GLFW_KEY_ESCAPE = 256;
    public static final int GLFW_KEY_ENTER = 257;
    public static final int GLFW_KEY_TAB = 258;
    public static final int GLFW_KEY_LEFT_SHIFT = 340;
    public static final int GLFW_KEY_LEFT_CONTROL = 341;
    public static final int GLFW_KEY_LEFT_ALT = 342;
    public static final int GLFW_KEY_RIGHT_SHIFT = 344;
    public static final int GLFW_KEY_RIGHT_CONTROL = 345;
    public static final int GLFW_KEY_RIGHT_ALT = 346;
    public static final int GLFW_MOUSE_BUTTON_1 = 0;
    public static final int GLFW_MOUSE_BUTTON_2 = 1;
    public static final int GLFW_MOUSE_BUTTON_3 = 2;
    public static final int GLFW_MOUSE_BUTTON_LEFT = 0;
    public static final int GLFW_MOUSE_BUTTON_MIDDLE = 2;
    public static final int GLFW_MOUSE_BUTTON_RIGHT = 1;
}
""")

# --------------------------------------------------------------------- misc --
stub("org/joml/Quaternionf.java", """package org.joml;

public class Quaternionf {
}
""")

stub("io/netty/buffer/ByteBuf.java", """package io.netty.buffer;

public class ByteBuf {
    public boolean readBoolean() { return false; }
    public ByteBuf writeBoolean(boolean b) { return this; }
    public int readInt() { return 0; }
    public ByteBuf writeInt(int i) { return this; }
    public long readLong() { return 0L; }
    public ByteBuf writeLong(long l) { return this; }
    public float readFloat() { return 0.0f; }
    public ByteBuf writeFloat(float f) { return this; }
    public double readDouble() { return 0.0; }
    public ByteBuf writeDouble(double d) { return this; }
    public int readableBytes() { return 0; }
}
""")

stub("com/mojang/authlib/GameProfile.java", """package com.mojang.authlib;

public class GameProfile {
    public java.util.UUID getId() { return null; }
    public String getName() { return null; }
}
""")


def main():
    n = 0
    for path, content in sorted(STUBS.items()):
        full = os.path.join(OUT_DIR, *path.split("/"))
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w", encoding="utf-8") as f:
            f.write(content)
        n += 1
    print(f"[handstubs] wrote {n} stub files -> {OUT_DIR}")


if __name__ == "__main__":
    main()
