package dev.milmod.model;

public final class VehicleOperation {
    private VehicleOperation() {}

    public static boolean remote(VehicleProfile profile) {
        return profile.air() && profile != VehicleProfile.L39_ALBATROS;
    }

    public static boolean needsFuel(VehicleProfile profile) {
        DroneSpec spec = DroneSpec.of(profile);
        return spec != null && spec.needsFuel();
    }

    public static boolean railOnly(VehicleProfile profile) {
        DroneSpec spec = DroneSpec.of(profile);
        return spec != null && spec.launch() != DroneSpec.Launch.RUNWAY;
    }

    public static boolean accepts(VehicleProfile stand, VehicleProfile craft) {
        return stand == VehicleProfile.LAUNCHER && craft.available() && remote(craft) && railOnly(craft);
    }

    public static double seatLongitudinal(VehicleProfile profile) {
        return switch (profile) {
            case ISKANDER_K -> -2.28;
            case GAZ_TIGR -> -.51;
            case L39_ALBATROS -> -.71;
            default -> -profile.length * .16;
        };
    }
}
