package dev.milmod.entity;

import dev.milmod.model.VehicleProfile;
import dev.milmod.model.DroneSpec;
import dev.milmod.config.ModConfig;
import net.minecraft.core.Direction;
import dev.milmod.model.VehicleSuspension;
import dev.milmod.item.TabletItem;
import dev.milmod.item.VehicleItem;
import dev.milmod.model.VehicleOperation;
import java.util.Optional;
import net.minecraft.world.entity.EquipmentSlot;
import dev.milmod.sound.ModSounds;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerLevel;
import dev.milmod.registry.ModContent;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public final class VehicleEntity extends Entity {
    private static final EntityDataAccessor<Integer> PROFILE =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DAMAGE =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> OPEN =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> BANK =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> RUNNING =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Byte> STEERING =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BYTE);

    private static final EntityDataAccessor<Byte> PAYLOAD =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DOCKED =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> SUSPENSION =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DISPLAY_START =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.LONG);
    private final DisplaySequence display = new DisplaySequence();

    private final VehicleSupportPhysics supportPhysics = new VehicleSupportPhysics();
    private final float[] suspension = new float[16];
    private final float[] previousSuspension = new float[16];
    private float turnTravel;
    private float previousTurnTravel;
    private static final EntityDataAccessor<Optional<UUID>> OPERATOR =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> FUEL =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final String REMOTE_KEY = "MilModRemote";
    private static final int FULL_FUEL = 2400;
    private Vec3 operatorPosition;
    private static final EntityDataAccessor<Byte> FRAGMENT =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> LOST_PARTS =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BYTE);
    private int fragmentAge;
    private UUID railId;
    private static final EntityDataAccessor<Integer> FLIGHT_PHASE =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> THROTTLE =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> MODEM =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> START_HITS =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MISSION_STATUS =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    public enum FlightPhase { READY, LOADED, LAUNCHING, AIRBORNE, LANDED, SPOOLING, TAXI }
    private int spoolAge;
    private boolean handPrimed;
    private int runwayAssistTicks;
    private long nextStartHit;
    private final FlightMission mission = new FlightMission();
    private float power;
    private int poweredTicks;
    private int fuelRemainder;
    private boolean engineExpired;
    private int launchTicks;
    private double launchDistance;
    private double baseAltitude;
    private float heldYaw, heldPitch;
    private long dockMissingSince = -1L;
    private int payloadCooldown;

    private float integrity;
    private float leftDamage;
    private float rightDamage;
    private float oldBank;
    private static final EntityDataAccessor<Integer> CONTROL_SURFACES =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private final float[] surfaces = new float[3], previousSurfaces = new float[3];
    private float gear, oldGear;
    private boolean retractGear;
    private float renderedBank;
    private float rotorAngle, oldRotorAngle, rotorSpeed;
    private float wheelTravel;
    private float previousWheelTravel;
    private UUID owner;
    private float featurePose;
    private float previousFeaturePose;
    private int remainingLerp;
    private double targetX, targetY, targetZ;
    private float targetYaw, targetPitch;
    private boolean controlsActive;
    private long pilotInputAt = Long.MIN_VALUE;
    private float pilotForward;
    private float pilotSide;
    private float pilotYaw;
    private float pilotPitch;
    private boolean pilotClimb;
    private int lastProjectileId;
    private long lastImpactTick = Long.MIN_VALUE;
    private Vec3 lastImpact;
    private int visualMask;
    private int leftDetachedAt;
    private int rightDetachedAt;


    public VehicleEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
        blocksBuilding = true;
        integrity = profile().durability;
        setMaxUpStep(0.55F);
        refreshDimensions();
    }

    public static VehicleEntity create(Level level, VehicleProfile profile) {
        VehicleEntity vehicle = new VehicleEntity(ModContent.VEHICLE.get(), level);
        vehicle.entityData.set(PROFILE, profile.ordinal());
        vehicle.integrity = profile.durability;
        return vehicle;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(PROFILE, VehicleProfile.MAVIC3T.ordinal());
        entityData.define(DAMAGE, 0);
        entityData.define(OPEN, false);
        entityData.define(MODEM, false);
        entityData.define(THROTTLE, 0);
        entityData.define(FLIGHT_PHASE, FlightPhase.READY.ordinal());
        entityData.define(START_HITS, 0);
        entityData.define(MISSION_STATUS, 0);
        entityData.define(CONTROL_SURFACES, 0);
        entityData.define(DISPLAY_START, -1L);
        entityData.define(BANK, 0F);
        entityData.define(RUNNING, false);
        entityData.define(STEERING, (byte) 0);
        entityData.define(PAYLOAD, (byte) 0);
        entityData.define(DOCKED, false);
        entityData.define(SUSPENSION, 0L);
        entityData.define(OPERATOR, Optional.empty());
        entityData.define(FUEL, 0);
        entityData.define(FRAGMENT, (byte) 0);
        entityData.define(LOST_PARTS, (byte) 0);
    }

    public VehicleProfile profile() { return VehicleProfile.byIndex(entityData.get(PROFILE)); }
    public int damageMask() { return entityData.get(DAMAGE); }
    public float wheelTravel(float partial, float lateral) {
        return Mth.lerp(partial, previousWheelTravel, wheelTravel)
                - lateral * Mth.lerp(partial, previousTurnTravel, turnTravel);
    }

    public void setControlSurfaces(float roll, float pitch, float yaw) {
        int encoded = (Math.round(roll * 100) & 255) | (Math.round(pitch * 100) & 255) << 8
                | (Math.round(yaw * 100) & 255) << 16;
        entityData.set(CONTROL_SURFACES, encoded);
    }

    public float controlSurface(int axis, float partial) {
        return Mth.lerp(partial, previousSurfaces[axis], surfaces[axis]);
    }

    public float gearProgress(float partial) { return Mth.lerp(partial, oldGear, gear); }

    public boolean hasAnimatedGear() {
        return profile() == VehicleProfile.FORPOST_R || profile() == VehicleProfile.MOHAJER_6
                || profile() == VehicleProfile.LYUTYI;
    }

    public float rotorAngle(float partial) { return Mth.lerp(partial, oldRotorAngle, rotorAngle); }

    public float suspensionOffset(int index, float partial) {
        return index < 0 ? 0 : Mth.lerp(partial, previousSuspension[index], suspension[index]);
    }

    float authoritativeBank() { return entityData.get(BANK); }
    double groundGrip() { return supportPhysics.grip(); }
    void setSuspension(long value) { entityData.set(SUSPENSION, value); }

    void moveSupported(double x, double z, boolean powered, double lift) {
        supportPhysics.step(this, x, z, powered, lift);
        checkInsideBlocks();
    }

    void clearSupport() {
        supportPhysics.reset();
        setSuspension(0L);
    }

    public float steering() { return entityData.get(STEERING) / 100F; }

    void setSteering(float steering) {
        entityData.set(STEERING, (byte) Mth.clamp(Math.round(100 * steering), -100, 100));
    }

    public boolean running() { return entityData.get(RUNNING); }
    public float bank(float partial) { return Mth.lerp(partial, oldBank, renderedBank); }
    public float displayTime(float partial) {
        long start = entityData.get(DISPLAY_START);
        return start < 0 ? 0 : Mth.clamp(level().getGameTime() - start + partial, 0, DisplaySequence.END);
    }

    public boolean startDisplay(ServerPlayer driver, BlockPos point) {
        if (profile() != VehicleProfile.ISKANDER_K || getFirstPassenger() != driver || !isAlive()
                || entityData.get(OPEN) || !onGround() || getDeltaMovement().lengthSqr() > .006
                || entityData.get(DISPLAY_START) >= 0 || !display.begin(this, driver.serverLevel(), point)) return false;
        entityData.set(DISPLAY_START, level().getGameTime());
        return true;
    }

    public float feature(float partial) { return Mth.lerp(partial, previousFeaturePose, featurePose); }
    public float integrity() { return integrity; }
    UUID owner() { return owner; }
    void setOwner(UUID value) { owner = value; }

    @Override
    protected Component getTypeName() {
        return Component.translatable("item.milmod." + profile().id);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (fragmentPart() != 0) return EntityDimensions.fixed(.42F, .24F);
        VehicleProfile profile = profile();
        return EntityDimensions.fixed(profile.width, profile.height);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (PROFILE.equals(key) || FRAGMENT.equals(key)) refreshDimensions();
        if (DAMAGE.equals(key) && level().isClientSide) {
            int mask = damageMask();
            if ((mask & 1) != 0 && (visualMask & 1) == 0) leftDetachedAt = tickCount;
            if ((mask & 2) != 0 && (visualMask & 2) == 0) rightDetachedAt = tickCount;
            visualMask = mask;
        }
    }

    public float debrisAge(int part, float partial) {
        return tickCount - (part == 1 ? leftDetachedAt : rightDetachedAt) + partial;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(profile().length * 0.65, profile().height, profile().length * 0.65);
    }

    @Override
    public boolean isPickable() { return isAlive(); }

    @Override
    public float getPickRadius() {
        return profile().air() ? Mth.clamp(profile().width * 0.10F, 0.09F, 0.45F) : 0.18F;
    }

    @Override
    public boolean canBeCollidedWith() { return isAlive(); }

    @Override
    public boolean isPushable() { return false; }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return profile().kind != VehicleProfile.Kind.STATIC && getPassengers().isEmpty();
    }

    @Override
    public double getPassengersRidingOffset() {
        return profile().kind == VehicleProfile.Kind.HOVER ? 0.15 : profile().height * 0.68;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        if (hasPassenger(passenger)) {
            Vec3 seat = seatPosition(getYRot(), getX(), getY(), getZ());
            move.accept(passenger, seat.x, seat.y, seat.z);
        }
    }

    public Vec3 seatPosition(float yaw, double x, double y, double z) {
        double heading = Math.toRadians(yaw);
        double longitudinal = VehicleOperation.seatLongitudinal(profile());
        double lateral = profile() == VehicleProfile.GAZ_TIGR || profile() == VehicleProfile.ISKANDER_K ? .27 : 0;
        return new Vec3(x + longitudinal * Math.sin(heading) - lateral * Math.cos(heading),
                y + profile().height * .28, z - longitudinal * Math.cos(heading) - lateral * Math.sin(heading));
    }

    public int fuelPercent() { return Math.round(entityData.get(FUEL) * 100F / FULL_FUEL); }
    boolean hasFuel() { return !VehicleOperation.needsFuel(profile()) || entityData.get(FUEL) > 0; }
    public boolean modemInstalled() { return entityData.get(MODEM); }

    public boolean hasFlightCamera() {
        DroneSpec spec = DroneSpec.of(profile());
        return spec != null && (spec.hasCamera() || spec.meshSlot() && modemInstalled());
    }

    public FlightPhase flightPhase() { return FlightPhase.values()[entityData.get(FLIGHT_PHASE)]; }
    private void setFlightPhase(FlightPhase next) { entityData.set(FLIGHT_PHASE, next.ordinal()); }
    public int startHitsRemaining() { return entityData.get(START_HITS); }
    public boolean handStartReady() {
        DroneSpec spec = DroneSpec.of(profile());
        if (spec == null) return false;
        if (!spec.handStart()) return true;
        return flightPhase() == FlightPhase.LOADED || flightPhase() == FlightPhase.AIRBORNE
                || flightPhase() == FlightPhase.TAXI;
    }
    public int missionStatus() { return entityData.get(MISSION_STATUS); }
    public double missionRange() {
        DroneSpec spec = DroneSpec.of(profile());
        return spec == null ? 0 : spec.meshSlot() ? spec.configuredMeshRange(ModConfig.MESH_RANGE.get()) : ModConfig.MESH_RANGE.get();
    }
    public double radioRange() {
        DroneSpec spec = DroneSpec.of(profile());
        return modemInstalled() && spec != null ? spec.configuredMeshRange(ModConfig.MESH_RANGE.get()) : 160;
    }
    private void publishMission() { entityData.set(MISSION_STATUS, mission.active() ? mission.mode().ordinal() + 1 : 0); }
    public int throttlePercent() { return entityData.get(THROTTLE); }
    float power() { return power; }
    double launchDistance() { return launchDistance; }
    void advanceLaunch(double distance) { launchDistance += distance; }
    double baseAltitude() { return baseAltitude; }
    float heldYaw() { return heldYaw; }
    float heldPitch() { return heldPitch; }
    boolean onMission() { return mission.active(); }

    void setPower(float value) {
        power = Mth.clamp(value, 0F, 1F);
        entityData.set(THROTTLE, Math.round(power * 100));
    }

    void finishLaunch() {
        launchTicks = 0;
        railId = null;
        entityData.set(DOCKED, false);
        setFlightPhase(FlightPhase.AIRBORNE);
    }

    void abortLaunch(VehicleEntity stand) {
        boolean retryable = stand != null && stand.position().distanceToSqr(position()) < 6;
        poweredTicks = 0;
        controlsActive = false;
        mission.clear();
        setPower(0);
        setActivity(false, authoritativeBank());
        publishMission();
        launchTicks = 0;
        spoolAge = 0;
        if (retryable) {
            // Retain the cradle reservation and current position. A later retry continues from the carriage.
            setDeltaMovement(Vec3.ZERO);
            setFlightPhase(FlightPhase.LOADED);
        } else {
            railId = null;
            entityData.set(DOCKED, false);
            setFlightPhase(FlightPhase.LANDED);
        }
        if (level() instanceof ServerLevel server && entityData.get(OPERATOR).isPresent()) {
            ServerPlayer operator = server.getServer().getPlayerList().getPlayer(entityData.get(OPERATOR).orElseThrow());
            if (operator != null) stopRemote(operator);
            else entityData.set(OPERATOR, Optional.empty());
        }
        playSound(ModSounds.failure(profile()), .7F, 1F);
    }

    public boolean remotelyControlledBy(Player player) {
        return entityData.get(OPERATOR).filter(player.getUUID()::equals).isPresent();
    }

    public static VehicleEntity controlledBy(ServerPlayer player) {
        if (player.getVehicle() instanceof VehicleEntity vehicle) return vehicle;
        var data = player.getPersistentData();
        if (!data.hasUUID(REMOTE_KEY)) return null;
        if (player.serverLevel().getEntity(data.getUUID(REMOTE_KEY)) instanceof VehicleEntity craft
                && craft.remoteStillValid(player)) return craft;
        data.remove(REMOTE_KEY);
        return null;
    }

    private boolean remoteStillValid(ServerPlayer player) {
        return remotelyControlledBy(player) && player.isAlive() && !player.isSpectator()
                && player.level() == level() && isAlive() && !player.isPassenger() && operatorPosition != null
                && player.position().distanceToSqr(operatorPosition) <= .85 * .85
                && hasFlightCamera()
                && distanceToSqr(player) <= radioRange() * radioRange()
                && (profile() != VehicleProfile.MAVIC3T || mavicEquipment(player));
    }

    public void stopRemote(ServerPlayer player) {
        if (!remotelyControlledBy(player)) return;
        var data = player.getPersistentData();
        if (data.hasUUID(REMOTE_KEY) && getUUID().equals(data.getUUID(REMOTE_KEY))) data.remove(REMOTE_KEY);
        entityData.set(OPERATOR, Optional.empty());
        operatorPosition = null;
        pilotInputAt = Long.MIN_VALUE;
    }

    private boolean mavicEquipment(Player player) {
        if (!player.getItemBySlot(EquipmentSlot.HEAD).is(ModContent.MAVIC_GOGGLES.get())) return false;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.is(ModContent.MAVIC_CONTROLLER.get()) && getUUID().equals(TabletItem.linked(held))) return true;
        }
        return false;
    }

    public int fragmentPart() { return entityData.get(FRAGMENT); }
    public boolean separated(int part) { return (entityData.get(LOST_PARTS) & part) != 0; }

    void crashAt(Vec3 point) {
        if (level() instanceof ServerLevel server) {
            if (profile() == VehicleProfile.FORPOST_R || profile() == VehicleProfile.MOHAJER_6) {
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                        point.x, point.y, point.z, 12, .28, .22, .28, .08);
                playSound(ModSounds.HIT.get(), .75F, .73F);
            } else if (profile().crashEffect() > 0) {
                FlashLight.place(server, point, 15);
                dev.milmod.network.DroneLink.heavyExplosion(server, point, profile().crashEffect());
            } else {
                dev.milmod.network.DroneLink.blast(server, point);
                playSound(ModSounds.crash(profile()), .85F, 1F);
            }
        }
        ejectPassengers();
        discard();
    }

    void loseWing(int part) {
        if ((damageMask() & part) != 0 || !(level() instanceof ServerLevel server)) return;
        entityData.set(DAMAGE, damageMask() | part);
        controlsActive = true;
        VehicleEntity debris = create(server, profile());
        debris.entityData.set(FRAGMENT, (byte) part);
        int sign = part == 1 ? -1 : 1;
        double yaw = Math.toRadians(getYRot());
        double separation = profile().width * .31 * sign;
        debris.moveTo(getX() - Math.cos(yaw) * separation, getY() + .25,
                getZ() - Math.sin(yaw) * separation, getYRot(), getXRot());
        debris.setDeltaMovement(getDeltaMovement().scale(.74).add(-Math.cos(yaw) * sign * .08, 0,
                -Math.sin(yaw) * sign * .08));
        if (server.addFreshEntity(debris)) entityData.set(LOST_PARTS, (byte) (entityData.get(LOST_PARTS) | part));
        playSound(ModSounds.HIT.get(), 1F, .72F);
    }

    private void tickFragment() {
        int sign = fragmentPart() == 1 ? -1 : 1;
        double drag = onGround() ? .48 : .982;
        Vec3 speed = getDeltaMovement();
        moveVehicle(new Vec3(speed.x * drag, Math.max(-.75, speed.y - .064), speed.z * drag));
        if (!onGround()) {
            setYRot(getYRot() + sign * 7);
            setActivity(false, Mth.wrapDegrees(authoritativeBank() + sign * 14));
        }
        if (++fragmentAge >= 300 || onGround() && fragmentAge > 140) discard();
    }

    public int trainingPayload() { return entityData.get(PAYLOAD); }
    public boolean docked() { return entityData.get(DOCKED); }
    public int payloadCapacity() {
        return switch (profile()) {
            case MAVIC3T -> 1;
            case FORPOST_R -> 2;
            case MOHAJER_6 -> 4;
            default -> 0;
        };
    }

    private boolean acceptsPayload(ItemStack stack) {
        return profile() == VehicleProfile.MAVIC3T && stack.is(ModContent.DUMMY_GRENADE.get())
                || (profile() == VehicleProfile.FORPOST_R || profile() == VehicleProfile.MOHAJER_6)
                && stack.is(ModContent.DUMMY_KAB.get());
    }

    private ItemStack payloadStack(int count) {
        return new ItemStack(profile() == VehicleProfile.MAVIC3T
                ? ModContent.DUMMY_GRENADE.get() : ModContent.DUMMY_KAB.get(), count);
    }

    private boolean availableTo(Player player) {
        return !player.isSpectator() && (owner == null || owner.equals(player.getUUID()));
    }

    public boolean hasKabRack() {
        return profile() == VehicleProfile.FORPOST_R || profile() == VehicleProfile.MOHAJER_6;
    }

    /** The attachment point in the same local coordinates the model renderer uses. */
    public Vec3 payloadMount(int slot) {
        if (profile() == VehicleProfile.MAVIC3T) return new Vec3(0, -.11, 0);
        boolean fourRacks = profile() == VehicleProfile.MOHAJER_6;
        double rack = fourRacks ? (slot < 2 ? .71 : 1.18) : 1.00;
        return new Vec3(slot % 2 == 0 ? -rack : rack, fourRacks ? .035 : .565, fourRacks ? .05 : -.23);
    }

    public boolean releaseRackItem(Player player) {
        if (!hasKabRack() || flightPhase() != FlightPhase.AIRBORNE || !canReleasePayload(player)) return false;
        Vec3 mount = payloadMount(trainingPayload() - 1);
        float pivot = profile().height * .43F;
        Vec3 origin = mount.add(0, -pivot, 0)
                .zRot((float) Math.toRadians(-authoritativeBank()))
                .xRot((float) Math.toRadians(-getXRot()))
                .yRot((float) Math.toRadians(180 - getYRot()))
                .add(getX(), getY() + pivot - .12, getZ());
        return finishPayloadRelease(ImpactCharge.releaseOnBlock(this, player, origin));
    }

    public boolean releaseTrainingPayload(Player player) {
        if (profile() == VehicleProfile.MOHAJER_6 || !canReleasePayload(player)) return false;
        Vec3 origin = position().add(0, -.28, 0);
        if (profile() == VehicleProfile.FORPOST_R) {
            double heading = Math.toRadians(getYRot());
            double rack = trainingPayload() == 2 ? -.68 : .68;
            origin = origin.add(-Math.cos(heading) * rack, 0, -Math.sin(heading) * rack);
            return finishPayloadRelease(ImpactCharge.release(this, origin));
        }
        ItemEntity capsule = new ItemEntity(level(), origin.x, origin.y, origin.z, payloadStack(1));
        capsule.setDefaultPickUpDelay();
        return finishPayloadRelease(capsule);
    }

    private boolean canReleasePayload(Player player) {
        return !level().isClientSide && isAlive() && !onGround() && !docked()
                && trainingPayload() > 0 && payloadCooldown == 0 && damageMask() == 0
                && (getFirstPassenger() == player || remotelyControlledBy(player));
    }

    private boolean finishPayloadRelease(Entity dropped) {
        if (!level().addFreshEntity(dropped)) return false;
        entityData.set(PAYLOAD, (byte) (trainingPayload() - 1));
        payloadCooldown = 6;
        playSound(ModSounds.RELEASE.get(), .8F, 1F);
        return true;
    }

    private boolean createDockedAircraft(Player player, ItemStack kit, VehicleProfile model) {
        if (!(level() instanceof ServerLevel server) || !availableTo(player) || !canHoldAircraft()) return false;
        for (VehicleEntity candidate : server.getEntitiesOfClass(VehicleEntity.class, getBoundingBox().inflate(3))) {
            if (getUUID().equals(candidate.railId)) return false;
        }
        VehicleEntity aircraft = create(server, model);
        aircraft.followRail(this);
        if (!VehiclePlacement.add(server, aircraft, player, this)) return false;
        aircraft.railId = getUUID();
        aircraft.entityData.set(DOCKED, true);
        aircraft.setFlightPhase(FlightPhase.LOADED);
        if (!player.getAbilities().instabuild) kit.shrink(1);
        playSound(ModSounds.ATTACH.get(), 0.75F, 1F);
        return true;
    }

    private boolean canHoldAircraft() {
        BlockPos support = BlockPos.containing(getX(), getY() - .06, getZ());
        return isAlive() && level().hasChunkAt(support) && getDeltaMovement().lengthSqr() <= .022
                && level().getBlockState(support).isFaceSturdy(level(), support, Direction.UP);
    }

    private VehicleEntity linkedStand() {
        if (railId != null && level() instanceof ServerLevel server
                && server.getEntity(railId) instanceof VehicleEntity stand
                && stand.isAlive() && VehicleOperation.accepts(stand.profile(), profile())) return stand;
        return null;
    }

    private void followRail(VehicleEntity rail) {
        float rise = profile().kind == VehicleProfile.Kind.HOVER ? .76F : .82F;
        float pivot = rail.profile().height * .43F;
        Vec3 seat = new Vec3(0, rise - pivot, 0)
                .zRot((float) Math.toRadians(-rail.authoritativeBank()))
                .xRot((float) Math.toRadians(-rail.getXRot()))
                .yRot((float) Math.toRadians(180 - rail.getYRot()))
                .add(rail.getX(), rail.getY() + pivot, rail.getZ());
        setPos(seat);
        setYRot(rail.getYRot());
        setXRot(rail.getXRot() + (profile().kind == VehicleProfile.Kind.HOVER ? 0 : 8));
        setActivity(false, rail.authoritativeBank());
        setDeltaMovement(rail.getDeltaMovement());
    }

    private boolean canLaunch(ServerPlayer player) {
        if (!isAlive() || !player.isAlive() || player.isSpectator() || player.isPassenger()
                || player.level() != level() || damageMask() != 0 || !profile().air() || !availableTo(player) || launchTicks != 0) return false;
        if (VehicleOperation.railOnly(profile()) && !docked()
                && (onGround() || flightPhase() != FlightPhase.AIRBORNE)) {
            player.displayClientMessage(Component.translatable("message.milmod.rail_required"), true);
            return false;
        }
        if (docked()) {
            VehicleEntity stand = linkedStand();
            if (stand == null || !stand.canHoldAircraft()) return false;
        }
        if (!hasFuel()) {
            player.displayClientMessage(Component.translatable("message.milmod.needs_fuel"), true);
            return false;
        }
        if (!docked() && DroneSpec.of(profile()).handStart() && !handStartReady()) {
            player.displayClientMessage(Component.translatable("message.milmod.hand_prime"), true);
            return false;
        }
        return true;
    }

    public boolean startFromTablet(Player player) {
        if (!(player instanceof ServerPlayer operator) || !hasFlightCamera()) return false;
        if (!operator.isAlive() || operator.isSpectator() || operator.level() != level()) return false;
        if (VehicleOperation.remote(profile())) {
            VehicleEntity previous = controlledBy(operator);
            if (previous == this) return true;
            double range = flightPhase() == FlightPhase.AIRBORNE
                    ? radioRange() : 8;
            if (distanceToSqr(player) > range * range || previous != null || isVehicle()
                    || entityData.get(OPERATOR).isPresent() || !canLaunch(operator)) return false;
            if (profile() == VehicleProfile.MAVIC3T && !mavicEquipment(player)) {
                player.displayClientMessage(Component.translatable("message.milmod.mavic_equipment"), true);
                return false;
            }
            mission.clear();
            publishMission();
            entityData.set(OPERATOR, Optional.of(player.getUUID()));
            player.getPersistentData().putUUID(REMOTE_KEY, getUUID());
            operatorPosition = player.position();
            dev.milmod.server.RemoteChunkView.keepCameraFlightLoaded(operator, this);
            pilotInputAt = Long.MIN_VALUE;
            heldYaw = getYRot();
            heldPitch = -getXRot();
        } else if (!canLaunch(operator) || !mountPilot(player)) return false;
        prepareLaunch();
        return true;
    }

    public boolean startMission(ServerPlayer player, FlightMission.Mode mode, BlockPos marker) {
        if (!VehicleOperation.remote(profile()) || distanceToSqr(player) > 64 || !canLaunch(player)
                || entityData.get(OPERATOR).isPresent() || mission.active()
                || !mission.set(player.serverLevel(), this, mode, marker)) return false;
        publishMission();
        prepareLaunch();
        return true;
    }

    private void prepareLaunch() {
        controlsActive = true;
        if (flightPhase() == FlightPhase.AIRBORNE || runwayAssistTicks > 0) return;
        baseAltitude = getY();
        heldYaw = getYRot();
        heldPitch = 0;
        poweredTicks = 0;
        if (docked()) {
            launchTicks = 1;
            spoolAge = 0;
            setPower(0);
            setFlightPhase(FlightPhase.SPOOLING);
        } else {
            setFlightPhase(FlightPhase.TAXI);
            if (DroneSpec.of(profile()).handStart()) return;
        }
        playSound(ModSounds.launch(profile()), .8F, 1F);
        if (docked()) playSound(ModSounds.RAIL_LATCH.get(), .8F, 1F);
        if (level() instanceof ServerLevel server) {
            dev.milmod.network.DroneLink.exhaustFlash(server, exhaustPosition());
        }
    }

    public Vec3 exhaustPosition() {
        float yaw = (float) Math.toRadians(getYRot());
        double offset = profile().length * .40;
        return position().add(Math.sin(yaw) * offset, profile().height * .40, -Math.cos(yaw) * offset);
    }

    private void stepAircraft(Player pilot) {
        DroneSpec spec = DroneSpec.of(profile());
        if (!started() || damageMask() != 0) {
            VehicleMotion.step(this, pilot);
            return;
        }
        boolean wasAirborne = flightPhase() == FlightPhase.AIRBORNE;
        if (wasAirborne && onGround() && getDeltaMovement().horizontalDistance() < spec.liftOff() * .62) {
            mission.clear();
            publishMission();
            handPrimed = false;
            runwayAssistTicks = 0;
            entityData.set(START_HITS, 0);
            setFlightPhase(FlightPhase.LANDED);
            wasAirborne = false;
            setPower(0);
            if (VehicleOperation.railOnly(profile()) || spec.handStart()) {
                controlsActive = false;
                if (pilot instanceof ServerPlayer operator) stopRemote(operator);
                setActivity(false, 0);
                VehicleMotion.step(this, null);
                return;
            }
        }
        float rate = spec.thrustRatePerSecond() / 20F;
        if (mission.active()) {
            Vec3 direction = mission.direction(this);
            if (mission.active() && direction.horizontalDistanceSqr() > .001) {
                heldYaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
            }
            heldPitch = (float) Mth.clamp((getY() - (baseAltitude + spec.missionHeight())) * .65, -14, 13);
            float cruisePower = (float) Math.pow(spec.flightCruise() / spec.flightMaximum(), 2);
            setPower(mission.active() ? Mth.approach(power, onGround() ? 1F : cruisePower, rate) : 0F);
            publishMission();
        } else if (pilot != null) {
            float input = pilotForward(pilot);
            setPower(power + input * rate);
            // Explicit input packets are authoritative for remote look direction.
            if (!VehicleOperation.remote(profile()) || freshPilotInput()) {
                heldYaw = pilotYaw(pilot);
                heldPitch = pilotClimb() && profile().kind == VehicleProfile.Kind.HOVER ? -30 : pilotPitch(pilot);
            }
        }
        if (runwayAssistTicks > 0) {
            if (pilot != null && pilotForward(pilot) < -.05F) {
                runwayAssistTicks = 0;
            } else {
                runwayAssistTicks--;
                heldPitch = -9F;
                setPower(Mth.approach(power, flightPhase() == FlightPhase.AIRBORNE ? .76F : 1F, rate));
                if (runwayAssistTicks == 0 && flightPhase() == FlightPhase.TAXI) {
                    setPower(0);
                    controlsActive = false;
                    handPrimed = false;
                    mission.clear();
                    publishMission();
                    setFlightPhase(FlightPhase.READY);
                }
            }
        }
        if (!hasFuel() || poweredTicks >= spec.enduranceSeconds() * 20) {
            if (!engineExpired && (power > 0 || running())) playSound(ModSounds.failure(profile()), .72F, 1F);
            engineExpired = true;
            setPower(0);
        } else {
            engineExpired = false;
            if (power > 0) {
                poweredTicks++;
                if (VehicleOperation.needsFuel(profile())) {
                    // Fixed-point game fuel use. Full throttle consumes six units per second.
                    fuelRemainder += Math.round(power * 600);
                    if (fuelRemainder >= 2000) {
                        entityData.set(FUEL, Math.max(0, entityData.get(FUEL) - fuelRemainder / 2000));
                        fuelRemainder %= 2000;
                    }
                }
            }
        }
        VehicleMotion.step(this, pilot);
        if (!onGround() && !isRemoved() && (wasAirborne || running() && getDeltaMovement().y > .028)) {
            if (!wasAirborne && runwayAssistTicks > 0) runwayAssistTicks = Math.min(runwayAssistTicks, 22);
            setFlightPhase(FlightPhase.AIRBORNE);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || fragmentPart() != 0) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        ItemStack held = player.getItemInHand(hand);
        DroneSpec spec = DroneSpec.of(profile());
        if (spec != null && spec.meshSlot() && availableTo(player)
                && (held.is(ModContent.MESH_MODEM.get()) || player.isShiftKeyDown() && held.isEmpty() && modemInstalled())) {
            if (launchTicks > 0 || !docked() && !onGround()) return InteractionResult.FAIL;
            if (held.is(ModContent.MESH_MODEM.get())) {
                if (modemInstalled()) return InteractionResult.FAIL;
                if (!player.getAbilities().instabuild) held.shrink(1);
                entityData.set(MODEM, true);
            } else {
                ItemStack returned = new ItemStack(ModContent.MESH_MODEM.get());
                if (!player.getInventory().add(returned)) return InteractionResult.FAIL;
                entityData.set(MODEM, false);
            }
            playSound(ModSounds.ATTACH.get(), .7F, 1F);
            return InteractionResult.CONSUME;
        }
        if (held.is(ModContent.FLIGHT_FUEL.get()) && VehicleOperation.needsFuel(profile())) {
            if ((!onGround() && !docked()) || entityData.get(FUEL) >= FULL_FUEL || !availableTo(player)) return InteractionResult.FAIL;
            entityData.set(FUEL, FULL_FUEL);
            fuelRemainder = 0;
            if (!player.getAbilities().instabuild) held.shrink(1);
            playSound(ModSounds.ATTACH.get(), .60F, .85F);
            player.displayClientMessage(Component.translatable("message.milmod.refueled"), true);
            return InteractionResult.CONSUME;
        }
        if (held.getItem() instanceof TabletItem tablet && profile().air() && availableTo(player)) {
            if (!tablet.accepts(this)) {
                player.displayClientMessage(Component.translatable("message.milmod.mavic_equipment"), true);
                return InteractionResult.FAIL;
            }
            if (getUUID().equals(TabletItem.linked(held)) && !player.isShiftKeyDown()) {
                if (hasFlightCamera()) return startFromTablet(player) ? InteractionResult.CONSUME : InteractionResult.FAIL;
                player.displayClientMessage(Component.translatable("message.milmod.plan_mission"), true);
                return InteractionResult.CONSUME;
            }
            TabletItem.bind(held, this, player);
            return InteractionResult.CONSUME;
        }
        if (profile() == VehicleProfile.LAUNCHER && held.getItem() instanceof VehicleItem kit && kit.profile().air()) {
            if (!VehicleOperation.accepts(profile(), kit.profile())) {
                player.displayClientMessage(Component.translatable("message.milmod.runway_required"), true);
                return InteractionResult.FAIL;
            }
            if (!createDockedAircraft(player, held, kit.profile())) return InteractionResult.FAIL;
            return InteractionResult.CONSUME;
        }
        if (acceptsPayload(held)) {
            if (!availableTo(player) || !onGround() || isVehicle() || trainingPayload() >= payloadCapacity()) {
                return InteractionResult.FAIL;
            }
            entityData.set(PAYLOAD, (byte) (trainingPayload() + 1));
            if (!player.getAbilities().instabuild) held.shrink(1);
            playSound(ModSounds.ATTACH.get(), 0.7F, 1F);
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown()) {
            if (profile().feature && getPassengers().isEmpty() && entityData.get(DISPLAY_START) < 0) {
                entityData.set(OPEN, !entityData.get(OPEN));
                playSound(ModSounds.vehicle(profile()), 0.6F, 0.9F);
            } else if (getPassengers().isEmpty() && entityData.get(OPERATOR).isEmpty() && damageMask() == 0 && integrity >= profile().durability) {
                spawnAtLocation(ModContent.kit(profile()));
                if (trainingPayload() > 0) spawnAtLocation(payloadStack(trainingPayload()));
                if (modemInstalled()) spawnAtLocation(ModContent.MESH_MODEM.get());
                discard();
            }
        } else if (profile().kind == VehicleProfile.Kind.STATIC && profile().feature) {
            entityData.set(OPEN, !entityData.get(OPEN));
            playSound(ModSounds.vehicle(profile()), 0.7F, 1F);
        } else if (profile().air()) {
            return startFromTablet(player) ? InteractionResult.CONSUME : InteractionResult.FAIL;
        } else if (profile().kind == VehicleProfile.Kind.GROUND) {
            return mountPilot(player) ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        return InteractionResult.CONSUME;
    }

    private boolean mountPilot(Player player) {
        if (!availableTo(player) || !isAlive() || !player.isAlive() || isVehicle() || player.isPassenger()
                || distanceToSqr(player) > 64 || damageMask() != 0 || !canAddPassenger(player)) return false;
        pilotInputAt = Long.MIN_VALUE;
        if (!player.startRiding(this, true)) return false;
        controlsActive = true;
        positionRider(player);
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
            serverPlayer.connection.send(new ClientboundSetPassengersPacket(this));
        }
        return true;
    }

    public void acceptPilotInput(Player rider, float forward, float side, boolean climb, float yaw, float pitch) {
        if (!(getFirstPassenger() == rider || rider instanceof ServerPlayer operator && remoteStillValid(operator))
                || !isAlive() || level().isClientSide
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) return;
        pilotInputAt = level().getGameTime();
        pilotForward = Mth.clamp(forward, -1, 1);
        pilotSide = Mth.clamp(side, -1, 1);
        pilotClimb = climb;
        pilotYaw = Mth.wrapDegrees(yaw);
        pilotPitch = Mth.clamp(pitch, -90, 90);
    }

    private boolean freshPilotInput() {
        return pilotInputAt != Long.MIN_VALUE && level().getGameTime() - pilotInputAt <= 8;
    }
    float pilotForward(Player player) {
        return pilotInputAt == Long.MIN_VALUE ? player.zza : freshPilotInput() ? pilotForward : 0;
    }
    float pilotSide(Player player) {
        return pilotInputAt == Long.MIN_VALUE ? player.xxa : freshPilotInput() ? pilotSide : 0;
    }
    float pilotYaw(Player player) { return freshPilotInput() ? pilotYaw : player.getYRot(); }
    float pilotPitch(Player player) { return freshPilotInput() ? pilotPitch : player.getXRot(); }
    boolean pilotClimb() { return freshPilotInput() && pilotClimb; }

    @Override
    public void tick() {
        if (!profile().available()) {
            if (!level().isClientSide) discard();
            return;
        }
        super.tick();
        if (level().isClientSide) {
            if (profile().kind == VehicleProfile.Kind.PLANE) {
                int commands = entityData.get(CONTROL_SURFACES);
                boolean movable = flightPhase() == FlightPhase.AIRBORNE && damageMask() == 0;
                for (int i = 0; i < surfaces.length; i++) {
                    previousSurfaces[i] = surfaces[i];
                    float target = movable ? (byte) (commands >> (i * 8)) / 100F : 0;
                    surfaces[i] = Mth.approach(surfaces[i], target, .16F);
                }
                if (hasAnimatedGear()) {
                    oldGear = gear;
                    if (!movable || onGround()) retractGear = false;
                    else if (tickCount % 5 == 0 && level().hasChunkAt(blockPosition())) {
                        int ground = level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                                getBlockX(), getBlockZ());
                        retractGear = getY() - ground > (retractGear ? 4 : 7);
                    }
                    gear = Mth.approach(gear, retractGear ? 1F : 0F, .075F);
                }
            }
            oldRotorAngle = rotorAngle;
            float rotorTarget = (running() && profile().air()) ? 12 + throttlePercent() * .72F : 0;
            rotorSpeed = Mth.approach(rotorSpeed, rotorTarget, 7);
            rotorAngle += rotorSpeed;
            if (rotorAngle >= 360) { rotorAngle -= 360; oldRotorAngle -= 360; }
            oldBank = renderedBank;
            renderedBank = Mth.rotLerp(0.35F, renderedBank, entityData.get(BANK));
            previousFeaturePose = featurePose;
            featurePose = Mth.approach(featurePose, entityData.get(OPEN) ? 1F : 0F, 0.085F);
            double previousX = getX(), previousZ = getZ();
            float previousYaw = getYRot();
            long syncedSuspension = entityData.get(SUSPENSION);
            int contactCount = VehicleSuspension.of(profile()).size();
            for (int i = 0; i < contactCount; i++) {
                previousSuspension[i] = suspension[i];
                suspension[i] = Mth.lerp(.48F, suspension[i], VehicleSuspension.decode(syncedSuspension, i));
            }
            if (remainingLerp > 0) {
                double ratio = 1.0 / remainingLerp--;
                setPos(Mth.lerp(ratio, getX(), targetX), Mth.lerp(ratio, getY(), targetY),
                        Mth.lerp(ratio, getZ(), targetZ));
                setYRot(Mth.rotLerp((float) ratio, getYRot(), targetYaw));
                setXRot(Mth.lerp((float) ratio, getXRot(), targetPitch));
            }
            previousWheelTravel = wheelTravel;
            previousTurnTravel = turnTravel;
            turnTravel += (float) Math.toRadians(Mth.wrapDegrees(getYRot() - previousYaw));
            double heading = Math.toRadians(getYRot());
            wheelTravel += (float) (-(getX() - previousX) * Math.sin(heading)
                    + (getZ() - previousZ) * Math.cos(heading));
            if (Math.abs(wheelTravel) >= 3600) {
                float offset = Math.copySign(3600, wheelTravel);
                wheelTravel -= offset;
                previousWheelTravel -= offset;
            }
            if (Math.abs(turnTravel) >= 3600) {
                float offset = Math.copySign(3600, turnTravel);
                turnTravel -= offset;
                previousTurnTravel -= offset;
            }
            return;
        }
        if (fragmentPart() != 0) {
            tickFragment();
            return;
        }
        if (payloadCooldown > 0) --payloadCooldown;
        boolean displaying = entityData.get(DISPLAY_START) >= 0;
        if (displaying && level() instanceof ServerLevel server
                && !display.tick(server, this, (int) displayTime(0))) {
            entityData.set(DISPLAY_START, -1L);
            displaying = false;
        }
        Player pilot = getFirstPassenger() instanceof Player passenger ? passenger : null;
        if (level() instanceof ServerLevel server && entityData.get(OPERATOR).isPresent()) {
            ServerPlayer operator = server.getServer().getPlayerList().getPlayer(entityData.get(OPERATOR).orElseThrow());
            if (operator != null && remoteStillValid(operator)) {
                pilot = operator;
            } else {
                if (operator != null) stopRemote(operator);
                else {
                    entityData.set(OPERATOR, Optional.empty());
                    operatorPosition = null;
                    pilotInputAt = Long.MIN_VALUE;
                }
            }
        }
        if (docked()) {
            VehicleEntity stand = linkedStand();
            if (stand == null) {
                long now = level().getGameTime();
                if (dockMissingSince < 0) dockMissingSince = now;
                if (now - dockMissingSince >= 40) abortLaunch(null);
                return;
            }
            dockMissingSince = -1L;
            if (launchTicks > 0) {
                if (!stand.canHoldAircraft() || ++launchTicks > 100) {
                    abortLaunch(stand.canHoldAircraft() ? stand : null);
                    return;
                }
                if (flightPhase() == FlightPhase.SPOOLING) {
                    setPower(++spoolAge / (float) DroneSpec.of(profile()).spoolUpTicks());
                    setActivity(true, 0);
                    if (power < 1) return;
                    setFlightPhase(FlightPhase.LAUNCHING);
                }
                VehicleMotion.launch(this, stand);
                return;
            }
            if (launchDistance == 0) followRail(stand);
            else if (!stand.canHoldAircraft() || stand.distanceToSqr(this) > 6) abortLaunch(null);
            return;
        }
        if (flightPhase() == FlightPhase.SPOOLING) {
            if (!hasFuel() || !onGround() || damageMask() != 0) {
                abortLaunch(null);
                handPrimed = false;
                return;
            }
            setPower(++spoolAge / (float) DroneSpec.of(profile()).spoolUpTicks());
            setActivity(true, 0);
            if (power >= 1) {
                handPrimed = true;
                controlsActive = true;
                runwayAssistTicks = 100;
                setFlightPhase(FlightPhase.TAXI);
            }
            return;
        }
        if (handPrimed && !controlsActive) {
            moveSupported(0, 0, hasFuel(), 0);
            if (!onGround()) {
                handPrimed = false;
                setPower(0);
                setFlightPhase(FlightPhase.LANDED);
            }
            return;
        }
        if (profile().air()) {
            stepAircraft(pilot);
            if (!isRemoved() && !onGround() && running() && (level().getGameTime() + getId()) % 10 == 0
                    && level() instanceof ServerLevel server) {
                dev.milmod.network.DroneLink.distantEngine(server, this);
            }
        } else VehicleMotion.step(this, displaying ? null : pilot);
    }

    void setActivity(boolean active, float roll) {
        entityData.set(RUNNING, active);
        if (Math.abs(roll - entityData.get(BANK)) >= 0.45F || roll == 0) entityData.set(BANK, roll);
    }

    boolean started() { return controlsActive; }

    void moveVehicle(Vec3 motion) {
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        if (horizontalCollision || verticalCollision) {
            Vec3 clipped = getDeltaMovement();
            setDeltaMovement(new Vec3(horizontalCollision ? clipped.x * 0.35 : clipped.x,
                    verticalCollision ? 0 : clipped.y, horizontalCollision ? clipped.z * 0.35 : clipped.z));
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        targetX = x;
        targetY = y;
        targetZ = z;
        targetYaw = yaw;
        targetPitch = pitch;
        remainingLerp = Math.max(steps, 1);
    }

    void rememberImpact(Entity projectile, Vec3 point) {
        lastProjectileId = projectile.getId();
        lastImpactTick = level().getGameTime();
        lastImpact = point;
    }

    Vec3 recentImpact(Entity projectile) {
        return lastProjectileId == projectile.getId() && lastImpactTick == level().getGameTime() ? lastImpact : null;
    }

    private boolean tapStarter(ServerPlayer player) {
        DroneSpec spec = DroneSpec.of(profile());
        if (spec == null || !spec.handStart() || !onGround() || damageMask() != 0 || !availableTo(player)
                || !player.getMainHandItem().isEmpty() || distanceToSqr(player) > 25 || controlsActive) return false;
        if (handPrimed || flightPhase() == FlightPhase.SPOOLING) return true;
        if (!hasFuel()) {
            playSound(ModSounds.STARTER_CLICK.get(), .5F, .9F);
            player.displayClientMessage(Component.translatable("message.milmod.needs_fuel"), true);
            return true;
        }
        long now = level().getGameTime();
        if (now < nextStartHit) return true;
        nextStartHit = now + 4;
        int remaining = startHitsRemaining();
        if (remaining == 0) remaining = random.nextInt(spec.startHitsMax() - spec.startHitsMin() + 1) + spec.startHitsMin();
        entityData.set(START_HITS, --remaining);
        if (remaining == 0) {
            spoolAge = 0;
            poweredTicks = 0;
            baseAltitude = getY();
            setPower(0);
            setFlightPhase(FlightPhase.SPOOLING);
            playSound(ModSounds.launch(profile()), .8F, 1F);
            dev.milmod.network.DroneLink.exhaustFlash(player.serverLevel(), exhaustPosition());
        } else {
            playSound(ModSounds.STARTER_CLICK.get(), .65F, .95F + .09F * remaining);
            player.displayClientMessage(Component.translatable("message.milmod.hand_hits", remaining), true);
        }
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || !Float.isFinite(amount) || amount <= 0 || !isAlive()) return false;
        if (level().isClientSide) return true;
        if (source.getDirectEntity() instanceof ServerPlayer player && tapStarter(player)) return true;
        Vec3 hit = VehicleHits.impactPoint(this, source);
        if (takeVehicleDamage(amount, hit) && isAlive()
                && profile().kind == VehicleProfile.Kind.STATIC && VehicleSuspension.of(profile()).size() > 0) {
            var attacker = source.getDirectEntity();
            Vec3 origin = source.getSourcePosition();
            if (origin != null) {
                Vec3 offset = position().subtract(origin).multiply(1, 0, 1);
                if (offset.horizontalDistanceSqr() < .01 && attacker != null) {
                    offset = attacker.getLookAngle().multiply(1, 0, 1);
                }
                double strength = Mth.clamp(amount * .035, .09, .32);
                Vec3 impulse = offset.normalize().scale(strength).add(0, .14 + strength * .42, 0);
                supportPhysics.kick(this, impulse);
                setDeltaMovement(getDeltaMovement().multiply(.6, 0, .6).add(impulse));
                setOnGround(false);
            }
        }
        return true;
    }

    public boolean takeVehicleDamage(float amount, Vec3 hit) {
        if (!isAlive() || level().isClientSide || !Float.isFinite(amount) || amount <= 0) return false;
        if (profile().air() && hit != null) {
            double yaw = Math.toRadians(getYRot());
            double lateral = -(hit.x - getX()) * Math.cos(yaw) - (hit.z - getZ()) * Math.sin(yaw);
            if (Math.abs(lateral) > profile().width * 0.15) {
                boolean left = lateral < 0;
                float side = left ? (leftDamage += amount) : (rightDamage += amount);
                int mask = damageMask();
                int bit = left ? 1 : 2;
                if (side >= Math.max(2, profile().durability * 0.19F) && (mask & bit) == 0) {
                    entityData.set(DAMAGE, mask | bit);
                    controlsActive = true;
                    playSound(ModSounds.HIT.get(), 1F, 0.85F);
                }
            }
        }
        integrity -= amount;
        if (integrity <= 0) {
            if (!profile().air()) {
                ejectPassengers();
                discard();
            } else {
                entityData.set(DAMAGE, 3);
                controlsActive = true;
                if (onGround()) {
                    ejectPassengers();
                    discard();
                }
            }
        }
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        VehicleProfile profile = VehicleProfile.byId(tag.getString("Model"));
        if (profile.oldStand()) profile = VehicleProfile.LAUNCHER;
        entityData.set(PROFILE, profile.ordinal());
        entityData.set(DAMAGE, tag.getInt("DamageMask") & 3);
        entityData.set(OPEN, tag.getBoolean("Feature"));
        integrity = tag.contains("Integrity") ? tag.getFloat("Integrity") : profile.durability;
        leftDamage = tag.getFloat("LeftDamage");
        rightDamage = tag.getFloat("RightDamage");
        controlsActive = tag.getBoolean("Started");
        float savedBank = tag.getFloat("Bank");
        if (Float.isFinite(savedBank)) entityData.set(BANK, Mth.clamp(savedBank, -90, 90));
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        entityData.set(PAYLOAD, (byte) Mth.clamp(tag.getByte("TrainingPayload"), 0, payloadCapacity()));
        boolean legacyRunwayDock = tag.hasUUID("Rail") && !VehicleOperation.railOnly(profile);
        railId = tag.hasUUID("Rail") && !legacyRunwayDock ? tag.getUUID("Rail") : null;
        DroneSpec spec = DroneSpec.of(profile);
        entityData.set(MODEM, spec != null && spec.meshSlot() && tag.getBoolean("MeshModem"));
        double savedAltitude = tag.getDouble("FlightBase");
        baseAltitude = tag.contains("FlightBase") && Double.isFinite(savedAltitude) ? savedAltitude : getY();
        heldYaw = tag.contains("FlightHeading") && Float.isFinite(tag.getFloat("FlightHeading"))
                ? Mth.wrapDegrees(tag.getFloat("FlightHeading")) : getYRot();
        heldPitch = tag.contains("FlightPitch") && Float.isFinite(tag.getFloat("FlightPitch"))
                ? Mth.clamp(tag.getFloat("FlightPitch"), -40, 40) : 0;
        float savedPower = tag.getFloat("Throttle");
        setPower(Float.isFinite(savedPower) ? savedPower : 0);
        poweredTicks = Mth.clamp(tag.getInt("PoweredTicks"), 0, 12001);
        FlightPhase fallback = railId != null ? FlightPhase.LOADED : controlsActive
                ? FlightPhase.AIRBORNE : FlightPhase.READY;
        int phase = tag.contains("FlightPhase") ? Mth.clamp(tag.getInt("FlightPhase"), 0, FlightPhase.values().length - 1) : fallback.ordinal();
        setFlightPhase(legacyRunwayDock ? FlightPhase.READY : FlightPhase.values()[phase]);
        if (legacyRunwayDock) {
            controlsActive = false;
            poweredTicks = 0;
            setPower(0);
            setXRot(0);
        }
        if (legacyRunwayDock) mission.clear();
        else mission.load(tag);
        if (!mission.active() && tag.contains("Patrol", net.minecraft.nbt.Tag.TAG_LONG_ARRAY)) {
            // Legacy multi-point routes are retired. Leave airborne craft unpowered on load.
            setPower(0);
        }
        publishMission();
        launchTicks = 0;
        launchDistance = 0;
        spoolAge = 0;
        runwayAssistTicks = legacyRunwayDock ? 0 : Mth.clamp(tag.getInt("RunwayAssist"), 0, 100);
        handPrimed = spec != null && spec.handStart() && tag.getBoolean("HandPrimed");
        entityData.set(START_HITS, spec == null ? 0 : Mth.clamp(tag.getInt("StartHits"), 0, spec.startHitsMax()));
        if (spec != null && flightPhase() == FlightPhase.SPOOLING) {
            spoolAge = Mth.clamp(tag.getInt("SpoolAge"), 0, spec.spoolUpTicks());
        }
        if (railId != null && spec != null) {
            double distance = tag.getDouble("LaunchDistance");
            launchDistance = Double.isFinite(distance) ? Mth.clamp(distance, 0, 8) : 0;
            if (flightPhase() == FlightPhase.LAUNCHING || flightPhase() == FlightPhase.SPOOLING) {
                launchTicks = Mth.clamp(tag.getInt("LaunchTicks"), 1, 100);
            }
        }
        entityData.set(DOCKED, railId != null && profile.air());
        entityData.set(FUEL, Mth.clamp(tag.getInt("Fuel"), 0, FULL_FUEL));
        fuelRemainder = Mth.clamp(tag.getInt("FuelRemainder"), 0, 1999);
        entityData.set(FRAGMENT, (byte) (tag.getByte("Fragment") & 3));
        entityData.set(LOST_PARTS, (byte) (tag.getByte("Separated") & 3));
        fragmentAge = tag.getInt("FragmentAge");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Model", profile().id);
        tag.putInt("DamageMask", damageMask());
        tag.putBoolean("Feature", entityData.get(OPEN));
        tag.putFloat("Integrity", integrity);
        tag.putFloat("LeftDamage", leftDamage);
        tag.putFloat("RightDamage", rightDamage);
        tag.putBoolean("Started", controlsActive);
        tag.putFloat("Bank", authoritativeBank());
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putByte("TrainingPayload", (byte) trainingPayload());
        tag.putInt("Fuel", entityData.get(FUEL));
        if (fuelRemainder > 0) tag.putInt("FuelRemainder", fuelRemainder);
        if (fragmentPart() != 0) {
            tag.putByte("Fragment", (byte) fragmentPart());
            tag.putInt("FragmentAge", fragmentAge);
        }
        tag.putByte("Separated", entityData.get(LOST_PARTS));
        if (profile().air()) {
            tag.putBoolean("MeshModem", modemInstalled());
            tag.putFloat("Throttle", power);
            tag.putInt("FlightPhase", flightPhase().ordinal());
            tag.putDouble("FlightBase", baseAltitude);
            tag.putFloat("FlightHeading", heldYaw);
            tag.putFloat("FlightPitch", heldPitch);
            tag.putInt("PoweredTicks", poweredTicks);
            mission.save(tag);
            tag.putBoolean("HandPrimed", handPrimed);
            if (runwayAssistTicks > 0) tag.putInt("RunwayAssist", runwayAssistTicks);
            tag.putInt("StartHits", startHitsRemaining());
            if (flightPhase() == FlightPhase.SPOOLING) tag.putInt("SpoolAge", spoolAge);
            if (railId != null && docked()) {
                tag.putUUID("Rail", railId);
                tag.putDouble("LaunchDistance", launchDistance);
                if (launchTicks > 0) tag.putInt("LaunchTicks", launchTicks);
            }
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
