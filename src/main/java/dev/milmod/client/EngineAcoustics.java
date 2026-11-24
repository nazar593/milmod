package dev.milmod.client;

import dev.milmod.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class EngineAcoustics implements ConcussionAudio.Bandwidth {
    private float doppler = 1;
    private float obstruction = 1;
    private float obstructionTarget = 1;
    private volatile float highFrequencies = 1;
    private int probe;
    private float volumeFactor = 1;
    private Vec3 previousListener;

    float pitch() { return doppler; }
    float volume() { return volumeFactor; }
    @Override public float highFrequencyGain() { return highFrequencies; }

    void update(Minecraft client, Vec3 source, Vec3 velocity, boolean cabin) {
        Vec3 listener = client.gameRenderer.getMainCamera().getPosition();
        Vec3 offset = listener.subtract(source);
        double distance = offset.length();
        float target = 1;
        if (!cabin && distance > .35) {
            Vec3 direction = offset.scale(1 / distance);
            double speed = ModConfig.SOUND_SPEED.get() / 20.0;
            double radial = Mth.clamp(velocity.dot(direction), -speed * .4, speed * .4);
            double observer = 0;
            if (previousListener != null) {
                Vec3 step = listener.subtract(previousListener);
                if (step.lengthSqr() < 4) observer = Mth.clamp(step.dot(direction), -speed * .2, speed * .2);
            }
            target = (float) Mth.clamp((speed - observer) / (speed - radial), .75, 1.3);
        }
        previousListener = listener;
        doppler = cabin ? 1 : Mth.lerp(.39F, doppler, target);

        if (cabin) {
            obstruction = obstructionTarget = volumeFactor = highFrequencies = 1;
            return;
        }
        if (probe-- <= 0) {
            probe = 4;
            obstructionTarget = 1;
            if (client.level != null && client.player != null && distance > 1) {
                var hit = client.level.clip(new ClipContext(listener, source, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, client.player));
                if (hit.getType() == HitResult.Type.BLOCK) obstructionTarget = .45F;
            }
        }
        obstruction = Mth.lerp(.34F, obstruction, obstructionTarget);
        double max = ModConfig.DRONE_AUDIO_RANGE.get();
        double fade = Mth.clamp((max - distance) / Math.max(1, max * .12), 0, 1);
        volumeFactor = (float) (Math.min(1, 14 / Math.max(1, distance)) * fade) * obstruction;
        float air = 1 - .62F * Mth.clamp((float) ((distance - 12) / Math.max(1, max - 12)), 0, 1);
        highFrequencies = Math.min(air, .08F + .92F * obstruction * obstruction);
    }
}
