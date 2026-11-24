package dev.milmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/** Forge 1.20.1 SimpleChannel message, containing visual data only. */
public record HeavyExplosionPayload(Vec3 pos, float yield) {
    void encode(FriendlyByteBuf buffer) {
        buffer.writeDouble(pos.x).writeDouble(pos.y).writeDouble(pos.z).writeFloat(yield);
    }

    static HeavyExplosionPayload decode(FriendlyByteBuf buffer) {
        return new HeavyExplosionPayload(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readFloat());
    }
}
