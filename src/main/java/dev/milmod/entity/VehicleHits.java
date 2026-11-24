package dev.milmod.entity;

import dev.milmod.MilMod;
import java.lang.reflect.Method;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VehicleHits {
    private static final Logger LOGGER = LoggerFactory.getLogger(MilMod.ID);
    private static final String SBW_EVENT = "com.atsuishio.superbwarfare.api.event.ProjectileHitEvent$HitEntity";

    private VehicleHits() {}

    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.LOWEST, VehicleHits::vanillaImpact);
    }

    public static void registerOptional(IEventBus bus) {
        if (!ModList.get().isLoaded("superbwarfare")) return;
        try {
            Class<? extends Event> eventType = Class.forName(SBW_EVENT).asSubclass(Event.class);
            Method getResult = eventType.getMethod("getResult");
            Method getProjectile = eventType.getMethod("getProjectile");
            bus.addListener(EventPriority.LOWEST, false, eventType,
                    event -> rememberOptionalHit(event, getResult, getProjectile));
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.warn("Could not register the optional Superb Warfare impact listener", exception);
        }
    }

    private static void vanillaImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult result) {
            remember(result, event.getProjectile());
        }
    }

    private static void rememberOptionalHit(Event event, Method getResult, Method getProjectile) {
        try {
            if (getResult.invoke(event) instanceof EntityHitResult result
                    && getProjectile.invoke(event) instanceof Entity projectile) {
                remember(result, projectile);
            }
        } catch (ReflectiveOperationException exception) {
            LOGGER.debug("Could not read the Superb Warfare projectile impact", exception);
        }
    }

    private static void remember(EntityHitResult result, Entity direct) {
        if (result.getEntity() instanceof VehicleEntity target && !target.level().isClientSide) {
            Vec3 point = result.getLocation();
            // Some vanilla hits carry only the target position. In that case use the projectile segment in hurt().
            if (point.distanceToSqr(target.position()) > 1.0E-10) target.rememberImpact(direct, point);
        }
    }

    static Vec3 impactPoint(VehicleEntity vehicle, DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct == null) return null;
        Vec3 cached = vehicle.recentImpact(direct);
        if (cached != null) return cached;
        Vec3 position = direct.position();
        if (direct == source.getEntity()) return source.getSourcePosition();
        Vec3 velocity = direct.getDeltaMovement();
        if (velocity.lengthSqr() < 1.0E-7) return position;
        Vec3 start = position.subtract(velocity);
        Vec3 end = position.add(velocity);
        return vehicle.getBoundingBox().clip(start, end).orElse(position);
    }
}
