package dev.milmod.model;

/** Wheel and skid locations in model coordinates, with the nose along negative Z. */
public final class VehicleSuspension {
    private static final VehicleSuspension NONE = new VehicleSuspension(false, 0, new Contact[0]);
    private static final VehicleSuspension TIGR = paired(false, .67F, .36F, -1.04F, 1.305F);
    private static final VehicleSuspension TRUCK = paired(false, .995F, .40F, -2.34F, -.729F, 1.643F, 2.715F);
    private static final VehicleSuspension BUK = paired(true, .94F, .25F, -1.84F, -1.06F, -.28F, .50F, 1.28F, 2.06F, 2.84F);
    private static final VehicleSuspension TOR = paired(true, 1.12F, .26F, -1.83F, -1.21F, -.59F, .03F, .65F, 1.27F, 1.89F);
    private static final VehicleSuspension T64 = paired(true, 1.057F, .265F, -.60F, -.044F, .511F, 1.066F, 1.621F, 2.177F);
    private static final VehicleSuspension M113 = paired(true, 1.07F, .30F, -1.61F, -.82F, -.03F, .76F, 1.55F);
    private static final VehicleSuspension LYUTYI = new VehicleSuspension(false, .112F, new Contact[]{
            new Contact(0, -1.31F), new Contact(-.37F, .166F), new Contact(.37F, .166F)});
    private static final VehicleSuspension MOHAJER = new VehicleSuspension(false, .13F, new Contact[]{
            new Contact(0, -1.23F), new Contact(-.43F, .29F), new Contact(.43F, .29F)});
    private static final VehicleSuspension MAVIC = paired(false, .23F, 0, -.24F, .24F);
    private static final VehicleSuspension FORPOST = new VehicleSuspension(false, .096F, new Contact[]{
            new Contact(0, -1.575F), new Contact(-.296F, .125F), new Contact(.296F, .125F)});
    private static final VehicleSuspension SMALL_SKIDS = paired(false, .16F, 0, -.47F, .49F);
    private static final VehicleSuspension TRAINER = new VehicleSuspension(false, .16F, new Contact[]{
            new Contact(0, -1.44F), new Contact(-.58F, .48F), new Contact(.58F, .48F)});

    private static final VehicleSuspension TRIPOD = new VehicleSuspension(false, 0, new Contact[]{
            new Contact(-.56F, -.51F), new Contact(.56F, -.51F), new Contact(0, .88F)});
    private static final VehicleSuspension FP1_RAMP = paired(false, .53F, 0, -.96F, .93F);
    private static final VehicleSuspension ORLAN_RAIL = paired(false, .52F, 0, -1.08F, 1.03F);

    private final Contact[] contacts;
    private final boolean tracks;
    private final float radius;
    private final float centerZ;

    private VehicleSuspension(boolean tracks, float radius, Contact[] contacts) {
        this.tracks = tracks;
        this.radius = radius;
        this.contacts = contacts;
        float sum = 0;
        for (Contact contact : contacts) sum += contact.z();
        centerZ = contacts.length == 0 ? 0 : sum / contacts.length;
    }

    private static VehicleSuspension paired(boolean tracks, float halfWidth, float radius, float... axleZ) {
        Contact[] contacts = new Contact[axleZ.length * 2];
        for (int i = 0; i < axleZ.length; i++) {
            contacts[i * 2] = new Contact(-halfWidth, axleZ[i]);
            contacts[i * 2 + 1] = new Contact(halfWidth, axleZ[i]);
        }
        return new VehicleSuspension(tracks, radius, contacts);
    }

    public static VehicleSuspension of(VehicleProfile profile) {
        return switch (profile) {
            case GAZ_TIGR -> TIGR;
            case ISKANDER_K -> TRUCK;
            case BUK_M3 -> BUK;
            case TOR_M1 -> TOR;
            case T64BV -> T64;
            case M113 -> M113;
            case MAVIC3T -> MAVIC;
            case FORPOST_R -> FORPOST;
            case L39_ALBATROS -> TRAINER;
            case FP1, ORLAN_10, GERAN4, SHAHED_238 -> SMALL_SKIDS;
            case LAUNCHER -> TRIPOD;
            case FP1_RAMP -> FP1_RAMP;
            case ORLAN_RAIL -> ORLAN_RAIL;
            case LYUTYI -> LYUTYI;
            case MOHAJER_6 -> MOHAJER;
            default -> NONE;
        };
    }

    public int size() { return contacts.length; }
    public Contact contact(int index) { return contacts[index]; }
    public boolean tracks() { return tracks; }
    public float radius() { return radius; }
    public float centerZ() { return centerZ; }

    public int nearest(float x, float z) {
        int index = -1;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < contacts.length; i++) {
            float dx = x - contacts[i].x(), dz = z - contacts[i].z();
            float distance = dx * dx + dz * dz;
            if (distance < best) {
                best = distance;
                index = i;
            }
        }
        return index;
    }

    public static long encode(long bits, int contact, float offset) {
        int value = Math.max(-8, Math.min(7, Math.round(offset / .055F)));
        return bits | (long) (value & 15) << (contact * 4);
    }

    public static float decode(long bits, int contact) {
        int value = (int) (bits >>> (contact * 4) & 15);
        return (value < 8 ? value : value - 16) * .055F;
    }

    public record Contact(float x, float z) {}
}
