package dev.milmod.model;

/** Tuned in Minecraft blocks and ticks for each construction type. */
public enum VehicleHandling {
    TRACKS(3.8F, 0.38F, 0.28F, 0.72F, 0),
    WHEELS(5.0F, 0.42F, 0.18F, 0.84F, 0),
    HEAVY_TRUCK(2.9F, 0.30F, 0.12F, 0.89F, 0),
    PROPELLER(5.5F, 0, 0.17F, 0.90F, 0.28F),
    DELTA_JET(4.0F, 0, 0.12F, 0.94F, 0.37F),
    TRAINER_JET(3.8F, 0, 0.09F, 0.95F, 0.44F),
    QUAD(7.0F, 0, 0.27F, 0.72F, 0),
    FIXTURE(0, 0, 0, 0, 0);

    public final float turn;
    public final float reverse;
    public final float response;
    public final float coast;
    public final float takeoffFraction;

    VehicleHandling(float turn, float reverse, float response, float coast, float takeoffFraction) {
        this.turn = turn;
        this.reverse = reverse;
        this.response = response;
        this.coast = coast;
        this.takeoffFraction = takeoffFraction;
    }

    public static VehicleHandling of(VehicleProfile profile) {
        return switch (profile) {
            case BUK_M3, M113, TOR_M1, T64BV -> TRACKS;
            case GAZ_TIGR -> WHEELS;
            case ISKANDER_K -> HEAVY_TRUCK;
            case FP1, FORPOST_R, MOHAJER_6, ORLAN_10, LYUTYI -> PROPELLER;
            case SHAHED_238, GERAN4 -> DELTA_JET;
            case L39_ALBATROS -> TRAINER_JET;
            case MAVIC3T -> QUAD;
            case BEACON_MARKER, FP1_RAMP, LAUNCHER, ORLAN_RAIL -> FIXTURE;
        };
    }
}
