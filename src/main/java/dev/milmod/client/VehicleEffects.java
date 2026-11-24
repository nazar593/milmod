package dev.milmod.client;

import dev.milmod.MilMod;
import dev.milmod.model.DroneSpec;
import dev.milmod.config.ModConfig;
import dev.milmod.entity.VehicleEntity;
import dev.milmod.model.VehicleHandling;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import dev.milmod.sound.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MilMod.ID, value = Dist.CLIENT)
public final class VehicleEffects {
    private static final int AUDIO_LIMIT = 12;
    private static final double GROUND_RANGE_SQ = 36 * 36;

    private static final Map<VehicleEntity, EngineLayers> SOUNDS = new IdentityHashMap<>();

    private VehicleEffects() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        Iterator<Map.Entry<VehicleEntity, EngineLayers>> iterator = SOUNDS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().main().isStopped() || !audible(client, entry.getKey())) {
                client.getSoundManager().stop(entry.getValue().main());
                if (entry.getValue().detail() != null) client.getSoundManager().stop(entry.getValue().detail());
                iterator.remove();
            }
        }
        if (client.level == null || client.player == null || client.isPaused()
) return;

        long time = client.level.getGameTime();
        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof VehicleEntity vehicle)) continue;
            double distance = vehicle.distanceToSqr(camera);
            if (distance > (vehicle.profile().air() ? airRangeSquared() : 52 * 52)) continue;
            if (SOUNDS.size() < AUDIO_LIMIT && audible(client, vehicle) && !SOUNDS.containsKey(vehicle)) {
                var spec = DroneSpec.of(vehicle.profile());
                EngineAcoustics acoustics = new EngineAcoustics();
                EngineSound main = new EngineSound(vehicle, false, acoustics);
                EngineSound detail = spec != null ? new EngineSound(vehicle, true, acoustics) : null;
                SOUNDS.put(vehicle, new EngineLayers(main, detail));
                client.getSoundManager().play(main);
                if (detail != null) client.getSoundManager().play(detail);
            }
            if (distance > 52 * 52 || (time + vehicle.getId()) % (distance < 20 * 20 ? 3 : 8) != 0) continue;
            DroneSpec spec = DroneSpec.of(vehicle.profile());
            if (spec == null || vehicle.fragmentPart() != 0) continue;
            Vec3 nozzle = vehicle.exhaustPosition();
            if (vehicle.damageMask() != 0 && !vehicle.onGround() && BlastEffects.claimParticles(client.level, 1) > 0) {
                client.level.addParticle(ParticleTypes.LARGE_SMOKE, nozzle.x, nozzle.y, nozzle.z, .02, .045, .012);
            }
            if (!vehicle.running() || spec.engine() == DroneSpec.Engine.ELECTRIC) continue;
            double wind = Math.sin(time * .028) * .025;
            double sin = Math.sin(Math.toRadians(vehicle.getYRot())), cos = Math.cos(Math.toRadians(vehicle.getYRot()));
            if (BlastEffects.claimParticles(client.level, 1) == 0) continue;
            if (spec.engine() == DroneSpec.Engine.JET) {
                client.level.addParticle(ParticleTypes.FLAME, nozzle.x, nozzle.y, nozzle.z,
                        sin * .18, Math.sin(time * 2) * .012, -cos * .18);
                if (distance < 27 * 27) {
                    GlowSprites.add(client.level, nozzle, GlowSprites.Style.JET);
                    GlowSprites.add(client.level, nozzle.add(sin * .46, .03, -cos * .46), GlowSprites.Style.HEAT);
                }
            } else {
                client.level.addParticle(ParticleTypes.SMOKE, nozzle.x, nozzle.y, nozzle.z,
                        sin * .05 + wind, .024, -cos * .05);
                if (distance < 16 * 16 && (time + vehicle.getId()) % 15 == 0 && BlastEffects.claimParticles(client.level, 1) > 0) {
                    client.level.addParticle(ParticleTypes.SMALL_FLAME, nozzle.x, nozzle.y, nozzle.z,
                            sin * .05, .014, -cos * .05);
                }
            }
        }
    }

    private static double airRangeSquared() {
        int range = ModConfig.DRONE_AUDIO_RANGE.get();
        return (double) range * range;
    }

    private record EngineLayers(EngineSound main, EngineSound detail) {}

    private static boolean audible(Minecraft client, VehicleEntity vehicle) {
        return client.level == vehicle.level() && client.player != null && vehicle.isAlive() && vehicle.running()
                && vehicle.distanceToSqr(client.gameRenderer.getMainCamera().getPosition()) <= (vehicle.profile().air() ? airRangeSquared() : GROUND_RANGE_SQ);
    }

    private static final class EngineSound extends AbstractTickableSoundInstance implements ConcussionAudio.Bandwidth {
        private final VehicleEntity vehicle;
        private final VehicleHandling handling;
        private final boolean detail;
        private final DroneSpec spec;
        private final EngineAcoustics acoustics;
        private double priorDistance = Double.POSITIVE_INFINITY;
        private int flybyCooldown;

        EngineSound(VehicleEntity vehicle, boolean detail, EngineAcoustics acoustics) {
            super(detail ? ModSounds.engineDetail(vehicle.profile())
                    : ModSounds.vehicle(vehicle.profile()), SoundSource.NEUTRAL, RandomSource.create());
            this.vehicle = vehicle;
            this.detail = detail;
            this.acoustics = acoustics;
            this.spec = DroneSpec.of(vehicle.profile());
            if (spec != null) attenuation = Attenuation.NONE;
            this.handling = VehicleHandling.of(vehicle.profile());
            looping = true;
            delay = 0;
            tick();
        }

        @Override
        public void tick() {
            if (!audible(Minecraft.getInstance(), vehicle)) {
                stop();
                return;
            }
            x = vehicle.getX();
            y = vehicle.getY();
            z = vehicle.getZ();
            float speed = (float) Math.hypot(vehicle.getX() - vehicle.xo, vehicle.getZ() - vehicle.zo);
            float throttle = vehicle.profile().air() ? vehicle.throttlePercent() / 100F
                    : Mth.clamp(speed / (float) vehicle.profile().speed, 0, 1);
            float enginePitch = switch (handling) {
                case QUAD -> 1.15F + 0.42F * throttle;
                case DELTA_JET, TRAINER_JET -> 0.80F + 0.40F * throttle;
                case PROPELLER -> 0.70F + 0.50F * throttle;
                default -> 0.70F + 0.33F * throttle;
            };
            var client = Minecraft.getInstance();
            Vec3 listener = client.gameRenderer.getMainCamera().getPosition();
            double length = listener.distanceTo(vehicle.position());
            if (spec != null) {
                boolean cabin = PilotView.onboardAudio(client, vehicle);
                Vec3 velocity = new Vec3(vehicle.getX() - vehicle.xo, vehicle.getY() - vehicle.yo,
                        vehicle.getZ() - vehicle.zo);
                if (!detail) acoustics.update(client, vehicle.position(), velocity, cabin);
                if (cabin) { x = listener.x; y = listener.y; z = listener.z; }
            }
            if (flybyCooldown > 0) flybyCooldown--;
            if (!detail && flybyCooldown == 0 && spec != null && !PilotView.onboardAudio(client, vehicle)
                    && speed >= .27F && priorDistance <= 14 && length > priorDistance + .02 && length > 1.4) {
                client.level.playLocalSound(x, y, z, ModSounds.flyby(vehicle.profile()),
                        SoundSource.NEUTRAL, .45F, acoustics.pitch(), false);
                flybyCooldown = 70;
            }
            priorDistance = length;
            pitch = enginePitch * acoustics.pitch() * (spec == null ? 1 : spec.enginePitch());
            volume = (detail ? .13F + .16F * throttle
                    : handling == VehicleHandling.QUAD ? .22F + .15F * throttle : .34F + .41F * throttle)
                    * acoustics.volume();
        }

        @Override
        public float highFrequencyGain() { return acoustics.highFrequencyGain(); }

    }
}
