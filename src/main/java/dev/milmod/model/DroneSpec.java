package dev.milmod.model;

import java.util.Map;

/** Fictional flight tuning for Minecraft at 20 ticks/s and one block per reference metre. */
public record DroneSpec(double mass, double thrust, double maximum, double cruise, int ceiling,
                        int enduranceSeconds, float thrustRatePerSecond, float bank, Engine engine,
                        LaunchMode launchMode, boolean hasCamera, boolean meshSlot, double deckHeight, double railTravel,
                        boolean needsFuel, int startHitsMin, int startHitsMax, int spoolUpTicks,
                        double launchSpeed, double meshRange, int missionHeight, double waypointRadius,
                        float rollRate, float yawRate, float enginePitch) {
    public enum Engine { ELECTRIC, PISTON, JET }
    public enum Launch { RAIL, PAD, RUNWAY }
    public enum LaunchMode { LAUNCHER, GROUND_HAND_START }

    public static final Map<VehicleProfile, DroneSpec> ALL = Map.of(
            VehicleProfile.ORLAN_10,    new DroneSpec(1.0, .035, .54, .35, 100, 480, .35F, 33, Engine.PISTON, LaunchMode.LAUNCHER, true, false, .53, 1.45, false, 0, 0, 12, 4.8, 0,    22, 13, 3.9F, 4.2F, 1.07F),
            VehicleProfile.FP1,         new DroneSpec(1.8, .078, .64, .44, 125, 390, .29F, 31, Engine.PISTON, LaunchMode.LAUNCHER, false, true, .71, 1.65, false, 0, 0, 16, 6.0, 3000, 16, 18, 3.2F, 3.9F, .94F),
            VehicleProfile.GERAN4,      new DroneSpec(2.4, .118, .84, .61, 150, 360, .26F, 27, Engine.JET, LaunchMode.LAUNCHER, false, true, .59, 1.42, false, 0, 0, 22, 8.2, 3000, 23, 23, 2.6F, 3.2F, .93F),
            VehicleProfile.SHAHED_238,  new DroneSpec(1.9, .099, .78, .56, 140, 330, .38F, 36, Engine.JET, LaunchMode.LAUNCHER, false, true, .59, 1.42, false, 0, 0, 16, 7.6, 3000, 29, 20, 3.5F, 4.1F, 1.04F),
            VehicleProfile.MAVIC3T,     new DroneSpec(.6, .039, .31, .17, 60, 280, .62F, 25, Engine.ELECTRIC, LaunchMode.LAUNCHER, true, false, .59, .48, false, 0, 0,  8, 1.8, 0,    11,  5, 5.0F, 5.2F, 1.10F),
            VehicleProfile.FORPOST_R,   new DroneSpec(2.8, .116, .62, .43, 135, 420, .28F, 27, Engine.PISTON, LaunchMode.GROUND_HAND_START, true, false, 0, 0, true,  1, 3, 36, 5.8, 0,    30, 20, 2.7F, 3.1F, .84F),
            VehicleProfile.MOHAJER_6,   new DroneSpec(2.3, .101, .59, .42, 125, 450, .32F, 32, Engine.PISTON, LaunchMode.GROUND_HAND_START, true, false, 0, 0, true,  1, 3, 30, 5.7, 0,    24, 17, 3.4F, 3.8F, 1.03F),
            VehicleProfile.LYUTYI,      new DroneSpec(2.1, .098, .69, .48, 150, 400, .30F, 29, Engine.PISTON, LaunchMode.GROUND_HAND_START, false, true, 0, 0, true,  1, 3, 42, 6.6, 3000, 20, 21, 3.0F, 3.5F, .92F),
            VehicleProfile.L39_ALBATROS,new DroneSpec(3.4, .18, 1.12, .75, 180, 600, .31F, 43, Engine.JET, LaunchMode.GROUND_HAND_START, true, false, 0, 0, false, 0, 0, 65, 10.3, 0,   34, 25, 4.2F, 4.6F, .89F));

    public static DroneSpec of(VehicleProfile profile) { return ALL.get(profile); }
    public Launch launch() {
        if (launchMode == LaunchMode.GROUND_HAND_START) return Launch.RUNWAY;
        return engine == Engine.ELECTRIC ? Launch.PAD : Launch.RAIL;
    }

    public double flightMaximum() { return maximum * 1.28; }
    public double flightCruise() { return cruise * 1.18; }
    public double flightThrust() { return thrust * 1.24; }

    public boolean handStart() { return startHitsMax > 0; }
    public double liftOff() { return launchSpeed / 20; }

    /** Server configuration can scale the nominal 3000-block radio without editing individual aircraft. */
    public double configuredMeshRange(int configuredRange) { return meshRange * configuredRange / 3000D; }
}
