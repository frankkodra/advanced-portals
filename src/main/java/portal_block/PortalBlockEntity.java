package portal_block;

import advanced_portals.Logger;
import advanced_portals.PortalBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import portal_multiblock.PortalMultiblockManager;
import portal_multiblock.PortalStructure;

import java.util.UUID;

public class PortalBlockEntity extends BlockEntity {
    public PortalStructure portalStructure;
    private UUID portalStructureId;
    public boolean joinedPortalStructure = false;

    // Primary storage system
    private boolean isPrimaryStorage = false;
    private UUID storageId = null;
    private CompoundTag storedPortalData = null;

    public PortalBlockEntity(BlockPos pos, BlockState state) {
        super(PortalBlockEntities.PORTAL_BLOCK_ENTITY.get(), pos, state);
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
            Logger.sendMessage("PortalBlockEntity at " + worldPosition + " loaded stored portal data", true);
        }

        if (tag.contains("portalStructureId")) {
            portalStructureId = tag.getUUID("portalStructureId");
            this.joinedPortalStructure = false;
        } else {
            this.joinedPortalStructure = true;
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PortalBlockEntity be) {
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
                    Logger.sendMessage("Restored portal data from PortalBlockEntity at " + pos, true);
                }

                be.portalStructure.addPortalBlock(pos);
                be.joinedPortalStructure = true;
                be.setChanged();
            }
        }

        // Visual effects or other tick logic can go here
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
            Logger.sendMessage("PortalBlockEntity at " + worldPosition + " saved portal data as primary storage", true);
        }

        // Always save the portal ID reference
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

    public PortalStructure getPortal() {
        return portalStructure;
    }
}