package dev.milmod.client;

import dev.milmod.MilMod;
import dev.milmod.config.ModConfig;
import dev.milmod.entity.ImpactCharge;
import dev.milmod.network.HeavyExplosionPayload;
import dev.milmod.registry.ModContent;
import dev.milmod.sound.ModSounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MilMod.ID, value = Dist.CLIENT)
public final class HeavyExplosionClient {
    private static final int MAX_WAVES = 24;
    private static final int MAX_WHISTLES = 8;
    private static final int MAX_PLUMES = 270;
    private static final ArrayDeque<Long> PLUME_EXPIRY = new ArrayDeque<>();
    private static final ArrayList<Wave> WAITING = new ArrayList<>();
    private static final ArrayList<Impact> IMPACTS = new ArrayList<>();
    private static final Map<Integer, Whistle> WHISTLES = new HashMap<>();
    private static ClientLevel level;
    private static LocalPlayer player;
    private static long nextAudioRetry;
    private static long stunnedAt, recoverAt, screenFlashAt;
    private static float screenFlash;
    private static volatile float hearing = 1;
    private static EarSound ringing, pulse;

    private HeavyExplosionClient() {}

    public static float highFrequencyGain() { return hearing; }

    public static void receive(HeavyExplosionPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || !client.player.isAlive() || !Float.isFinite(payload.yield())) return;
        updateLevel(client);
        Vec3 pos = payload.pos();
        if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)) return;
        var camera = client.gameRenderer.getMainCamera();
        Vec3 delta = pos.subtract(camera.getPosition());
        double distance = delta.length();
        if (distance > 168) return;
        float strength = Mth.clamp(payload.yield(), .5F, 7F);
        boolean far = distance > 48, veryFar = distance > 92;
        long now = level.getGameTime();
        if (WAITING.size() >= MAX_WAVES) WAITING.remove(0);
        WAITING.add(new Wave(pos, strength, now + (int) (distance / 343.0 * 20)));
        double view = delta.normalize().dot(new Vec3(camera.getLookVector()));
        if (distance < 4) view = 1;
        float incomingFlash = (float) (Mth.clamp((view + .19) / 1.19,0,1)
                * Mth.clamp(1 - distance / 96,0,1) * (occluded(client,pos) ? .24 : 1));
        screenFlash = Math.max(incomingFlash, screenFlash * Mth.clamp(1 - (now - screenFlashAt) / 4F, 0, 1));
        screenFlashAt = now;
        BlastEffects.play(pos, true, true);
        var random = level.random;
        int cores = BlastEffects.claimParticles(level, far ? 1 : 2);
        for (int i = 0; i < cores; i++) {
            level.addParticle(ModContent.FLASH_CORE.get(), pos.x, pos.y + i * .8, pos.z,0,0,0);
        }
        while (!PLUME_EXPIRY.isEmpty() && PLUME_EXPIRY.peekFirst() <= now) PLUME_EXPIRY.removeFirst();
        int demand = veryFar ? 8 : far ? 24 : 60 + random.nextInt(31);
        int clouds = BlastEffects.claimParticles(level, Math.min(demand, MAX_PLUMES - PLUME_EXPIRY.size()));
        for (int i = 0; i < clouds; i++) {
            PLUME_EXPIRY.addLast(now + 600);
            double heading = random.nextDouble() * Math.PI * 2;
            double velocity = .1 + random.nextDouble() * .55;
            level.addParticle(ModContent.FIRE_SMOKE_PLUME.get(), pos.x, pos.y + .28, pos.z,
                    Math.cos(heading)*velocity,.09+random.nextDouble()*.30,Math.sin(heading)*velocity);
        }
        int sparks = BlastEffects.claimParticles(level, veryFar ? 1 : far ? 12 : 35 + random.nextInt(16));
        for (int i = 0; i < sparks; i++) {
            double heading = random.nextDouble() * Math.PI * 2;
            double speed = .25 + random.nextDouble() * .95;
            level.addParticle(ModContent.EXPLOSION_SPARK.get(), pos.x, pos.y+.35,pos.z,
                    Math.cos(heading)*speed,.23+random.nextDouble()*.80,Math.sin(heading)*speed);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        updateLevel(client);
        if (level == null || client.isPaused()) return;
        long now = level.getGameTime();
        WAITING.removeIf(wave -> {
            if (wave.at > now) return false;
            playWave(client, wave, now);
            return true;
        });
        IMPACTS.removeIf(impact -> now - impact.born >= 24);
        float intensity = intensity(now);
        boolean audio = ModConfig.CONCUSSION_AUDIO.get() && intensity > 0;
        hearing = audio ? 1F - .95F * intensity * intensity : 1F;
        if (!audio) {
            stopEarSounds(client);
        } else if (now >= nextAudioRetry) {
            updateEarSounds(client);
            nextAudioRetry = now + 20;
        }
        WHISTLES.values().removeIf(sound -> sound.isStopped() || !client.getSoundManager().isActive(sound));
        if (WHISTLES.size() >= MAX_WHISTLES || now % 4 != 0) return;
        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        for (var entity : level.entitiesForRendering()) {
            if (WHISTLES.size() >= MAX_WHISTLES) break;
            if (!(entity instanceof ImpactCharge charge) || charge.distanceToSqr(camera) > 72 * 72
                    || charge.getDeltaMovement().y > -.02 || WHISTLES.containsKey(charge.getId())) continue;
            var whistle = new Whistle(level, charge);
            WHISTLES.put(charge.getId(),whistle);
            client.getSoundManager().play(whistle);
        }
    }

    private static void playWave(Minecraft client, Wave wave, long now) {
        double distance = wave.pos.distanceTo(client.gameRenderer.getMainCamera().getPosition());
        if (distance >= 160) return;
        boolean muffled = occluded(client,wave.pos);
        SoundEvent sound = muffled ? ModSounds.EXPLOSION_MUFFLED.get() : ModSounds.EXPLOSION_CRACK.get();
        float volume = (float) (Math.sqrt(1 - distance / 160) * (muffled ? .78 : 1));
        client.getSoundManager().play(new SimpleSoundInstance(sound.getLocation(), SoundSource.BLOCKS, volume, 1F,
                level.random, false, 0,
                net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE, wave.pos.x, wave.pos.y, wave.pos.z, false));
        if (muffled && distance < 95) {
            playLocal(client, ModSounds.GLASS_RATTLE.get(), (float) (.48 * (1-distance/95)));
        }
        float impact = (float) Mth.clamp(1-distance/80,0,1) * (muffled ? 1.3F : 1F);
        if (IMPACTS.size() == MAX_WAVES) IMPACTS.remove(0);
        IMPACTS.add(new Impact(now, impact));
        double range = 28 * Mth.clamp(Math.sqrt(wave.strength / 2.8),.65,1.4);
        if (distance < range) {
            int ticks = 120 + (int) (40 * (1 - distance/range));
            stunnedAt = now;
            recoverAt = Math.max(recoverAt,now+ticks);
            nextAudioRetry = now;
        }
    }

    private static void updateEarSounds(Minecraft client) {
        var sounds = client.getSoundManager();
        if (ringing == null || ringing.isStopped() || !sounds.isActive(ringing)) {
            ringing = new EarSound(ModSounds.TINNITUS.get(), .28F);
            sounds.play(ringing);
        }
        if (pulse == null || pulse.isStopped() || !sounds.isActive(pulse)) {
            pulse = new EarSound(ModSounds.HEARTBEAT.get(), .48F);
            sounds.play(pulse);
        }
    }

    private static void stopEarSounds(Minecraft client) {
        if (ringing != null) client.getSoundManager().stop(ringing);
        if (pulse != null) client.getSoundManager().stop(pulse);
        ringing = null;
        pulse = null;
    }

    private static void playLocal(Minecraft client, SoundEvent sound, float volume) {
        client.getSoundManager().play(SimpleSoundInstance.forUI(sound,1F,volume));
    }

    private static boolean occluded(Minecraft client, Vec3 point) {
        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        Vec3 direction = point.subtract(camera);
        if (direction.lengthSqr() < .04) return false;
        // Stop short of the impact surface itself. A wall between listener and impact still occludes.
        Vec3 end = point.subtract(direction.normalize().scale(.19));
        return level.clip(new ClipContext(camera,end,
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,client.player)).getType() == HitResult.Type.BLOCK;
    }

    private static void updateLevel(Minecraft client) {
        LocalPlayer alive = client.player != null && client.player.isAlive() ? client.player : null;
        ClientLevel connected = alive == null ? null : client.level;
        if (connected == level && alive == player) return;
        stopEarSounds(client);
        for (var whistle : WHISTLES.values()) client.getSoundManager().stop(whistle);
        WHISTLES.clear();
        WAITING.clear();
        PLUME_EXPIRY.clear();
        IMPACTS.clear();
        recoverAt = stunnedAt = screenFlashAt = nextAudioRetry = 0;
        screenFlash = 0;
        hearing = 1;
        player = alive;
        level = connected;
    }

    static float intensity(double now) {
        return now >= recoverAt ? 0 : Mth.clamp((float) ((recoverAt - now) / 100),0,1);
    }

    @SubscribeEvent
    public static void shake(ViewportEvent.ComputeCameraAngles event) {
        if (level == null || !ModConfig.CAMERA_SHAKE.get()) return;
        double now = level.getGameTime() + event.getPartialTick();
        double pitchOffset = 0, yawOffset = 0, rollOffset = 0;
        for (Impact impact : IMPACTS) {
            double t = (now-impact.born)/20;
            double envelope = impact.amplitude*4.2*Math.exp(-t*6);
            pitchOffset += envelope * Math.sin(t * 45);
            yawOffset += envelope * .54 * Math.sin(t * 48 + 1);
            rollOffset += envelope * .61 * Math.sin(t * 45 + 2);
        }
        double pulse = Math.sin((now-stunnedAt)*.402);
        float strength = intensity(now);
        event.setRoll(event.getRoll() + (float) Mth.clamp(rollOffset + strength * (Math.sin(now * .12) + pulse * .38), -5, 5));
        event.setPitch(event.getPitch() + (float) Mth.clamp(pitchOffset + strength * pulse * .31, -6, 6));
        event.setYaw(event.getYaw() + (float) Mth.clamp(yawOffset, -4, 4));
    }

    @SubscribeEvent
    public static void overlay(RenderGuiEvent.Post event) {
        if (level == null) return;
        double now = level.getGameTime()+event.getPartialTick();
        var gui = event.getGuiGraphics();
        int w=gui.guiWidth(), h=gui.guiHeight();
        double age=now-screenFlashAt;
        int flash=(int)(174*screenFlash*Mth.clamp(1-age/4,0,1)*ModConfig.SCREEN_FLASH_STRENGTH.get());
        if (flash>0) gui.fill(0,0,w,h,flash<<24|0xFFE0AE);
        float strength=intensity(now);
        if (strength<=0) return;
        int alpha=(int)((110+35*Math.sin((now-stunnedAt)*.402))*strength);
        int dark=alpha<<24;
        gui.fillGradient(0,0,w,h/4,dark,0);
        gui.fillGradient(0,h-h/4,w,h,0,dark);
        for (int i=0; i<16; i++) {
            int from=i*w/112, to=(i+1)*w/112, tint=(alpha*(16-i)/24)<<24;
            gui.fill(from,h/4,to,h-h/4,tint);
            gui.fill(w-to,h/4,w-from,h-h/4,tint);
        }
    }

    private record Wave(Vec3 pos,float strength,long at) {}
    private record Impact(long born,float amplitude) {}

    private static final class EarSound extends AbstractTickableSoundInstance {
        private final float gain;
        EarSound(SoundEvent sound,float gain) {
            super(sound,SoundSource.PLAYERS,RandomSource.create());
            this.gain=gain;
            looping=true; relative=true; attenuation=Attenuation.NONE; volume=gain;
        }
        @Override public void tick() {
            if (level == null || intensity(level.getGameTime()) <= 0) stop();
            else volume=gain*intensity(level.getGameTime());
        }
    }

    private static final class Whistle extends AbstractTickableSoundInstance {
        private final ClientLevel world;
        private final ImpactCharge charge;
        Whistle(ClientLevel world, ImpactCharge charge) {
            super(ModSounds.FALLING_WHISTLE.get(),SoundSource.NEUTRAL,RandomSource.create());
            this.world=world; this.charge=charge;
            looping=true; volume=.25F; pitch=.65F;
            tick();
        }

        @Override public void tick() {
            if (world != Minecraft.getInstance().level || !charge.isAlive()
                    || charge.distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition())>72*72) {
                stop(); return;
            }
            x=charge.getX(); y=charge.getY(); z=charge.getZ();
            int ground=world.getHeight(Heightmap.Types.MOTION_BLOCKING,charge.getBlockX(),charge.getBlockZ());
            double near=Mth.clamp(1-(y-ground)/50,0,1);
            pitch=Mth.lerp(.18F,pitch,(float)(.65+.80*near));
            volume=(float)(.20+.32*near);
        }
    }
}
