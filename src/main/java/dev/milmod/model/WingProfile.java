package dev.milmod.model;

import java.util.Map;
import net.minecraft.util.Mth;

/** Fictional wing constants in Minecraft simulation units. */
public record WingProfile(double area, double aspectRatio, double efficiency, double stallAngle) {
    private static final Map<VehicleProfile, WingProfile> FIXED_WINGS = Map.of(
            VehicleProfile.ORLAN_10, new WingProfile(.51, 6.0, .79, .31),
            VehicleProfile.FP1, new WingProfile(.58, 6.2, .78, .32),
            VehicleProfile.FORPOST_R, new WingProfile(.94, 8.0, .83, .31),
            VehicleProfile.MOHAJER_6, new WingProfile(.80, 7.2, .82, .31),
            VehicleProfile.LYUTYI, new WingProfile(.56, 7.6, .80, .32),
            VehicleProfile.GERAN4, new WingProfile(.40, 4.1, .72, .36),
            VehicleProfile.SHAHED_238, new WingProfile(.38, 4.3, .73, .36),
            VehicleProfile.L39_ALBATROS, new WingProfile(.38, 5.7, .78, .34));

    public static WingProfile of(VehicleProfile type) { return FIXED_WINGS.get(type); }

    public double liftCoefficient(double angle) {
        double limited = Mth.clamp(angle, -stallAngle, stallAngle);
        double linear = Mth.clamp(.84 + limited * 2.7, -.65, 1.82);
        double separation = Math.max(0, Math.abs(angle) - stallAngle);
        return linear / (1 + separation * 4);
    }

    public double inducedDrag(double liftCoefficient) {
        return liftCoefficient * liftCoefficient / (Math.PI * aspectRatio * efficiency);
    }
}
