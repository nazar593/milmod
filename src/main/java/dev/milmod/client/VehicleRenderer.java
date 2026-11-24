package dev.milmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.milmod.MilMod;
import dev.milmod.entity.VehicleEntity;
import dev.milmod.entity.DisplaySequence;
import dev.milmod.model.VehicleProfile;
import dev.milmod.model.DroneSpec;
import org.joml.Quaternionf;
import dev.milmod.model.VehicleSuspension;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import dev.milmod.registry.ModContent;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class VehicleRenderer extends EntityRenderer<VehicleEntity> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MilMod.ID);
    private static final Map<VehicleProfile, ResourceLocation> TEXTURES = new EnumMap<>(VehicleProfile.class);
    private static Map<VehicleProfile, VehicleMesh> meshes = Map.of();
    private final float[] suspension = new float[16];
    private static final ResourceLocation NOZZLE = ResourceLocation.tryBuild(MilMod.ID, "textures/effect/glow.png");
    private final ItemStack modem = new ItemStack(ModContent.MESH_MODEM.get());
    private final ItemStack capsule = new ItemStack(ModContent.DUMMY_GRENADE.get());
    private final ItemStack kab = new ItemStack(ModContent.DUMMY_KAB.get());

    static {
        for (VehicleProfile profile : VehicleProfile.values()) {
            TEXTURES.put(profile, ResourceLocation.tryBuild(MilMod.ID, "textures/entity/" + profile.id + ".png"));
        }
    }

    VehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.4F;
    }

    static void reload(ResourceManager manager) {
        Map<VehicleProfile, VehicleMesh> next = new EnumMap<>(VehicleProfile.class);
        for (VehicleProfile profile : VehicleProfile.values()) {
            if (!profile.available()) continue;
            ResourceLocation resource = ResourceLocation.tryBuild(MilMod.ID, "mesh/" + profile.id + ".milmesh");
            try {
                next.put(profile, VehicleMesh.read(manager, resource, profile));
            } catch (IOException exception) {
                LOGGER.error("Failed to read {}", resource, exception);
            }
        }
        meshes = next;
    }

    @Override
    public ResourceLocation getTextureLocation(VehicleEntity vehicle) {
        return TEXTURES.get(vehicle.profile());
    }

    @Override
    public void render(VehicleEntity vehicle, float yaw, float partial, PoseStack stack,
                       MultiBufferSource buffers, int light) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.getVehicle() == vehicle
                && !vehicle.profile().air() && client.options.getCameraType().isFirstPerson()) return;
        VehicleMesh mesh = meshes.getOrDefault(vehicle.profile(), VehicleMesh.EMPTY);
        double distance = entityRenderDispatcher.distanceToSqr(vehicle);
        int detail = distance > 68 * 68 ? 2 : distance > 30 * 30 ? 1 : 0;
        float time = vehicle.tickCount + partial;
        int mask = vehicle.damageMask();
        if (vehicle.profile() == VehicleProfile.BEACON_MARKER && vehicle.feature(partial) > 0.5F
                && time % 20 < 12) light = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(vehicle)));
        stack.pushPose();
        double pivot = vehicle.profile().height * 0.43;
        stack.translate(0, pivot, 0);
        Quaternionf old = orientation(vehicle.yRotO, vehicle.xRotO, vehicle.bank(0));
        Quaternionf next = orientation(vehicle.getYRot(), vehicle.getXRot(), vehicle.bank(1));
        stack.mulPose(old.slerp(next, partial));
        stack.translate(0, -pivot, 0);

        VehicleSuspension layout = VehicleSuspension.of(vehicle.profile());
        for (int i = 0; i < layout.size(); i++) suspension[i] = vehicle.suspensionOffset(i, partial);
        int fragment = vehicle.fragmentPart();
        if (fragment != 0) stack.translate(vehicle.profile().width * (fragment == 1 ? .31 : -.31), -.25, 0);
        float display = vehicle.displayTime(partial);
        for (VehicleMesh.Group group : mesh.groups) {
            if (group.animation() == 15 && display >= DisplaySequence.FIRST + 8
                    || group.animation() == 16 && display >= DisplaySequence.SECOND + 8) continue;
            if (fragment != 0 && group.part() != fragment || fragment == 0 && vehicle.separated(group.part())) continue;
            boolean detached = group.part() != 0 && (mask & group.part()) != 0;
            float debris = detached ? vehicle.debrisAge(group.part(), partial) : 0;
            if (detached && debris >= 18) continue;
            if (group.animation() == 0 && !detached) {
                group.draw(stack.last(), buffer, light, detail, suspension);
                continue;
            }
            boolean gearPart = vehicle.hasAnimatedGear() && (group.animation() == 17 || group.animation() == 5);
            float gear = gearPart ? vehicle.gearProgress(partial) : 0;
            if (gear >= .99F) continue;
            stack.pushPose();
            if (gear > 0) stack.translate(-group.x() * .8 * gear, .42 * gear, 0);
            if (detached) {
                float side = group.part() == 1 ? -1 : 1;
                stack.translate(side * debris * 0.065, -debris * debris * 0.007, debris * 0.025);
                stack.mulPose(Axis.ZP.rotationDegrees(side * debris * 8));
            }
            if (group.wheel() >= 0) stack.translate(0, suspension[group.wheel()] * (1 - gear), 0);
            stack.translate(group.x(), group.y(), group.z());
            if (group.animation() == 5 && (vehicle.profile() == VehicleProfile.GAZ_TIGR
                    || vehicle.profile() == VehicleProfile.ISKANDER_K) && group.z() < -0.25) {
                stack.mulPose(Axis.YP.rotationDegrees(24 * vehicle.steering()));
            }
            if (group.animation() == 5) {
                float radius = Math.max(.085F, layout.radius());
                float lateral = layout.tracks() ? group.wheelX() : 0;
                stack.mulPose(Axis.XP.rotationDegrees((float) Math.toDegrees(-vehicle.wheelTravel(partial, lateral) / radius)));
            } else {
                animate(group.animation(), vehicle, time, partial, display, stack);
            }
            stack.translate(-group.x(), -group.y(), -group.z());
            group.draw(stack.last(), buffer, light, detail, suspension);
            stack.popPose();
        }
        if (fragment == 0 && distance <= 38 * 38) {
            DroneSpec spec = DroneSpec.of(vehicle.profile());
            if (spec != null && spec.engine() == DroneSpec.Engine.JET && vehicle.running()) {
                drawNozzle(vehicle, stack, buffers, time);
            }
            if (vehicle.modemInstalled()) {
                stack.pushPose();
                stack.translate(0, vehicle.profile().height * .59, -vehicle.profile().length * .43);
                stack.scale(.27F, .27F, .27F);
                Minecraft.getInstance().getItemRenderer().renderStatic(modem, ItemDisplayContext.FIXED, light,
                        OverlayTexture.NO_OVERLAY, stack, buffers, vehicle.level(), vehicle.getId());
                stack.popPose();
            }
        }
        int visiblePayloads = detail == 2 ? 0 : vehicle.trainingPayload();
        for (int i = 0; i < visiblePayloads; i++) {
            boolean small = vehicle.profile() == VehicleProfile.MAVIC3T;
            stack.pushPose();
            var mount = vehicle.payloadMount(i);
            stack.translate(mount.x, mount.y, mount.z);
            float size = small ? 0.31F : 0.70F;
            stack.scale(size, size, size);
            Minecraft.getInstance().getItemRenderer().renderStatic(small ? capsule : kab,
                    ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, stack, buffers,
                    vehicle.level(), vehicle.getId());
            stack.popPose();
        }
        stack.popPose();
        super.render(vehicle, yaw, partial, stack, buffers, light);
    }

    private static Quaternionf orientation(float yaw, float pitch, float roll) {
        return Axis.YP.rotationDegrees(180 - yaw)
                .mul(Axis.XP.rotationDegrees(pitch)).mul(Axis.ZP.rotationDegrees(roll));
    }

    private static void drawNozzle(VehicleEntity vehicle, PoseStack stack, MultiBufferSource buffers, float time) {
        // Emissive geometry ignores the lightmap. FlashLight supplies discrete real block light.
        var consumer = buffers.getBuffer(RenderType.eyes(NOZZLE));
        var pose = stack.last();
        float radius = .065F + vehicle.throttlePercent() * .0008F + .016F * Mth.sin(time * 4);
        float y = vehicle.profile().height * .40F, z = vehicle.profile().length * .43F;
        nozzleVertex(consumer, pose, -radius, y - radius, z, 0, 1);
        nozzleVertex(consumer, pose, radius, y - radius, z, 1, 1);
        nozzleVertex(consumer, pose, radius, y + radius, z, 1, 0);
        nozzleVertex(consumer, pose, -radius, y + radius, z, 0, 0);
    }

    private static void nozzleVertex(VertexConsumer consumer, PoseStack.Pose pose,
                                     float x, float y, float z, float u, float v) {
        consumer.vertex(pose.pose(), x, y, z).color(255, 165, 65, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), 0, 0, 1).endVertex();
    }

    private static void animate(int action, VehicleEntity vehicle, float time, float partial, float display, PoseStack stack) {
        float feature = vehicle.feature(partial);
        float doors = Math.max(feature, DisplaySequence.doors(display));
        float roof = Math.max(feature, DisplaySequence.roof(display));
        switch (action) {
            case 1 -> stack.mulPose(Axis.YP.rotationDegrees(vehicle.rotorAngle(partial)));
            case 2 -> stack.mulPose(Axis.YP.rotationDegrees(-vehicle.rotorAngle(partial)));
            case 3 -> stack.mulPose(Axis.ZP.rotationDegrees(vehicle.rotorAngle(partial)));
            case 4 -> stack.mulPose(Axis.YP.rotationDegrees((time * 4 % 360) * feature));
            case 6 -> stack.mulPose(Axis.XP.rotationDegrees(-58 * feature));
            case 7 -> stack.mulPose(Axis.XP.rotationDegrees(33 * feature));
            case 8 -> stack.mulPose(Axis.YP.rotationDegrees(-100 * doors));
            case 9 -> stack.mulPose(Axis.YP.rotationDegrees(100 * doors));
            case 10 -> stack.mulPose(Axis.ZP.rotationDegrees(146 * roof));
            case 11 -> stack.mulPose(Axis.ZP.rotationDegrees(-146 * roof));
            case 14, 15, 16 -> {
                stack.mulPose(Axis.XP.rotationDegrees(90 * Math.max(feature, DisplaySequence.lift(display))));
                if (action != 14) {
                    int start = action == 15 ? DisplaySequence.FIRST : DisplaySequence.SECOND;
                    stack.translate(0, 0, -6.6 * DisplaySequence.progress(display, start, start + 8));
                }
            }
            case 12 -> stack.mulPose(Axis.XP.rotationDegrees(-15 * feature));
            case 18 -> stack.mulPose(Axis.XP.rotationDegrees(vehicle.controlSurface(0, partial) * 22));
            case 19 -> stack.mulPose(Axis.XP.rotationDegrees(-vehicle.controlSurface(0, partial) * 22));
            case 20 -> stack.mulPose(Axis.XP.rotationDegrees(-vehicle.controlSurface(1, partial) * 23));
            case 21 -> stack.mulPose(Axis.YP.rotationDegrees(vehicle.controlSurface(2, partial) * 24));
            default -> { }
        }
    }
}
