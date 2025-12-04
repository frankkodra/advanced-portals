package portal_controller;

import advanced_portals.Logger;
import advanced_portals.PortalBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkHooks;
import portal_multiblock.*;

import java.util.UUID;
import java.util.List;
import java.util.Collections;

public class PortalControllerBlockEntity extends BlockEntity {
    public PortalStructure portalStructure;
    private UUID portalStructureId;
    public boolean joinedPortalStructure = false;

    // Primary storage system
    private boolean isPrimaryStorage = false;
    private UUID storageId = null;
    private CompoundTag storedPortalData = null;

    public PortalControllerBlockEntity(BlockPos pos, BlockState state) {
        super(PortalBlockEntities.PORTAL_CONTROLLER_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        // Load primary storage info
        if (tag.contains("isPrimaryStorage")) {
            this.isPrimaryStorage = tag.getBoolean("isPrimaryStorage");
        }
        if (tag.contains("storageId")) {
            this.storageId = tag.getUUID("storageId");
        }

        // Load stored portal data if we have it
        if (tag.contains("portalStructureData")) {
            this.storedPortalData = tag.getCompound("portalStructureData");
            Logger.sendMessage("PortalControllerBlockEntity at " + worldPosition + " loaded stored portal data", true);
        }

        if (tag.contains("portalStructureId")) {
            portalStructureId = tag.getUUID("portalStructureId");
            this.joinedPortalStructure = false;
        } else {
            this.joinedPortalStructure = true;
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PortalControllerBlockEntity be) {
        if (!level.isClientSide && !be.joinedPortalStructure) {
            if (be.portalStructureId == null) {
                be.joinedPortalStructure = true;
                return;
            }

            // Get or create the portal structure
            be.portalStructure = PortalStructure.getOrCreatePortalStructure(be.portalStructureId, level);

            if (be.portalStructure != null) {
                // Check if we need to transfer data TO this block
                if (be.portalStructure.needsPrimaryStorage()) {
                    be.portalStructure.transferPrimaryStorage();
                }

                // Check if we have stored data that needs to be restored
                if (be.storedPortalData != null && be.isPrimaryStorage) {
                    be.portalStructure.loadFromNBT(be.storedPortalData);
                    be.storedPortalData = null; // Clear after loading
                    Logger.sendMessage("Restored portal data from PortalControllerBlockEntity at " + pos, true);
                }

                be.portalStructure.addPortalControllerBlock(be);
                be.joinedPortalStructure = true;
                be.setChanged();
            }
        }

        // Tick the portal structure
        if (be.portalStructure != null && !level.isClientSide) {
            // PortalStructure tick is now handled by PortalManager
            // We just need to ensure the structure is still valid
            if (!be.portalStructure.isValid() && be.portalStructure.isActive()) {
                be.portalStructure.setActive(false);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        // Save primary storage info
        tag.putBoolean("isPrimaryStorage", isPrimaryStorage);
        if (storageId != null) {
            tag.putUUID("storageId", storageId);
        }

        // If we are primary storage and have a portal structure, save all data
        if (isPrimaryStorage && portalStructure != null) {
            CompoundTag portalData = portalStructure.saveToNBT();
            tag.put("portalStructureData", portalData);
            Logger.sendMessage("PortalControllerBlockEntity at " + worldPosition + " saved portal data as primary storage", true);
        }

        if (portalStructure != null) {
            tag.putUUID("portalStructureId", portalStructure.getPortalId());
        } else if (portalStructureId != null) {
            tag.putUUID("portalStructureId", portalStructureId);
        }
    }

    public PortalStructure getPortalStructure() {
        return portalStructure;
    }

    public void setPortalStructure(PortalStructure portalStructure) {
        this.portalStructure = portalStructure;
        if (portalStructure != null) {
            this.portalStructureId = portalStructure.getPortalId();
            this.joinedPortalStructure = true;
            this.setChanged();
        }
    }

    // Primary storage methods
    public void setPrimaryStorage(boolean primary, UUID storageId) {
        this.isPrimaryStorage = primary;
        this.storageId = storageId;
        this.setChanged();
    }

    public boolean isPrimaryStorage() {
        return isPrimaryStorage;
    }

    public UUID getStorageId() {
        return storageId;
    }

    public boolean hasStoredData() {
        return storedPortalData != null;
    }

    public void openGui(Player player) {
        if (level != null && !level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            PortalStructure portal = getPortalStructure();
            if (portal != null) {
                NetworkHooks.openScreen(serverPlayer,
                        new PortalControllerMenuProvider(portal.getPortalId(), worldPosition),
                        buf -> {
                            // 1. Write the mandatory base data
                            buf.writeUUID(portal.getPortalId());
                            buf.writeBlockPos(worldPosition);

                            // 2. Write ALL portal data for the GUI
                            buf.writeUtf(portal.getPortalName());
                            buf.writeBoolean(portal.isActive());
                            buf.writeBoolean(portal.isValid());

                            // Linked portals
                            List<UUID> linkedPortals = portal.getLinkedPortals();
                            buf.writeInt(linkedPortals.size());
                            for (UUID linkedId : linkedPortals) {
                                buf.writeUUID(linkedId);
                            }

                            // Saved locations
                            List<PortalLocation> savedLocations = portal.getSettings().getSavedLocations();
                            buf.writeInt(savedLocations.size());
                            for (PortalLocation location : savedLocations) {
                                location.writeToBuffer(buf);
                            }

                            // Settings
                            buf.writeInt(portal.getSettings().getOpenDuration().ordinal());
                            buf.writeBoolean(portal.shouldCloseAfterTeleport());

                            // Resources
                            buf.writeFloat(portal.getPowerFillPercentage());
                            buf.writeFloat(portal.getFluidFillPercentage());
                        });
            }
        }
    }

    public boolean linkToPortal(String targetPortalName) {
        PortalStructure portal = getPortalStructure();
        if (portal == null) return false;

        PortalStructure targetPortal = PortalMultiblockManager.getPortalByName(targetPortalName);
        if (targetPortal != null && !targetPortal.getPortalId().equals(portal.getPortalId())) {
            portal.addLinkedPortal(targetPortal.getPortalId());
            targetPortal.addLinkedPortal(portal.getPortalId());
            setChanged();
            return true;
        }
        return false;
    }

    public boolean unlinkPortal(String targetPortalName) {
        PortalStructure portal = getPortalStructure();
        if (portal == null) return false;

        PortalStructure targetPortal = PortalMultiblockManager.getPortalByName(targetPortalName);
        if (targetPortal != null) {
            portal.removeLinkedPortal(targetPortal.getPortalId());
            targetPortal.removeLinkedPortal(portal.getPortalId());
            setChanged();
            return true;
        }
        return false;
    }





    public boolean setPortalName(String name) {
        PortalStructure portal = getPortalStructure();
        if (portal != null && name != null && !name.trim().isEmpty()) {
            portal.setPortalName(name.trim());
            setChanged();
            return true;
        }
        return false;
    }

    public String getPortalStatus() {
        PortalStructure portal = getPortalStructure();
        if (portal != null) {
            return portal.getStatus();
        }
        return "No Portal Structure";
    }

    public boolean setOpenDuration(String durationName) {
        PortalStructure portal = getPortalStructure();
        if (portal != null) {
            // Need to access PortalSettings.PortalOpenDuration enum
            PortalSettings.PortalOpenDuration duration = PortalSettings.PortalOpenDuration.fromDisplayName(durationName);
            if (duration != null) {
                portal.getSettings().setOpenDuration(duration);
                setChanged();
                return true;
            }
        }
        return false;
    }

    public boolean setCloseAfterTeleport(boolean close) {
        PortalStructure portal = getPortalStructure();
        if (portal != null) {
            portal.setCloseAfterTeleport(close);
            setChanged();
            return true;
        }
        return false;
    }

    // New GUI methods for enhanced functionality
    public boolean importLocationsFromPortal(String sourcePortalName) {
        PortalStructure portal = getPortalStructure();
        if (portal == null) return false;

        PortalStructure sourcePortal = PortalMultiblockManager.getPortalByName(sourcePortalName);
        if (sourcePortal != null && !sourcePortal.getPortalId().equals(portal.getPortalId())) {
            portal.getSettings().importLocations(sourcePortal.getSettings().getSavedLocations());
            setChanged();
            Logger.sendMessage("Imported locations from portal " + sourcePortalName, true);
            return true;
        }
        return false;
    }

    public boolean addSavedLocation(String name, Player player) {
        PortalStructure portal = getPortalStructure();
        if (portal == null || name == null || name.trim().isEmpty() || player == null) return false;

        // Create a PortalLocation from player's current position
        PortalLocation location = new PortalLocation(
                name.trim(),
                player.level().dimension(),
                player.blockPosition(),
                player.getYRot(),
                player.getXRot()
        );

        portal.getSettings().addSavedLocation(location);
        setChanged();
        Logger.sendMessage("Saved location '" + name + "' for portal", true);
        return true;
    }

    public boolean teleportToSavedLocation(String locationName, Player player) {
        PortalStructure portal = getPortalStructure();
        if (portal == null || locationName == null || player == null) return false;

        PortalLocation location = portal.getSettings().getLocationByName(locationName);
        if (location == null) return false;

        // Check if portal is active and has power
        if (!portal.isActive() || !portal.hasPower()) {
            Logger.sendMessage("Cannot teleport: Portal is not active or has no power", true);
            return false;
        }

        // Get the target dimension level
        var server = player.getServer();
        if (server == null) return false;

        var targetLevel = server.getLevel(location.getDimension());
        if (targetLevel == null) {
            Logger.sendMessage("Cannot teleport: Target dimension not found", true);
            return false;
        }

        // Teleport the player
        if (player.changeDimension(targetLevel) != null) {
            player.teleportTo(
                    location.getPosition().getX() + 0.5,
                    location.getPosition().getY(),
                    location.getPosition().getZ() + 0.5
            );
            player.setYRot(location.getYaw());
            player.setXRot(location.getPitch());

            Logger.sendMessage("Teleported player to saved location '" + locationName + "'", true);

            // Check if portal should close after teleport
            if (portal.shouldCloseAfterTeleport() && portal.isActivatingSide()) {
                portal.setActive(false);
            }

            return true;
        }

        return false;
    }

    // Get portal info for GUI
    public String getPortalName() {
        PortalStructure portal = getPortalStructure();
        return portal != null ? portal.getPortalName() : "Unnamed Portal";
    }

    public float getPowerFillPercentage() {
        PortalStructure portal = getPortalStructure();
        return portal != null ? portal.getPowerFillPercentage() : 0.0f;
    }

    public float getFluidFillPercentage() {
        PortalStructure portal = getPortalStructure();
        return portal != null ? portal.getFluidFillPercentage() : 0.0f;
    }

    public boolean isPortalValid() {
        PortalStructure portal = getPortalStructure();
        return portal != null && portal.isValid();
    }

    public boolean isPortalActive() {
        PortalStructure portal = getPortalStructure();
        return portal != null && portal.isActive();
    }

    public List<UUID> getLinkedPortalIds() {
        PortalStructure portal = getPortalStructure();
        return portal != null ? portal.getLinkedPortals() : Collections.emptyList();
    }

    public List<PortalLocation> getSavedLocations() {
        PortalStructure portal = getPortalStructure();
        return portal != null ? portal.getSettings().getSavedLocations() : Collections.emptyList();
    }

    public PortalSettings.PortalOpenDuration getOpenDuration() {
        PortalStructure portal = getPortalStructure();
        return portal != null ? portal.getSettings().getOpenDuration() : PortalSettings.PortalOpenDuration.PERSISTENT;
    }

    public boolean getCloseAfterTeleport() {
        PortalStructure portal = getPortalStructure();
        return portal != null && portal.shouldCloseAfterTeleport();
    }
}