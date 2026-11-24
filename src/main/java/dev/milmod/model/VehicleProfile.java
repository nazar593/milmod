package dev.milmod.model;

import java.util.Locale;

public enum VehicleProfile {
    BUK_M3(Kind.GROUND, 6.9F, 2.8F, 2.2F, 0.24, 58, true),
    SHAHED_238(Kind.PLANE, 2.5F, 2.4F, 0.65F, 0.80, 18, false),
    M113(Kind.GROUND, 4.3F, 2.3F, 1.6F, 0.28, 44, true),
    TOR_M1(Kind.GROUND, 5.8F, 2.6F, 2.5F, 0.25, 52, true),
    ISKANDER_K(Kind.GROUND, 7.5F, 2.4F, 2.2F, 0.30, 45, true),
    BEACON_MARKER(Kind.STATIC, 0.55F, 0.55F, 0.7F, 0, 8, true),
    FP1(Kind.PLANE, 2.8F, 2.6F, 0.65F, 0.60, 16, false),
    LAUNCHER(Kind.STATIC, 2.65F, 1.45F, 1.05F, 0, 16, false),
    FP1_RAMP(Kind.STATIC, 2.8F, 1.35F, 0.9F, 0, 16, true),
    GERAN4(Kind.PLANE, 2.9F, 2.7F, 0.7F, 0.90, 19, false),
    MAVIC3T(Kind.HOVER, 0.85F, 0.85F, 0.4F, 0.33, 10, false),
    FORPOST_R(Kind.PLANE, 3.8F, 3.9F, 0.95F, 0.59, 25, false),
    GAZ_TIGR(Kind.GROUND, 4.0F, 1.85F, 1.6F, 0.43, 30, false),
    L39_ALBATROS(Kind.PLANE, 5.3F, 4.0F, 1.1F, 1.12, 32, true),
    MOHAJER_6(Kind.PLANE, 3.5F, 3.7F, 0.8F, 0.57, 23, false),
    T64BV(Kind.GROUND, 6.0F, 2.7F, 1.85F, 0.31, 65, false),
    ORLAN_10(Kind.PLANE, 2.20F, 2.8F, 0.46F, 0.48, 17, false),
    ORLAN_RAIL(Kind.STATIC, 3.0F, 1.35F, 0.7F, 0, 22, false),
    LYUTYI(Kind.PLANE, 3.45F, 3.8F, 0.75F, 0.66, 26, false);

    private static final VehicleProfile[] PROFILES = values();
    public final String id = name().toLowerCase(Locale.ROOT);
    public final Kind kind;
    public final float length;
    public final float width;
    public final float height;
    public final double speed;
    public final float durability;
    public final boolean feature;

    VehicleProfile(Kind kind, float length, float width, float height, double speed, float durability, boolean feature) {
        this.kind = kind;
        this.length = length;
        this.width = width;
        this.height = height;
        this.speed = speed;
        this.durability = durability;
        this.feature = feature;
    }

    public static VehicleProfile byIndex(int index) {
        return index >= 0 && index < PROFILES.length ? PROFILES[index] : MAVIC3T;
    }

    public static VehicleProfile byId(String id) {
        for (VehicleProfile profile : PROFILES) {
            if (profile.id.equals(id)) return profile;
        }
        return MAVIC3T;
    }

    public boolean available() {
        return switch (this) {
            case GAZ_TIGR, T64BV, M113, L39_ALBATROS, BEACON_MARKER, ORLAN_RAIL, FP1_RAMP -> false;
            default -> true;
        };
    }

    public boolean oldStand() { return this == ORLAN_RAIL || this == FP1_RAMP; }

    public float crashEffect() {
        return switch (this) {
            case FP1 -> 2.1F;
            case LYUTYI -> 2.6F;
            case SHAHED_238 -> 1.8F;
            case GERAN4 -> 3.0F;
            default -> 0F;
        };
    }

    public boolean air() {
        return kind == Kind.HOVER || kind == Kind.PLANE;
    }

    public enum Kind { GROUND, PLANE, HOVER, STATIC }
}
