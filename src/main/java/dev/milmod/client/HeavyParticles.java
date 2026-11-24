package dev.milmod.client;

import dev.milmod.registry.ModContent;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;

final class HeavyParticles {
    private HeavyParticles() {}

    static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModContent.FLASH_CORE.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new FlashCoreParticle(level, x, y, z, sprites));
        event.registerSpriteSet(ModContent.FIRE_SMOKE_PLUME.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new FireSmokePlumeParticle(level,x,y,z,dx,dy,dz,sprites));
        event.registerSpriteSet(ModContent.EXPLOSION_SPARK.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new ExplosionSparkParticle(level,x,y,z,dx,dy,dz,sprites));
    }

    private abstract static class Visual extends TextureSheetParticle {
        Visual(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z);
            pickSprite(sprites);
        }

        @Override
        public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    }

    private static final class FlashCoreParticle extends Visual {
        FlashCoreParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level,x,y,z,sprites);
            lifetime = 3;
            quadSize = 8F + random.nextFloat() * 2F;
            setColor(1F,.78F,.40F);
            hasPhysics = false;
        }

        @Override public int getLightColor(float partial) { return LightTexture.FULL_BRIGHT; }

        @Override public void tick() {
            super.tick();
            setAlpha(Math.max(0F, 1F - age / 3F));
        }
    }

    private static final class FireSmokePlumeParticle extends Visual {
        private final float maxSize;

        FireSmokePlumeParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level,x,y,z,sprites);
            xd=dx; yd=dy; zd=dz;
            lifetime = 350 + random.nextInt(251);
            friction = .86F;
            hasPhysics = false;
            maxSize = 5.5F + random.nextFloat();
            quadSize = .6F + random.nextFloat();
            setColor(1F,.45F,.1F);
            setAlpha(.58F);
        }

        @Override public int getLightColor(float partial) {
            return age < 12 ? LightTexture.FULL_BRIGHT : super.getLightColor(partial);
        }

        @Override public void tick() {
            if (age >= 8) {
                friction = .965F;
                double wind = Math.sin((level.getGameTime() + 19) * .022) * .010;
                xd = Mth.lerp(.040,xd,.014 + wind);
                zd = Mth.lerp(.04,zd,.012);
                yd = Mth.lerp(.12,yd,.025);
            }
            super.tick();
            float cooling = Mth.clamp((age - 7) / 25F,0,1);
            setColor(Mth.lerp(cooling,1F,.08F), Mth.lerp(cooling,.45F,.08F), Mth.lerp(cooling,.1F,.09F));
            quadSize = Mth.lerp(Mth.clamp((float) Math.sqrt(age / 115F),0,1),1F,maxSize);
            setAlpha(.53F * Math.min(1F,(lifetime - age) / 115F));
        }
    }

    private static final class ExplosionSparkParticle extends Visual {
        private int bounces;

        ExplosionSparkParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level,x,y,z,sprites);
            xd=dx; yd=dy; zd=dz;
            lifetime = 26 + random.nextInt(30);
            gravity = .29F;
            friction = .94F;
            quadSize = .10F + random.nextFloat() * .09F;
            setColor(1F,.63F,.19F);
        }

        @Override public int getLightColor(float partial) { return LightTexture.FULL_BRIGHT; }

        @Override public void move(double x, double y, double z) {
            // Particle.move permanently stops particles on near-zero vertical collision.
            // Resolve the same block shapes here so a reflected velocity can move next tick.
            var box = getBoundingBox();
            Vec3 moved = Entity.collideBoundingBox(null, new Vec3(x,y,z), box, level, List.of());
            setBoundingBox(box.move(moved));
            setLocationFromBoundingbox();
            if (Math.abs(x - moved.x) > 1.0E-6) xd *= -.40;
            if (Math.abs(z - moved.z) > 1.0E-6) zd *= -.40;
            boolean floor = y < 0 && Math.abs(y - moved.y) > 1.0E-6;
            onGround = floor;
            if (floor && y < -.05 && bounces < 3) {
                yd = -y * .48;
                xd *= .75;
                zd *= .75;
                onGround = false;
                bounces++;
            } else if (Math.abs(y - moved.y) > 1.0E-6) {
                yd = 0;
            }
        }

        @Override public void tick() {
            super.tick();
            setAlpha(Math.max(0F, 1F - age / (float) lifetime));
        }

    }
}
