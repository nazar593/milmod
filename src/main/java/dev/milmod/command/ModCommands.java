package dev.milmod.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.milmod.compat.OptionalMods;
import dev.milmod.config.ModConfig;
import dev.milmod.entity.VehicleEntity;
import dev.milmod.entity.VehiclePlacement;
import dev.milmod.model.VehicleProfile;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Arrays;

public final class ModCommands {
    private ModCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("milmod")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn").then(Commands.argument("model", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(VehicleProfile.values()).filter(VehicleProfile::available).map(profile -> profile.id), builder))
                        .executes(context -> {
                            String id = StringArgumentType.getString(context, "model");
                            VehicleProfile profile = VehicleProfile.byId(id);
                            if (!profile.id.equals(id) || !profile.available()) {
                                context.getSource().sendFailure(Component.literal("Unknown MilMod model: " + id));
                                return 0;
                            }
                            VehicleEntity vehicle = VehicleEntity.create(context.getSource().getLevel(), profile);
                            Vec3 pos = context.getSource().getPosition();
                            vehicle.moveTo(pos.x, pos.y, pos.z, context.getSource().getRotation().y, 0);
                            if (!VehiclePlacement.add(context.getSource().getLevel(), vehicle, context.getSource().getPlayer())) {
                                context.getSource().sendFailure(Component.literal("MilMod could not place " + id));
                                return 0;
                            }
                            context.getSource().sendSuccess(() -> Component.literal("MilMod spawned " + id), false);
                            return 1;
                        })))
                .then(Commands.literal("debug").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.translatableWithFallback(
                            "commands.milmod.debug",
                            "TaCZ: %s | Superb Warfare: %s | Drone limits: %s per dimension, %s per player",
                            OptionalMods.displayVersion("tacz"),
                            OptionalMods.displayVersion("superbwarfare"),
                            ModConfig.WORLD_DRONE_LIMIT.get(),
                            ModConfig.PLAYER_DRONE_LIMIT.get()), false);
                    return 1;
                })));
    }
}
