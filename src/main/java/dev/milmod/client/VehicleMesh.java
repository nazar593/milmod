package dev.milmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.milmod.model.VehicleProfile;
import dev.milmod.model.VehicleSuspension;
import net.minecraft.util.Mth;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.ResourceLocation;

final class VehicleMesh {
    static final VehicleMesh EMPTY = new VehicleMesh(new Group[0]);
    final Group[] groups;

    private VehicleMesh(Group[] groups) {
        this.groups = groups;
    }

    static VehicleMesh read(ResourceManager manager, ResourceLocation location, VehicleProfile profile) throws IOException {
        VehicleSuspension layout = VehicleSuspension.of(profile);
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(manager.open(location)))) {
            if (in.readInt() != 0x4D494C31) throw new IOException("Invalid MilMod mesh signature");
            int count = in.readUnsignedShort();
            if (count > 2048) throw new IOException("Too many MilMod mesh groups");
            Group[] groups = new Group[count];
            int totalVertices = 0;
            for (int i = 0; i < count; i++) {
                int part = in.readUnsignedByte();
                int animation = in.readUnsignedByte();
                float x = in.readFloat(), y = in.readFloat(), z = in.readFloat();
                int vertices = in.readInt();
                if (vertices < 0 || vertices % 4 != 0 || vertices > 120_000 - totalVertices) {
                    throw new IOException("Invalid MilMod mesh vertex count");
                }
                totalVertices += vertices;
                float[] data = new float[vertices * 8];
                for (int at = 0; at < data.length; at++) {
                    data[at] = in.readFloat();
                    if (!Float.isFinite(data[at])) throw new IOException("Non-finite MilMod mesh coordinate");
                }
                float wheelX = x;
                int wheel = -1;
                if (animation == 5 && vertices > 0) {
                    float min = data[0], max = min;
                    for (int at = 8; at < data.length; at += 8) {
                        min = Math.min(min, data[at]);
                        max = Math.max(max, data[at]);
                    }
                    wheelX = (min + max) * .5F;
                    wheel = layout.nearest(wheelX, z);
                }
                float[] medium = simplify(data, .002F);
                float[] far = simplify(medium, Math.min(.012F, profile.width * profile.width * .003F));
                boolean followsSuspension = switch (profile) {
                    case T64BV -> i == 1 || i == 8;
                    case BUK_M3 -> i >= 1 && i <= 4;
                    case M113 -> i == 8 || i == 9;
                    case TOR_M1 -> i == 0;
                    default -> false;
                };
                TrackBinding nearTrack = followsSuspension ? bindTrack(data, layout, profile) : null;
                TrackBinding mediumTrack = medium == data ? nearTrack
                        : followsSuspension ? bindTrack(medium, layout, profile) : null;
                TrackBinding farTrack = far == medium ? mediumTrack
                        : followsSuspension ? bindTrack(far, layout, profile) : null;
                groups[i] = new Group(part, animation, x, y, z, wheelX, wheel, data, medium, far,
                        nearTrack, mediumTrack, farTrack);
            }
            if (in.read() != -1) throw new IOException("Unexpected trailing MilMod mesh bytes");
            return new VehicleMesh(groups);
        }
    }

    private static float[] simplify(float[] source, float minimumArea) {
        // Small standalone pieces retain their geometry at every distance.
        if (source.length < 16 * 32) return source;
        float limit = minimumArea * minimumArea;
        float[] visible = new float[source.length];
        int tail = 0;
        for (int at = 0; at < source.length; at += 32) {
            float ux = source[at + 8] - source[at], uy = source[at + 9] - source[at + 1];
            float uz = source[at + 10] - source[at + 2];
            float vx = source[at + 16] - source[at], vy = source[at + 17] - source[at + 1];
            float vz = source[at + 18] - source[at + 2];
            float x = uy * vz - uz * vy, y = uz * vx - ux * vz, z = ux * vy - uy * vx;
            if (x*x + y*y + z*z < limit) continue;
            System.arraycopy(source, at, visible, tail, 32);
            tail += 32;
        }
        return tail < 6 * 32 || tail == source.length ? source : java.util.Arrays.copyOf(visible, tail);
    }

    private static TrackBinding bindTrack(float[] vertices, VehicleSuspension layout, VehicleProfile profile) {
        int size = vertices.length / 8;
        byte[] indices = new byte[size];
        float[] blends = new float[size];
        float[] weights = new float[size];
        for (int i = 0; i < size; i++) {
            float x = vertices[i * 8], y = vertices[i * 8 + 1], z = vertices[i * 8 + 2];
            int side = x < 0 ? 0 : 1;
            if (profile == VehicleProfile.TOR_M1 && (Math.abs(x) < .91F || y > .35F)) continue;
            int front = side;
            while (front + 2 < layout.size() - 2 && z > layout.contact(front + 2).z()) front += 2;
            float a = layout.contact(front).z(), b = layout.contact(front + 2).z();
            indices[i] = (byte) front;
            blends[i] = Mth.clamp((z - a) / (b - a), 0, 1);
            float end = Mth.clamp(1 - Math.max(layout.contact(side).z() - z,
                    z - layout.contact(layout.size() - 2 + side).z()) / .64F, 0, 1);
            float treadHeight = Math.max(0F, layout.radius() * .28F);
            weights[i] = Mth.clamp((.58F - y) / (.58F - treadHeight), 0, 1) * end;
        }
        return new TrackBinding(indices, blends, weights);
    }

    private record TrackBinding(byte[] indices, float[] blends, float[] weights) {
        float offset(int vertex, float[] suspension) {
            float weight = weights[vertex];
            int i = indices[vertex];
            return weight == 0 ? 0 : weight * Mth.lerp(blends[vertex], suspension[i], suspension[i + 2]);
        }
    }

    record Group(int part, int animation, float x, float y, float z, float wheelX, int wheel,
                 float[] near, float[] medium, float[] far, TrackBinding nearTrack, TrackBinding mediumTrack,
                 TrackBinding farTrack) {
        void draw(PoseStack.Pose pose, VertexConsumer output, int light, int detail, float[] suspension) {
            float[] vertices = detail == 0 ? near : detail == 1 ? medium : far;
            TrackBinding track = detail == 0 ? nearTrack : detail == 1 ? mediumTrack : farTrack;
            for (int at = 0; at < vertices.length; at += 8) {
                float y = vertices[at + 1] + (track == null ? 0 : track.offset(at / 8, suspension));
                output.vertex(pose.pose(), vertices[at], y, vertices[at + 2])
                        .color(255, 255, 255, 255).uv(vertices[at + 3], vertices[at + 4])
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                        .normal(pose.normal(), vertices[at + 5], vertices[at + 6], vertices[at + 7])
                        .endVertex();
            }
        }
    }
}
