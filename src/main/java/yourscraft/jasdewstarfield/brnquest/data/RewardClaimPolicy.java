package yourscraft.jasdewstarfield.brnquest.data;

import java.util.Locale;

/** Native reward trigger and presentation policy; every automatic variant uses the normal claim ledger. */
public enum RewardClaimPolicy {
    MANUAL("manual", false, true, true),
    AUTO_VISIBLE("auto_visible", true, true, true),
    AUTO_SILENT("auto_silent", true, true, false),
    AUTO_HIDDEN("auto_hidden", true, false, false);

    private final String serializedName;
    private final boolean automatic;
    private final boolean visible;
    private final boolean notify;

    RewardClaimPolicy(String serializedName, boolean automatic, boolean visible, boolean notify) {
        this.serializedName = serializedName;
        this.automatic = automatic;
        this.visible = visible;
        this.notify = notify;
    }

    public String serializedName() { return serializedName; }
    public boolean automatic() { return automatic; }
    public boolean visible() { return visible; }
    public boolean notifyPlayer() { return notify; }

    public static RewardClaimPolicy parse(String value) {
        if (value == null) return MANUAL;
        String normalized = value.toLowerCase(Locale.ROOT);
        // Schema-1 books used "auto" before the visible/silent distinction existed.
        if (normalized.equals("auto")) return AUTO_VISIBLE;
        for (RewardClaimPolicy policy : values()) if (policy.serializedName.equals(normalized)) return policy;
        return MANUAL;
    }

    public static boolean isKnown(String value) {
        if (value == null) return false;
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.equals("auto")) return true;
        for (RewardClaimPolicy policy : values()) if (policy.serializedName.equals(normalized)) return true;
        return false;
    }
}
