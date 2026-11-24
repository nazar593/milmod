package dev.milmod.sound;

import dev.milmod.MilMod;
import dev.milmod.model.VehicleProfile;
import dev.milmod.model.DroneSpec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> REGISTER =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MilMod.ID);
    private static final Map<VehicleProfile, RegistryObject<SoundEvent>> VEHICLES =
            new EnumMap<>(VehicleProfile.class);
    private static final Map<VehicleProfile, FlightSounds> FLIGHT = new EnumMap<>(VehicleProfile.class);
    private record FlightSounds(RegistryObject<SoundEvent> detail, RegistryObject<SoundEvent> launch, RegistryObject<SoundEvent> flyby,
                                RegistryObject<SoundEvent> failure, RegistryObject<SoundEvent> crash) {}
    public static final RegistryObject<SoundEvent> EXPLOSION_CRACK = add("explosion_crack");
    public static final RegistryObject<SoundEvent> EXPLOSION_MUFFLED = add("explosion_muffled");
    public static final RegistryObject<SoundEvent> GLASS_RATTLE = add("glass_rattle");
    public static final RegistryObject<SoundEvent> TINNITUS = add("tinnitus");
    public static final RegistryObject<SoundEvent> HEARTBEAT = add("heartbeat");
    public static final RegistryObject<SoundEvent> FALLING_WHISTLE = add("falling_whistle");
    private static final RegistryObject<SoundEvent> ENGINE_PROP_DETAIL = add("engine_prop_detail");
    private static final RegistryObject<SoundEvent> ENGINE_JET_DETAIL = add("engine_jet_detail");
    public static final RegistryObject<SoundEvent> STARTER_CLICK = add("starter_click");
    public static final RegistryObject<SoundEvent> RAIL_LATCH = add("rail_latch");
    public static final RegistryObject<SoundEvent> TABLET_CLICK = add("tablet_click");
    public static final RegistryObject<SoundEvent> TABLET_ERROR = add("tablet_error");
    public static final RegistryObject<SoundEvent> ATTACH = add("attach");
    public static final RegistryObject<SoundEvent> RELEASE = add("release");
    public static final RegistryObject<SoundEvent> HIT = add("damage");
    public static final RegistryObject<SoundEvent> CRASH = add("crash");
    public static final RegistryObject<SoundEvent> START = add("start");
    public static final RegistryObject<SoundEvent> IMPACT_BLAST = add("impact_blast");

    static {
        for (VehicleProfile profile : VehicleProfile.values()) {
            VEHICLES.put(profile, add(profile.id));
            if (DroneSpec.of(profile) != null) {
                FLIGHT.put(profile, new FlightSounds(add(profile.id + "_texture_loop"), add(profile.id + "_launch"), add(profile.id + "_flyby"),
                        add(profile.id + "_failure"), add(profile.id + "_crash")));
            }
        }
    }

    public static SoundEvent launch(VehicleProfile profile) { return FLIGHT.get(profile).launch().get(); }
    public static SoundEvent flyby(VehicleProfile profile) { return FLIGHT.get(profile).flyby().get(); }
    public static SoundEvent failure(VehicleProfile profile) { return FLIGHT.get(profile).failure().get(); }
    public static SoundEvent crash(VehicleProfile profile) { return FLIGHT.get(profile).crash().get(); }

    public static SoundEvent engineDetail(VehicleProfile profile) { return FLIGHT.get(profile).detail().get(); }

    private ModSounds() {}
    public static void register(IEventBus bus) { REGISTER.register(bus); }
    public static SoundEvent vehicle(VehicleProfile profile) { return VEHICLES.get(profile).get(); }

    private static RegistryObject<SoundEvent> add(String id) {
        return REGISTER.register(id, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.tryBuild(MilMod.ID, id)));
    }
}
