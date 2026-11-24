package dev.milmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.milmod.MilMod;
import dev.milmod.entity.VehicleEntity;
import dev.milmod.model.VehicleProfile;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import dev.milmod.network.DroneLink;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MilMod.ID, value = Dist.CLIENT)
public final class PilotView {
    static final KeyMapping ZOOM = new KeyMapping("key.milmod.camera_zoom",
            InputConstants.Type.KEYSYM, InputConstants.KEY_F8, "key.categories.milmod");
    static final KeyMapping MAVIC_MODE = new KeyMapping("key.milmod.mavic_mode",
            InputConstants.Type.KEYSYM, InputConstants.KEY_F6, "key.categories.milmod");
    static final KeyMapping RELEASE = new KeyMapping("key.milmod.training_release",
            InputConstants.Type.KEYSYM, InputConstants.KEY_G, "key.categories.milmod");
    static final KeyMapping TABLET = new KeyMapping("key.milmod.pilot_tablet",
            InputConstants.Type.KEYSYM, InputConstants.KEY_F7, "key.categories.milmod");
    static final KeyMapping DISPLAY_MAP = new KeyMapping("key.milmod.display_map",
            InputConstants.Type.KEYSYM, InputConstants.KEY_B, "key.categories.milmod");
    private static CameraType oldCamera;
    private static final double[] FOV_SCALE = {1, 0.64, 0.39};
    private static int magnification;
    private static boolean mavicMonochrome = true;
    private static int connecting;
    private static int clientTicks;
    private static VehicleEntity lastVehicle;
    private static VehicleEntity remoteVehicle;

    private PilotView() {}

    @SubscribeEvent
    public static void updateZoom(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        clientTicks++;
        updateRemote(client);
        sendRemoteInput(client);
        VehicleEntity current = mountedAircraft(client);
        if (connecting > 0) connecting--;
        if (lastVehicle != current) {
            if (lastVehicle == null && current != null) {
                oldCamera = client.options.getCameraType();
                client.options.setCameraType(CameraType.FIRST_PERSON);
            } else if (current == null && oldCamera != null) {
                client.options.setCameraType(oldCamera);
                oldCamera = null;
            }
            lastVehicle = current;
            magnification = 0;
            mavicMonochrome = true;
            connecting = current != null && current.profile() == VehicleProfile.FORPOST_R
                    ? ForpostCameraDisplay.CONNECTION_TICKS : 0;
        }
        if (client.player != null && client.player.getVehicle() instanceof VehicleEntity
                && client.player.tickCount % 2 == 0) {
            var input = client.player.input;
            boolean drive = client.screen == null;
            DroneLink.pilot(drive ? input.forwardImpulse : 0, drive ? input.leftImpulse : 0,
                    drive && input.jumping, client.player.getYRot(), client.player.getXRot());
        }
        while (DISPLAY_MAP.consumeClick()) {
            if (client.screen != null || client.player == null) continue;
            if (current != null && current.hasKabRack()) {
                DroneLink.releaseRackItem();
            } else if (client.player.getVehicle() instanceof VehicleEntity vehicle
                    && vehicle.profile() == VehicleProfile.ISKANDER_K) {
                client.setScreen(new DisplayMap(vehicle));
            }
        }
        while (TABLET.consumeClick()) {
            if (current != null && client.screen == null) DroneTabletScreen.open(InteractionHand.MAIN_HAND);
        }
        while (RELEASE.consumeClick()) {
            if (current != null && current.profile() != VehicleProfile.MOHAJER_6 && current.trainingPayload() > 0) {
                dev.milmod.network.DroneLink.send(true, net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        }
        while (MAVIC_MODE.consumeClick()) {
            if (client.screen == null && isMavicView(client)) mavicMonochrome = !mavicMonochrome;
        }
        while (ZOOM.consumeClick()) {
            if (current != null && client.options.getCameraType().isFirstPerson()) {
                magnification = (magnification + 1) % FOV_SCALE.length;
            }
        }
    }

    private static void updateRemote(Minecraft client) {
        if (client.player == null || client.level == null || client.player.isPassenger()) {
            remoteVehicle = null;
            return;
        }
        if (remoteVehicle != null && (remoteVehicle.isRemoved() || remoteVehicle.level() != client.level
                || !remoteVehicle.hasFlightCamera() || !remoteVehicle.remotelyControlledBy(client.player))) remoteVehicle = null;
        if (remoteVehicle == null && clientTicks % 3 == 0) {
            for (var entity : client.level.entitiesForRendering()) {
                if (entity instanceof VehicleEntity vehicle && vehicle.hasFlightCamera()
                        && vehicle.remotelyControlledBy(client.player)) {
                    remoteVehicle = vehicle;
                    break;
                }
            }
        }
    }

    @SubscribeEvent
    public static void remoteInput(MovementInputUpdateEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (event.getEntity() != client.player || remoteVehicle == null || client.player == null
                || !remoteVehicle.remotelyControlledBy(client.player)) return;
        var input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.jumping = false;
    }

    private static void sendRemoteInput(Minecraft client) {
        if (remoteVehicle == null || client.player == null || (clientTicks & 1) != 0) return;
        var keys = client.options;
        boolean focused = client.screen == null && client.isWindowActive();
        if (focused && keys.keyShift.isDown()) {
            DroneLink.stopRemote();
            return;
        }
        float throttle = focused ? axis(keys.keyUp, keys.keyDown) : 0;
        float bank = focused ? axis(keys.keyLeft, keys.keyRight) : 0;
        DroneLink.pilot(throttle, bank, focused && keys.keyJump.isDown(), client.player.getYRot(), client.player.getXRot());
    }

    private static int axis(KeyMapping positive, KeyMapping negative) {
        return (positive.isDown() ? 1 : 0) - (negative.isDown() ? 1 : 0);
    }

    static boolean onboardAudio(Minecraft client, VehicleEntity vehicle) {
        return client.options.getCameraType().isFirstPerson() && mountedAircraft(client) == vehicle;
    }

    static VehicleEntity remoteVehicle() { return remoteVehicle; }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft client = Minecraft.getInstance();
        VehicleEntity vehicle = mountedAircraft(client);
        if (!client.options.getCameraType().isFirstPerson()) return;
        if (vehicle == null) {
            if (client.player != null && client.player.getVehicle() instanceof VehicleEntity ground
                    && ground.profile().kind == VehicleProfile.Kind.GROUND) {
                float tick = (float) event.getPartialTick();
                Vec3 point = ground.getPosition(tick);
                Vec3 seat = ground.seatPosition(Mth.rotLerp(tick, ground.yRotO, ground.getYRot()),
                        point.x, point.y, point.z).add(0, ground.profile().height * .45, 0);
                event.getCamera().setPosition(seat.x, seat.y, seat.z);
                event.setRoll(event.getRoll() + ground.bank(tick) * .55F);
            }
            return;
        }
        VehicleProfile profile = vehicle.profile();
        float partial = (float) event.getPartialTick();
        Camera camera = event.getCamera();
        Vec3 body = vehicle.getPosition(partial);
        double yaw = Math.toRadians(Mth.rotLerp(partial, vehicle.yRotO, vehicle.getYRot()));
        double reach = profile.length * switch (profile) {
            case MAVIC3T -> 0.51;
            case L39_ALBATROS -> 0.24;
            default -> 0.55;
        };
        double height = profile.height * (profile == VehicleProfile.L39_ALBATROS ? 1.1 : 0.37);
        Vec3 origin = body.add(0, height, 0);
        Vec3 requested = origin.add(-Math.sin(yaw) * reach, 0, Math.cos(yaw) * reach);
        // Keep the onboard camera on the visible side of solid blocks.
        HitResult hit = vehicle.level().clip(new ClipContext(origin, requested,
                ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, vehicle));
        Vec3 position = hit.getType() == HitResult.Type.MISS ? requested : hit.getLocation().lerp(origin, 0.12);
        camera.setPosition(position.x, position.y, position.z);
        if (profile != VehicleProfile.MAVIC3T) {
            event.setRoll(event.getRoll() + vehicle.bank(partial) * 0.55F);
        }
    }

    @SubscribeEvent
    public static void zoom(ViewportEvent.ComputeFov event) {
        Minecraft client = Minecraft.getInstance();
        VehicleEntity vehicle = mountedAircraft(client);
        if (event.usedConfiguredFov() && vehicle != null
                && client.options.getCameraType().isFirstPerson()) {
            event.setFOV(event.getFOV() * FOV_SCALE[magnification]);
        }
    }

    @SubscribeEvent
    public static void hideFlightOverlays(RenderGuiOverlayEvent.Pre event) {
        Minecraft client = Minecraft.getInstance();
        VehicleEntity vehicle = mountedAircraft(client);
        if (vehicle == null || !client.options.getCameraType().isFirstPerson()) return;
        var id = event.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.CROSSHAIR.id()) || id.equals(VanillaGuiOverlay.HOTBAR.id())
                || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id()) || id.equals(VanillaGuiOverlay.ITEM_NAME.id())
                || (vehicle.profile() == VehicleProfile.FORPOST_R || vehicle.profile() == VehicleProfile.ORLAN_10
                    || vehicle.profile() == VehicleProfile.MAVIC3T)
                && (id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id()) || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())
                || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id()) || id.equals(VanillaGuiOverlay.MOUNT_HEALTH.id())
                || id.equals(VanillaGuiOverlay.AIR_LEVEL.id()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void overlay(RenderGuiEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        VehicleEntity vehicle = mountedAircraft(client);
        if (client.options.hideGui || !client.options.getCameraType().isFirstPerson()) return;
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        if (vehicle == null) {
            if (client.player != null && client.player.getVehicle() instanceof VehicleEntity ground
                    && ground.profile().kind == VehicleProfile.Kind.GROUND && client.screen == null) {
                GroundCabDisplay.draw(event.getGuiGraphics(), client.font, width, height, ground);
            }
            return;
        }
        float zoom = (float) (1 / FOV_SCALE[magnification]);
        if (vehicle.profile() == VehicleProfile.FORPOST_R) {
            ForpostCameraDisplay.draw(event.getGuiGraphics(), client.font, width, height, vehicle,
                    zoom, event.getPartialTick(), connecting, client.screen == null);
        } else if (vehicle.profile() == VehicleProfile.ORLAN_10) {
            Camera view = client.gameRenderer.getMainCamera();
            OrlanDisplay.draw(event.getGuiGraphics(), client.font, width, height, vehicle,
                    zoom, view.getYRot(), view.getXRot());
        } else if (vehicle.profile() == VehicleProfile.MAVIC3T) {
            MavicDisplay.draw(event.getGuiGraphics(), client.font, width, height, vehicle, zoom, client.player,
                    mavicMonochrome(client) && MavicCameraEffect.available(),
                    MAVIC_MODE.getTranslatedKeyMessage(), ZOOM.getTranslatedKeyMessage(),
                    client.options.keyScreenshot.getTranslatedKeyMessage());
        } else {
            FlightDisplay.draw(event.getGuiGraphics(), client.font, width, height, vehicle, zoom);
        }
    }

    @SubscribeEvent
    public static void hideHands(RenderHandEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (client.options.getCameraType().isFirstPerson() && (mountedAircraft(client) != null
                || client.player != null && client.player.getVehicle() instanceof VehicleEntity)) event.setCanceled(true);
    }

    static boolean mavicMonochrome(Minecraft client) {
        return mavicMonochrome && isMavicView(client);
    }

    private static boolean isMavicView(Minecraft client) {
        VehicleEntity vehicle = mountedAircraft(client);
        return client.level != null && client.player != null && client.player.isAlive()
                && client.options.getCameraType().isFirstPerson() && vehicle != null
                && !vehicle.isRemoved() && vehicle.profile() == VehicleProfile.MAVIC3T;
    }

    private static VehicleEntity mountedAircraft(Minecraft client) {
        return client.player != null && client.player.getVehicle() instanceof VehicleEntity vehicle
                && vehicle.profile().air() ? vehicle : remoteVehicle;
    }

}
