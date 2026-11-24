package dev.milmod.client;

import dev.milmod.MilMod;
import dev.milmod.entity.VehicleEntity;
import dev.milmod.config.ModConfig;
import dev.milmod.model.DroneSpec;
import dev.milmod.network.DroneLink;
import dev.milmod.sound.ModSounds;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MilMod.ID, value = Dist.CLIENT)
public final class DistantEngineAudio {
    private static final int LIMIT = 8;
    private static final Map<Integer, Engine> SOUNDS = new HashMap<>();
    private static ClientLevel world;

    private DistantEngineAudio() {}

    public static void receive(DroneLink.EnginePosition sample) {
        var client = Minecraft.getInstance();
        updateWorld(client);
        if (world == null || DroneSpec.of(sample.profile()) == null || !sample.profile().available()
                || world.getEntity(sample.id()) instanceof VehicleEntity) return;
        int range = ModConfig.DRONE_AUDIO_RANGE.get();
        if (sample.pos().distanceToSqr(client.gameRenderer.getMainCamera().getPosition()) >= range * range) return;
        Engine sound = SOUNDS.get(sample.id());
        if (sound != null) {
            sound.update(sample, world.getGameTime());
        } else if (SOUNDS.size() < LIMIT) {
            sound = new Engine(sample, world.getGameTime());
            SOUNDS.put(sample.id(), sound);
            client.getSoundManager().play(sound);
        }
    }

    private static void updateWorld(Minecraft client) {
        if (world == client.level) return;
        for (Engine sound : SOUNDS.values()) client.getSoundManager().stop(sound);
        SOUNDS.clear();
        world = client.level;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        updateWorld(client);
        SOUNDS.values().removeIf(Engine::isStopped);
    }

    private static final class Engine extends AbstractTickableSoundInstance implements ConcussionAudio.Bandwidth {
        private final EngineAcoustics acoustics = new EngineAcoustics();
        private DroneLink.EnginePosition sample;
        private long received;

        Engine(DroneLink.EnginePosition position, long now) {
            super(ModSounds.vehicle(position.profile()), SoundSource.NEUTRAL, RandomSource.create());
            looping = true;
            attenuation = Attenuation.NONE;
            update(position, now);
            applyPosition(position.pos());
            updateEngine(position.pos(), position.throttle() / 100F);
        }

        void update(DroneLink.EnginePosition position, long now) {
            sample = position;
            received = now;
        }

        @Override
        public void tick() {
            long elapsed = world == null ? 40 : world.getGameTime() - received;
            if (elapsed > 26 || world.getEntity(sample.id()) instanceof VehicleEntity) {
                stop();
                return;
            }
            Vec3 point = sample.pos().add(sample.velocity().scale(Math.min(elapsed, 12)));
            applyPosition(point);
            updateEngine(point, sample.throttle() / 100F);
            volume *= Mth.clamp((26 - elapsed) / 8F, 0, 1);
        }

        private void applyPosition(Vec3 point) {
            x = point.x;
            y = point.y;
            z = point.z;
        }

        @Override
        public float highFrequencyGain() { return acoustics.highFrequencyGain(); }

        private void updateEngine(Vec3 point, float throttle) {
            acoustics.update(Minecraft.getInstance(), point, sample.velocity(), false);
            var spec = DroneSpec.of(sample.profile());
            volume = (spec.engine() == DroneSpec.Engine.ELECTRIC ? .22F + .15F * throttle : .34F + .41F * throttle)
                    * acoustics.volume();
            pitch = spec.enginePitch() * acoustics.pitch() * switch (spec.engine()) {
                case ELECTRIC -> 1.15F + .42F * throttle;
                case PISTON -> .67F + .40F * throttle;
                case JET -> .80F + .40F * throttle;
            };
        }
    }
}
