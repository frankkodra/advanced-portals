package portal_multiblock;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.*;

public class PortalSettings {
    public enum PortalOpenDuration {
        FIFTEEN_SECONDS(15 * 20, "15 Seconds"),
        ONE_MINUTE(60 * 20, "1 Minute"),
        FIVE_MINUTES(5 * 60 * 20, "5 Minutes"),
        FIFTEEN_MINUTES(15 * 60 * 20, "15 Minutes"),
        PERSISTENT(-1, "Persistent");

        private final int ticks;
        private final String displayName;

        PortalOpenDuration(int ticks, String displayName) {
            this.ticks = ticks;
            this.displayName = displayName;
        }

        public int getTicks() { return ticks; }
        public String getDisplayName() { return displayName; }

        public static PortalOpenDuration fromDisplayName(String name) {
            for (PortalOpenDuration duration : values()) {
                if (duration.displayName.equals(name)) {
                    return duration;
                }
            }
            return PERSISTENT; // Default
        }
    }
    private String portalName;
    private PortalOpenDuration openDuration;
    private boolean closeAfterTeleport;
    private final List<UUID> linkedPortals;
    private final List<PortalLocation> savedLocations;
    private boolean isActivatingSide;
    private long activationTime;

    public PortalSettings() {
        this.portalName = "";
        this.openDuration = PortalOpenDuration.PERSISTENT;
        this.closeAfterTeleport = false;
        this.linkedPortals = new ArrayList<>();
        this.savedLocations = new ArrayList<>();
        this.isActivatingSide = false;
        this.activationTime = 0;
    }
    public List<String> getLocationNameList() {
        List<String> list = new ArrayList<>();
        for (PortalLocation location : savedLocations) {
            list.add(location.getName());
        }
        return list;
    }
    public void setPortalName(String name) { this.portalName = name; }
    public String getPortalName() { return portalName; }

    public void setOpenDuration(PortalOpenDuration duration) { this.openDuration = duration; }
    public PortalOpenDuration getOpenDuration() { return openDuration; }

    public void setCloseAfterTeleport(boolean close) { this.closeAfterTeleport = close; }
    public boolean shouldCloseAfterTeleport() { return closeAfterTeleport; }

    public List<UUID> getLinkedPortals() { return Collections.unmodifiableList(linkedPortals); }
    public void addLinkedPortal(UUID portalId) {
        if (!linkedPortals.contains(portalId) && portalId != null) {
            linkedPortals.add(portalId);
        }
    }
    public void removeLinkedPortal(UUID portalId) { linkedPortals.remove(portalId); }
    public void clearLinkedPortals() { linkedPortals.clear(); }

    public List<PortalLocation> getSavedLocations() { return Collections.unmodifiableList(savedLocations); }
    public void addSavedLocation(PortalLocation location) { savedLocations.add(location); }
    public void removeSavedLocation(String name) { savedLocations.removeIf(loc -> loc.getName().equals(name)); }
    public PortalLocation getLocationByName(String name) {
        return savedLocations.stream()
                .filter(loc -> loc.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
    public void importLocations(List<PortalLocation> locations) {
        for (PortalLocation loc : locations) {
            if (getLocationByName(loc.getName()) == null) {
                savedLocations.add(loc);
            }
        }
    }

    public void setActivatingSide(boolean activating) { this.isActivatingSide = activating; }
    public boolean isActivatingSide() { return isActivatingSide; }

    public void setActivationTime(long time) { this.activationTime = time; }
    public long getActivationTime() { return activationTime; }

    public boolean isDurationExpired(long currentTime) {
        if (openDuration == PortalOpenDuration.PERSISTENT) return false;
        return currentTime - activationTime >= openDuration.getTicks();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("portalName", portalName);
        tag.putString("openDuration", openDuration.name());
        tag.putBoolean("closeAfterTeleport", closeAfterTeleport);
        tag.putBoolean("isActivatingSide", isActivatingSide);
        tag.putLong("activationTime", activationTime);

        // Save linked portals
        ListTag linkedList = new ListTag();
        for (UUID portalId : linkedPortals) {
            CompoundTag portalTag = new CompoundTag();
            portalTag.putUUID("portalId", portalId);
            linkedList.add(portalTag);
        }
        tag.put("linkedPortals", linkedList);

        // Save locations
        ListTag locationsList = new ListTag();
        for (PortalLocation location : savedLocations) {
            locationsList.add(location.save());
        }
        tag.put("savedLocations", locationsList);

        return tag;
    }

    public void load(CompoundTag tag) {
        portalName = tag.getString("portalName");
        openDuration = PortalOpenDuration.valueOf(tag.getString("openDuration"));
        closeAfterTeleport = tag.getBoolean("closeAfterTeleport");
        isActivatingSide = tag.getBoolean("isActivatingSide");
        activationTime = tag.getLong("activationTime");

        // Load linked portals
        linkedPortals.clear();
        ListTag linkedList = tag.getList("linkedPortals", Tag.TAG_COMPOUND);
        for (int i = 0; i < linkedList.size(); i++) {
            CompoundTag portalTag = linkedList.getCompound(i);
            linkedPortals.add(portalTag.getUUID("portalId"));
        }

        // Load locations
        savedLocations.clear();
        ListTag locationsList = tag.getList("savedLocations", Tag.TAG_COMPOUND);
        for (int i = 0; i < locationsList.size(); i++) {
            savedLocations.add(PortalLocation.load(locationsList.getCompound(i)));
        }
    }
}