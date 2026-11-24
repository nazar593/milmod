package dev.milmod.network;

import dev.milmod.MilMod;
import dev.milmod.client.BlastEffects;
import dev.milmod.client.DistantEngineAudio;
import dev.milmod.model.VehicleProfile;
import dev.milmod.client.HeavyExplosionClient;
import dev.milmod.client.SmokeClouds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.PacketDistributor;
import dev.milmod.entity.VehicleEntity;
import dev.milmod.entity.FlightMission;
import dev.milmod.entity.FlashLight;
import dev.milmod.item.TabletItem;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class DroneLink {
    public static final int ENGINE_RANGE = 112;
    private static final String VERSION = "18";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder.named(
            ResourceLocation.tryBuild(MilMod.ID, "drone_link")).networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(VERSION::equals).serverAcceptedVersions(VERSION::equals).simpleChannel();

    private DroneLink() {}

    public static void register() {
        CHANNEL.messageBuilder(RackRelease.class, 8, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, buffer) -> {})
                .decoder(buffer -> new RackRelease())
                .consumerMainThread((message, source) -> {
                    var context = source.get();
                    ServerPlayer player = context.getSender();
                    if (player != null && player.isAlive() && !player.isSpectator()) {
                        VehicleEntity craft = VehicleEntity.controlledBy(player);
                        if (craft != null && craft.hasKabRack() && !craft.releaseRackItem(player)) {
                            player.displayClientMessage(Component.translatable("message.milmod.no_payload"), true);
                        }
                    }
                    context.setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(EnginePosition.class, 7, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EnginePosition::encode).decoder(EnginePosition::decode)
                .consumerMainThread((msg, context) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DistantEngineAudio.receive(msg));
                    context.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(HeavyExplosionPayload.class, 6, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HeavyExplosionPayload::encode).decoder(HeavyExplosionPayload::decode)
                .consumerMainThread((msg, context) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HeavyExplosionClient.receive(msg));
                    context.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(VisualTrail.class, 5, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> buf.writeDouble(msg.point.x).writeDouble(msg.point.y).writeDouble(msg.point.z))
                .decoder(buf -> new VisualTrail(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())))
                .consumerMainThread((msg, context) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SmokeClouds.trail(msg.point));
                    context.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(Route.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Route::encode).decoder(Route::decode)
                .consumerMainThread((route, source) -> {
                    var context = source.get();
                    ServerPlayer player = context.getSender();
                    if (player != null && player.isAlive() && !player.isSpectator()) {
                        VehicleEntity craft = linked(player, route.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
                        boolean accepted = craft != null && craft.startMission(player, route.mode(), route.marker());
                        player.displayClientMessage(Component.translatable(accepted
                                ? "message.milmod.mission_started" : "message.milmod.mission_rejected"), true);
                    }
                    context.setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(DisplayPoint.class, 3, NetworkDirection.PLAY_TO_SERVER)
                .encoder((point, buffer) -> buffer.writeBlockPos(point.block()))
                .decoder(buffer -> new DisplayPoint(buffer.readBlockPos()))
                .consumerMainThread((point, source) -> {
                    NetworkEvent.Context context = source.get();
                    ServerPlayer player = context.getSender();
                    if (player != null && player.isAlive() && !player.isSpectator()) {
                        boolean started = player.getVehicle() instanceof VehicleEntity vehicle
                                && vehicle.startDisplay(player, point.block());
                        player.displayClientMessage(Component.translatable(started
                                ? "message.milmod.display_started" : "message.milmod.display_unavailable"), true);
                    }
                    context.setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(Action.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Action::encode).decoder(Action::decode).consumerMainThread(DroneLink::handle).add();
        CHANNEL.messageBuilder(Blast.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Blast::encode).decoder(Blast::decode)
                .consumerMainThread((msg, context) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BlastEffects.play(msg.position(), msg.explosion()));
                    context.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(PilotInput.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PilotInput::encode).decoder(PilotInput::decode)
                .consumerMainThread((input, context) -> {
                    ServerPlayer player = context.get().getSender();
                    VehicleEntity vehicle = player == null || !player.isAlive() || player.isSpectator()
                            ? null : VehicleEntity.controlledBy(player);
                    if (vehicle != null) {
                        vehicle.acceptPilotInput(player, input.forward / 100F, input.side / 100F,
                                input.climb, input.yaw, input.pitch);
                    }
                    context.get().setPacketHandled(true);
                }).add();
    }

    private record DisplayPoint(BlockPos block) {}

    public static void displayAt(BlockPos block) {
        CHANNEL.sendToServer(new DisplayPoint(block));
    }

    public static void blast(ServerLevel level, Vec3 position) { flash(level, position, true); }

    public static void exhaustFlash(ServerLevel level, Vec3 position) { flash(level, position, false); }

    private static void flash(ServerLevel level, Vec3 position, boolean explosion) {
        FlashLight.place(level, position, explosion ? 15 : 11);
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                position.x, position.y, position.z, 96, level.dimension())), new Blast(position, explosion));
    }

    public static void heavyExplosion(ServerLevel level, Vec3 pos, float yield) {
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                pos.x, pos.y, pos.z, 160, level.dimension())), new HeavyExplosionPayload(pos, yield));
    }

    public static void visualTrail(ServerLevel level, Vec3 position) {
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                position.x, position.y, position.z, 96, level.dimension())), new VisualTrail(position));
    }

    public static void distantEngine(ServerLevel level, VehicleEntity aircraft) {
        if (level.players().isEmpty()) return;
        Vec3 pos = aircraft.position();
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                pos.x, pos.y, pos.z, ENGINE_RANGE, level.dimension())),
                new EnginePosition(aircraft.getId(), aircraft.profile(), pos, aircraft.getDeltaMovement(), aircraft.throttlePercent()));
    }

    public record EnginePosition(int id, VehicleProfile profile, Vec3 pos, Vec3 velocity, int throttle) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(id).writeEnum(profile)
                    .writeDouble(pos.x).writeDouble(pos.y).writeDouble(pos.z)
                    .writeFloat((float) velocity.x).writeFloat((float) velocity.y).writeFloat((float) velocity.z)
                    .writeByte(throttle);
        }

        static EnginePosition decode(FriendlyByteBuf buffer) {
            return new EnginePosition(buffer.readVarInt(), buffer.readEnum(VehicleProfile.class),
                    new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                    new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat()), buffer.readUnsignedByte());
        }
    }

    private record VisualTrail(Vec3 point) {}

    private record Blast(Vec3 position, boolean explosion) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeDouble(position.x).writeDouble(position.y).writeDouble(position.z).writeBoolean(explosion);
        }

        static Blast decode(FriendlyByteBuf buffer) {
            return new Blast(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()), buffer.readBoolean());
        }
    }

    public static void pilot(float forward, float side, boolean climb, float yaw, float pitch) {
        CHANNEL.sendToServer(new PilotInput((byte) Math.round(100 * forward),
                (byte) Math.round(100 * side), climb, yaw, pitch));
    }

    private record PilotInput(byte forward, byte side, boolean climb, float yaw, float pitch) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeByte(forward).writeByte(side).writeBoolean(climb).writeFloat(yaw).writeFloat(pitch);
        }

        static PilotInput decode(FriendlyByteBuf buffer) {
            return new PilotInput(buffer.readByte(), buffer.readByte(), buffer.readBoolean(),
                    buffer.readFloat(), buffer.readFloat());
        }
    }

    public static void send(boolean release, InteractionHand hand) {
        CHANNEL.sendToServer(new Action(release, hand == InteractionHand.MAIN_HAND, false));
    }

    public static void releaseRackItem() {
        CHANNEL.sendToServer(new RackRelease());
    }

    private record RackRelease() {}

    public static void stopRemote() {
        CHANNEL.sendToServer(new Action(false, true, true));
    }

    private static void handle(Action action, Supplier<NetworkEvent.Context> source) {
        NetworkEvent.Context context = source.get();
        ServerPlayer player = context.getSender();
        if (player == null || !player.isAlive() || player.isSpectator()) {
            context.setPacketHandled(true);
            return;
        }
        VehicleEntity active = VehicleEntity.controlledBy(player);
        if (action.disconnect) {
            if (active != null) active.stopRemote(player);
        } else if (action.release) {
            if (active == null || !active.releaseTrainingPayload(player)) {
                player.displayClientMessage(Component.translatable("message.milmod.no_payload"), true);
            }
        } else {
            InteractionHand hand = action.mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            VehicleEntity drone = linked(player, hand);
            if (drone == null) {
                player.displayClientMessage(Component.translatable("message.milmod.launch_unavailable"), true);
            } else {
                drone.startFromTablet(player);
            }
        }
        context.setPacketHandled(true);
    }

    private static VehicleEntity linked(ServerPlayer player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        UUID id = TabletItem.linked(stack);
        return id != null && player.serverLevel().getEntity(id) instanceof VehicleEntity vehicle
                && stack.getItem() instanceof TabletItem tablet && tablet.accepts(vehicle) ? vehicle : null;
    }

    public static void sendMission(InteractionHand hand, FlightMission.Mode mode, BlockPos marker) {
        CHANNEL.sendToServer(new Route(hand == InteractionHand.MAIN_HAND, mode, marker));
    }

    private record Route(boolean mainHand, FlightMission.Mode mode, BlockPos marker) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeBoolean(mainHand).writeByte(mode.ordinal());
            buffer.writeBlockPos(marker);
        }

        static Route decode(FriendlyByteBuf buffer) {
            if (buffer.readableBytes() != 10) return new Route(true, null, null);
            boolean hand = buffer.readBoolean();
            int mode = buffer.readUnsignedByte();
            BlockPos marker = buffer.readBlockPos();
            return new Route(hand, mode < FlightMission.Mode.values().length
                    ? FlightMission.Mode.values()[mode] : null, marker);
        }
    }

    private record Action(boolean release, boolean mainHand, boolean disconnect) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeBoolean(release);
            buffer.writeBoolean(mainHand).writeBoolean(disconnect);
        }

        static Action decode(FriendlyByteBuf buffer) {
            return new Action(buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
        }
    }
}
