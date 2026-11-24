package dev.milmod.entity;

import dev.milmod.registry.ModContent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ImpactCharge extends ThrowableItemProjectile {
    private static final int MAX_FLIGHT_TICKS = 240;
    private int flightAge;
    private boolean spent;
    private boolean blockContactOnly;

    public ImpactCharge(EntityType<? extends ImpactCharge> type, Level level) {
        super(type, level);
    }

    static ImpactCharge release(VehicleEntity carrier, Vec3 origin) {
        ImpactCharge charge = new ImpactCharge(ModContent.IMPACT_CHARGE.get(), carrier.level());
        charge.setOwner(carrier);
        charge.setPos(origin);
        charge.setDeltaMovement(carrier.getDeltaMovement().scale(0.72).add(0, -0.13, 0));
        return charge;
    }

    static ImpactCharge releaseOnBlock(VehicleEntity carrier, Player operator, Vec3 origin) {
        ImpactCharge charge = new ImpactCharge(ModContent.IMPACT_CHARGE.get(), carrier.level());
        charge.blockContactOnly = true;
        charge.setOwner(operator);
        charge.setPos(origin);
        charge.setDeltaMovement(0, -.07, 0);
        return charge;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return !blockContactOnly && super.canHitEntity(entity);
    }

    @Override
    protected Item getDefaultItem() { return ModContent.DUMMY_KAB.get(); }

    @Override
    protected float getGravity() { return 0.052F; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || isRemoved()) return;
        if (!blockContactOnly && isInWater()) burst(position());
        else if (++flightAge >= MAX_FLIGHT_TICKS) discard();
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!(level() instanceof ServerLevel)) return;
        Vec3 impact = hit.getLocation();
        if (hit instanceof BlockHitResult block) {
            impact = impact.add(Vec3.atLowerCornerOf(block.getDirection().getNormal()).scale(0.07));
        } else if (blockContactOnly) return;
        burst(impact);
    }

    private void burst(Vec3 impact) {
        if (spent || !(level() instanceof ServerLevel server)) return;
        spent = true;
        if (blockContactOnly) HeavyExplosionManager.detonateKab(server, impact, this);
        else HeavyExplosionManager.detonate(server, impact, 2.8F, this);
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("FlightAge", flightAge);
        if (blockContactOnly) tag.putBoolean("BlockContactOnly", true);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        flightAge = Math.max(0, tag.getInt("FlightAge"));
        blockContactOnly = tag.getBoolean("BlockContactOnly") || tag.getBoolean("VisualImpact");
    }
}
