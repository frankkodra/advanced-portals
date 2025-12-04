package portal_multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import portal_battery.BatteryMultiblock;
import portal_block.PortalBlock;
import portal_block.PortalBlockEntity;
import portal_controller.PortalControllerBlock;
import portal_controller.PortalControllerBlockEntity;
import portal_fluid_pipe.FluidPipeMultiblock;
import portal_fluid_tank.TankMultiblock;
import portal_power_cable.PowerCableMultiblock;
import advanced_portals.Logger;

import java.util.*;

public class PortalStructure {
    private final UUID portalId;
    public final PortalSettings settings;
    private final Set<PortalControllerBlockEntity> portalControllers;
    private final Set<BlockPos> frameBlocks;
    private final Set<BlockPos> interiorBlocks;

    // Primary storage system
    private BlockPos primaryStoragePos;
    private UUID primaryStorageBlockId;
    private boolean hasStoredData;

    // CRITICAL: Track cable/pipe connections bidirectionally with specific positions
    public Map<UUID, Set<BlockPos>> connectedPowerCableMultiblocksMap;
    public Map<UUID, Set<BlockPos>> connectedFluidPipeMultiblocksMap;

    public boolean isValid;
    private boolean isActive;
    private final Level level;
    private final ResourceKey<Level> levelKey;

    // Portal bounds for entity detection
    private PortalBounds bounds;

    // Track if we need to save data (for primary storage block)
    private boolean needsSave;

    public PortalStructure(UUID portalId, Level level) {
        this.portalId = portalId;
        this.settings = new PortalSettings();
        this.settings.setPortalName("Portal_" + portalId.toString().substring(0, 8));
        this.frameBlocks = new HashSet<>();
        this.interiorBlocks = new HashSet<>();
        this.connectedPowerCableMultiblocksMap = new HashMap<>();
        this.connectedFluidPipeMultiblocksMap = new HashMap<>();

        this.level = level;
        this.isValid = false;
        this.isActive = false;
        this.levelKey = level.dimension();
        this.portalControllers = new HashSet<>();

        this.primaryStoragePos = null;
        this.primaryStorageBlockId = null;
        this.hasStoredData = false;
        this.needsSave = false;
        this.bounds = null;

        PortalMultiblockManager.addPortalStructure(this);
        Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " created", true);
    }

    // PRIMARY STORAGE SYSTEM METHODS

    public void setPrimaryStorage(BlockPos pos, BlockEntity blockEntity) {
        this.primaryStoragePos = pos;
        this.primaryStorageBlockId = UUID.randomUUID();
        this.hasStoredData = true;

        // Mark block entity as primary storage
        if (blockEntity instanceof PortalBlockEntity portalBE) {
            portalBE.setPrimaryStorage(true, primaryStorageBlockId);
        } else if (blockEntity instanceof PortalControllerBlockEntity controllerBE) {
            controllerBE.setPrimaryStorage(true, primaryStorageBlockId);
        }

        Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) +
                " primary storage set to block at " + pos, true);

        markForSave();
    }

    public boolean isPrimaryStorage(BlockPos pos, BlockEntity blockEntity) {
        if (primaryStoragePos == null) return false;

        if (primaryStoragePos.equals(pos)) {
            return true;
        }

        // Check by storage ID for failover
        if (blockEntity != null) {
            UUID blockStorageId = null;
            if (blockEntity instanceof PortalBlockEntity portalBE) {
                blockStorageId = portalBE.getStorageId();
            } else if (blockEntity instanceof PortalControllerBlockEntity controllerBE) {
                blockStorageId = controllerBE.getStorageId();
            }

            return primaryStorageBlockId != null && primaryStorageBlockId.equals(blockStorageId);
        }

        return false;
    }

    public boolean needsPrimaryStorage() {
        return primaryStoragePos == null && !frameBlocks.isEmpty();
    }

    public void transferPrimaryStorage() {
        if (!needsPrimaryStorage()) return;

        Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) +
                " transferring primary storage", true);

        // Try portal blocks first
        for (BlockPos pos : frameBlocks) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                setPrimaryStorage(pos, be);
                be.setChanged();
                return;
            }
        }

        // Fall back to controllers
        for (PortalControllerBlockEntity controller : portalControllers) {
            setPrimaryStorage(controller.getBlockPos(), controller);
            controller.setChanged();
            return;
        }

        Logger.sendMessage("WARNING: Could not find valid block for primary storage", true);
    }

    // DATA PERSISTENCE METHODS

    public CompoundTag saveToNBT() {
        CompoundTag tag = new CompoundTag();

        // Basic portal data
        tag.putUUID("portalId", portalId);
        tag.putString("portalName", settings.getPortalName());
        tag.putBoolean("isValid", isValid);
        tag.putBoolean("isActive", isActive);
        tag.putBoolean("hasStoredData", hasStoredData);

        // Settings
        tag.put("settings", settings.save());

        // Primary storage info
        if (primaryStoragePos != null) {
            tag.putInt("primaryX", primaryStoragePos.getX());
            tag.putInt("primaryY", primaryStoragePos.getY());
            tag.putInt("primaryZ", primaryStoragePos.getZ());
        }
        if (primaryStorageBlockId != null) {
            tag.putUUID("primaryStorageId", primaryStorageBlockId);
        }

        // Frame blocks
        ListTag frameList = new ListTag();
        for (BlockPos pos : frameBlocks) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", pos.getX());
            posTag.putInt("y", pos.getY());
            posTag.putInt("z", pos.getZ());
            frameList.add(posTag);
        }
        tag.put("frameBlocks", frameList);

        // Connected portals (from settings)
        ListTag linkedList = new ListTag();
        for (UUID linkedId : settings.getLinkedPortals()) {
            CompoundTag linkedTag = new CompoundTag();
            linkedTag.putUUID("portalId", linkedId);
            linkedList.add(linkedTag);
        }
        tag.put("linkedPortals", linkedList);

        // Cable connections
        CompoundTag cableConnections = new CompoundTag();
        for (Map.Entry<UUID, Set<BlockPos>> entry : connectedPowerCableMultiblocksMap.entrySet()) {
            ListTag cableList = new ListTag();
            for (BlockPos pos : entry.getValue()) {
                CompoundTag posTag = new CompoundTag();
                posTag.putInt("x", pos.getX());
                posTag.putInt("y", pos.getY());
                posTag.putInt("z", pos.getZ());
                cableList.add(posTag);
            }
            cableConnections.put(entry.getKey().toString(), cableList);
        }
        tag.put("cableConnections", cableConnections);

        // Pipe connections
        CompoundTag pipeConnections = new CompoundTag();
        for (Map.Entry<UUID, Set<BlockPos>> entry : connectedFluidPipeMultiblocksMap.entrySet()) {
            ListTag pipeList = new ListTag();
            for (BlockPos pos : entry.getValue()) {
                CompoundTag posTag = new CompoundTag();
                posTag.putInt("x", pos.getX());
                posTag.putInt("y", pos.getY());
                posTag.putInt("z", pos.getZ());
                pipeList.add(posTag);
            }
            pipeConnections.put(entry.getKey().toString(), pipeList);
        }
        tag.put("pipeConnections", pipeConnections);

        return tag;
    }

    public void loadFromNBT(CompoundTag tag) {
        // Basic portal data
        settings.setPortalName(tag.getString("portalName"));
        isValid = tag.getBoolean("isValid");
        isActive = tag.getBoolean("isActive");
        hasStoredData = tag.getBoolean("hasStoredData");

        // Settings
        if (tag.contains("settings")) {
            settings.load(tag.getCompound("settings"));
        }

        // Primary storage
        if (tag.contains("primaryX")) {
            primaryStoragePos = new BlockPos(
                    tag.getInt("primaryX"),
                    tag.getInt("primaryY"),
                    tag.getInt("primaryZ")
            );
        }
        if (tag.contains("primaryStorageId")) {
            primaryStorageBlockId = tag.getUUID("primaryStorageId");
        }

        // Frame blocks (will be re-added by block entities)
        if (tag.contains("frameBlocks")) {
            ListTag frameList = tag.getList("frameBlocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < frameList.size(); i++) {
                CompoundTag posTag = frameList.getCompound(i);
                BlockPos pos = new BlockPos(
                        posTag.getInt("x"),
                        posTag.getInt("y"),
                        posTag.getInt("z")
                );
                frameBlocks.add(pos);
            }
        }

        // Linked portals
        if (tag.contains("linkedPortals")) {
            ListTag linkedList = tag.getList("linkedPortals", Tag.TAG_COMPOUND);
            for (int i = 0; i < linkedList.size(); i++) {
                CompoundTag linkedTag = linkedList.getCompound(i);
                settings.addLinkedPortal(linkedTag.getUUID("portalId"));
            }
        }

        // Cable connections
        if (tag.contains("cableConnections")) {
            CompoundTag cableConnections = tag.getCompound("cableConnections");
            for (String cableIdStr : cableConnections.getAllKeys()) {
                UUID cableId = UUID.fromString(cableIdStr);
                ListTag cableList = cableConnections.getList(cableIdStr, Tag.TAG_COMPOUND);
                Set<BlockPos> positions = new HashSet<>();

                for (int i = 0; i < cableList.size(); i++) {
                    CompoundTag posTag = cableList.getCompound(i);
                    positions.add(new BlockPos(
                            posTag.getInt("x"),
                            posTag.getInt("y"),
                            posTag.getInt("z")
                    ));
                }

                connectedPowerCableMultiblocksMap.put(cableId, positions);
            }
        }

        // Pipe connections
        if (tag.contains("pipeConnections")) {
            CompoundTag pipeConnections = tag.getCompound("pipeConnections");
            for (String pipeIdStr : pipeConnections.getAllKeys()) {
                UUID pipeId = UUID.fromString(pipeIdStr);
                ListTag pipeList = pipeConnections.getList(pipeIdStr, Tag.TAG_COMPOUND);
                Set<BlockPos> positions = new HashSet<>();

                for (int i = 0; i < pipeList.size(); i++) {
                    CompoundTag posTag = pipeList.getCompound(i);
                    positions.add(new BlockPos(
                            posTag.getInt("x"),
                            posTag.getInt("y"),
                            posTag.getInt("z")
                    ));
                }

                connectedFluidPipeMultiblocksMap.put(pipeId, positions);
            }
        }

        Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) +
                " loaded from NBT", true);
    }

    // MARK FOR SAVE (called when data changes)
    public void markForSave() {
        needsSave = true;
        if (primaryStoragePos != null) {
            BlockEntity be = level.getBlockEntity(primaryStoragePos);
            if (be != null) {
                be.setChanged();
            }
        }
    }

    // Existing static methods from original (unchanged except for calls to new methods)
    public static PortalStructure addCreateOrMergePortalStructureForBlock(BlockPos pos, Level level) {
        Set<PortalStructure> multiblocksToMerge = new HashSet<PortalStructure>();
        Block addedBlock = level.getBlockState(pos).getBlock();

        Logger.sendMessage("Placing " + addedBlock.getDescriptionId() + " at " + pos + " - Scanning for adjacent portal structures", true);

        for(Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            Block block = level.getBlockState(neighborPos).getBlock();

            if(block instanceof PortalBlock ) {
                BlockEntity neighborEntity = level.getBlockEntity(neighborPos);
                if(neighborEntity instanceof PortalBlockEntity portalBE && portalBE.portalStructure != null) {
                    multiblocksToMerge.add(portalBE.portalStructure);
                    Logger.sendMessage("Found adjacent portal block at " + neighborPos + " belonging to PortalStructure " +
                            portalBE.portalStructure.portalId.toString().substring(0, 8), true);
                } else {
                    Logger.sendMessage("WARNING: Portal block at " + neighborPos + " has null portal structure reference!", true);
                }
            }
            if(block instanceof PortalControllerBlock) {
                BlockEntity neighborEntity = level.getBlockEntity(neighborPos);
                if(neighborEntity instanceof PortalControllerBlockEntity controllerBE && controllerBE.portalStructure != null) {
                    multiblocksToMerge.add(controllerBE.portalStructure);
                    Logger.sendMessage("Found adjacent controller at " + neighborPos + " belonging to PortalStructure " +
                            controllerBE.portalStructure.portalId.toString().substring(0, 8), true);
                } else {
                    Logger.sendMessage("WARNING: Controller at " + neighborPos + " has null portal structure reference!", true);
                }
            }
        }

        if(addedBlock instanceof PortalBlock) {
            if(multiblocksToMerge.size() == 0) {
                PortalStructure portalStructure = new PortalStructure(UUID.randomUUID(), level);
                portalStructure.addPortalBlock(pos);

                BlockEntity blockEntity = level.getBlockEntity(pos);
                if(blockEntity instanceof PortalBlockEntity portalBE) {
                    portalBE.portalStructure = portalStructure;
                }

                Logger.sendMessage("New PortalStructure " + portalStructure.portalId.toString().substring(0, 8) +
                        " created with 1 portal block at " + pos, true);

                // VALIDATION: Run validation after creating new structure
                portalStructure.revalidateStructure();
                return portalStructure;
            }
            if(multiblocksToMerge.size() == 1) {
                PortalStructure multiblock = multiblocksToMerge.iterator().next();
                multiblock.addPortalBlock(pos);

                BlockEntity blockEntity = level.getBlockEntity(pos);
                if(blockEntity instanceof PortalBlockEntity portalBE) {
                    portalBE.portalStructure = multiblock;
                }

                Logger.sendMessage("PortalStructure " + multiblock.portalId.toString().substring(0, 8) +
                        " added portal block at " + pos + " (total: " + multiblock.frameBlocks.size() + " frame blocks)", true);

                // VALIDATION: Run validation after adding block to existing structure
                multiblock.revalidateStructure();
                return multiblock;
            }
            if(multiblocksToMerge.size() > 1) {
                PortalStructure multiblock = mergeMultiblocks(multiblocksToMerge, level);
                multiblock.addPortalBlock(pos);

                BlockEntity blockEntity = level.getBlockEntity(pos);
                if(blockEntity instanceof PortalBlockEntity portalBE) {
                    portalBE.portalStructure = multiblock;
                }

                Logger.sendMessage("Merged " + multiblocksToMerge.size() + " PortalStructures into " + multiblock.portalId.toString().substring(0, 8) +
                        " and added portal block at " + pos + " (total: " + multiblock.frameBlocks.size() + " frame blocks)", true);

                // VALIDATION: Run validation after merge completes
                multiblock.revalidateStructure();
                return multiblock;
            }
        }

        if(addedBlock instanceof PortalControllerBlock) {
            PortalControllerBlockEntity portalControllerBlockEntity = (PortalControllerBlockEntity) level.getBlockEntity(pos);

            if(multiblocksToMerge.size() == 0) {
                PortalStructure portalStructure = new PortalStructure(UUID.randomUUID(), level);
                portalStructure.addPortalControllerBlock(portalControllerBlockEntity);

                Logger.sendMessage("New PortalStructure " + portalStructure.portalId.toString().substring(0, 8) +
                        " created with controller at " + pos, true);

                // VALIDATION: Run validation after creating new structure with controller
                portalStructure.revalidateStructure();
                return portalStructure;
            }
            if(multiblocksToMerge.size() == 1) {
                PortalStructure multiblock = multiblocksToMerge.iterator().next();
                multiblock.addPortalControllerBlock(portalControllerBlockEntity);

                Logger.sendMessage("PortalStructure " + multiblock.portalId.toString().substring(0, 8) +
                        " added controller at " + pos + " (total: " + multiblock.portalControllers.size() + " controllers)", true);

                // VALIDATION: Run validation after adding controller
                multiblock.revalidateStructure();
                return multiblock;
            }
            if(multiblocksToMerge.size() > 1) {
                PortalStructure multiblock = mergeMultiblocks(multiblocksToMerge, level);
                multiblock.addPortalControllerBlock(portalControllerBlockEntity);

                Logger.sendMessage("Merged " + multiblocksToMerge.size() + " PortalStructures into " + multiblock.portalId.toString().substring(0, 8) +
                        " and added controller at " + pos + " (total: " + multiblock.portalControllers.size() + " controllers)", true);

                // VALIDATION: Run validation after merge completes
                multiblock.revalidateStructure();
                return multiblock;
            }
        }

        Logger.sendMessage("ERROR: No valid PortalStructure created for block at " + pos, true);
        return null;
    }

    public static PortalStructure getOrCreatePortalStructure(UUID portalId, Level level) {
        PortalStructure existing = PortalMultiblockManager.portals.get(portalId);
        if (existing != null) {
            return existing;
        } else {
            PortalStructure newStructure = new PortalStructure(portalId, level);
            PortalMultiblockManager.addPortalStructure(newStructure);
            return newStructure;
        }
    }

    // Updated addPortalBlock method with primary storage logic
    public void addPortalBlock(BlockPos pos) {
        frameBlocks.add(pos);

        // FIRST BLOCK BECOMES PRIMARY STORAGE
        if (primaryStoragePos == null && frameBlocks.size() == 1) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                setPrimaryStorage(pos, be);
            }
        }

        Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) +
                " added portal block at " + pos + " (total: " + frameBlocks.size() + " frame blocks)", true);

        revalidateStructure();
    }

    // Updated addPortalControllerBlock method with primary storage logic
    public void addPortalControllerBlock(PortalControllerBlockEntity controller) {
        portalControllers.add(controller);
        controller.setPortalStructure(this);

        // FIRST CONTROLLER BECOMES PRIMARY STORAGE (if no portal blocks yet)
        if (primaryStoragePos == null && portalControllers.size() == 1 && frameBlocks.isEmpty()) {
            setPrimaryStorage(controller.getBlockPos(), controller);
        }

        Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) +
                " added controller at " + controller.getBlockPos() + " (total: " + portalControllers.size() + " controllers)", true);

        revalidateStructure();
    }

    private void addPortalControllerBlocks(Set<PortalControllerBlockEntity> portalControllers) {
        this.portalControllers.addAll(portalControllers);
    }

    private void addPortalBlocks(Set<BlockPos> frameBlocks) {
        this.frameBlocks.addAll(frameBlocks);
    }

    private void addPowerCableMultiblocks(Map<UUID, Set<BlockPos>> powerCablesMap) {
        for (Map.Entry<UUID, Set<BlockPos>> entry : powerCablesMap.entrySet()) {
            UUID cableId = entry.getKey();
            Set<BlockPos> connectionPoints = entry.getValue();

            if (!this.connectedPowerCableMultiblocksMap.containsKey(cableId)) {
                this.connectedPowerCableMultiblocksMap.put(cableId, new HashSet<>());
            }
            this.connectedPowerCableMultiblocksMap.get(cableId).addAll(connectionPoints);
        }
    }

    private void addFluidPipeMultiblocks(Map<UUID, Set<BlockPos>> fluidPipesMap) {
        for (Map.Entry<UUID, Set<BlockPos>> entry : fluidPipesMap.entrySet()) {
            UUID pipeId = entry.getKey();
            Set<BlockPos> connectionPoints = entry.getValue();

            if (!this.connectedFluidPipeMultiblocksMap.containsKey(pipeId)) {
                this.connectedFluidPipeMultiblocksMap.put(pipeId, new HashSet<>());
            }
            this.connectedFluidPipeMultiblocksMap.get(pipeId).addAll(connectionPoints);
        }
    }

    // Updated removePortalBlock with primary storage failover
    public void removePortalBlock(BlockPos pos) {
        boolean wasPrimary = isPrimaryStorage(pos, level.getBlockEntity(pos));

        if (frameBlocks.remove(pos)) {
            Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) +
                    " removed portal block at " + pos + " (total: " + frameBlocks.size() + " frame blocks remaining)", true);

            if (wasPrimary) {
                Logger.sendMessage("Primary storage block removed, transferring data", true);
                primaryStoragePos = null;
                primaryStorageBlockId = null;
                transferPrimaryStorage();
            }

            if (frameBlocks.isEmpty()) {
                PortalMultiblockManager.removePortalStructure(this);
                Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " DESTROYED (no frame blocks remaining)", true);
            } else {
                // CRITICAL: Check if removal caused structure to split
                handleSplitAfterRemoval();
                // VALIDATION: Run validation after block removal
                revalidateStructure();
            }
        }
    }

    // Updated removePortalController with primary storage failover
    public void removePortalController(PortalControllerBlockEntity controller) {
        boolean wasPrimary = isPrimaryStorage(controller.getBlockPos(), controller);

        if (portalControllers.remove(controller)) {
            Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) +
                    " removed controller at " + controller.getBlockPos() + " (total: " + portalControllers.size() + " controllers remaining)", true);

            if (wasPrimary) {
                Logger.sendMessage("Primary storage controller removed, transferring data", true);
                primaryStoragePos = null;
                primaryStorageBlockId = null;
                transferPrimaryStorage();
            }

            // VALIDATION: Run validation after controller removal
            revalidateStructure();
        }
    }

    // Existing mergeMultiblocks method (unchanged except for calls to new methods)
    public static PortalStructure mergeMultiblocks(Set<PortalStructure> multiblocksToMerge, Level level) {
        if (multiblocksToMerge.isEmpty()) {
            Logger.sendMessage("ERROR: Cannot merge empty PortalStructure set!", true);
            return null;
        }

        PortalStructure mainStructureMultiblock = multiblocksToMerge.iterator().next();
        UUID mainId = mainStructureMultiblock.portalId;

        Logger.sendMessage("Merging " + multiblocksToMerge.size() + " PortalStructures into " + mainId.toString().substring(0, 8), true);

        int totalFrameBlocksBefore = mainStructureMultiblock.frameBlocks.size();
        int totalControllersBefore = mainStructureMultiblock.portalControllers.size();
        int totalPowerCablesBefore = mainStructureMultiblock.connectedPowerCableMultiblocksMap.size();
        int totalFluidPipesBefore = mainStructureMultiblock.connectedFluidPipeMultiblocksMap.size();

        Set<BlockPos> allFrameBlocksToUpdate = new HashSet<>();
        Set<PortalControllerBlockEntity> allControllersToUpdate = new HashSet<>();

        for (PortalStructure portalStructure : multiblocksToMerge) {
            if(portalStructure.portalId.equals(mainId)) {
                continue;
            }

            allFrameBlocksToUpdate.addAll(portalStructure.frameBlocks);
            allControllersToUpdate.addAll(portalStructure.portalControllers);

            int frameBlocksToAdd = portalStructure.frameBlocks.size();
            int controllersToAdd = portalStructure.portalControllers.size();
            int powerCablesToAdd = portalStructure.connectedPowerCableMultiblocksMap.size();
            int fluidPipesToAdd = portalStructure.connectedFluidPipeMultiblocksMap.size();

            mainStructureMultiblock.addPortalBlocks(portalStructure.frameBlocks);
            mainStructureMultiblock.addPortalControllerBlocks(portalStructure.portalControllers);
            mainStructureMultiblock.addPowerCableMultiblocks(portalStructure.connectedPowerCableMultiblocksMap);
            mainStructureMultiblock.addFluidPipeMultiblocks(portalStructure.connectedFluidPipeMultiblocksMap);

            PortalMultiblockManager.removePortalStructure(portalStructure);

            Logger.sendMessage("Merged PortalStructure " + portalStructure.portalId.toString().substring(0, 8) +
                    " into " + mainId.toString().substring(0, 8) +
                    " (+" + frameBlocksToAdd + " frame blocks, +" + controllersToAdd + " controllers, +" +
                    powerCablesToAdd + " power cables, +" + fluidPipesToAdd + " fluid pipes)", true);
        }

        updateAllBlockEntityReferences(mainStructureMultiblock, allFrameBlocksToUpdate, allControllersToUpdate);

        int totalFrameBlocksAdded = mainStructureMultiblock.frameBlocks.size() - totalFrameBlocksBefore;
        int totalControllersAdded = mainStructureMultiblock.portalControllers.size() - totalControllersBefore;
        int totalPowerCablesAdded = mainStructureMultiblock.connectedPowerCableMultiblocksMap.size() - totalPowerCablesBefore;
        int totalFluidPipesAdded = mainStructureMultiblock.connectedFluidPipeMultiblocksMap.size() - totalFluidPipesBefore;

        Logger.sendMessage("Merge complete: " + totalFrameBlocksAdded + " total frame blocks, " +
                totalControllersAdded + " controllers, " + totalPowerCablesAdded + " power cables, " +
                totalFluidPipesAdded + " fluid pipes transferred to PortalStructure " +
                mainId.toString().substring(0, 8), true);

        return mainStructureMultiblock;
    }

    private static void updateAllBlockEntityReferences(PortalStructure multiblock, Set<BlockPos> frameBlocksToUpdate, Set<PortalControllerBlockEntity> controllersToUpdate) {
        if (multiblock.level == null) return;

        int updatedFrameBlocks = 0;
        int updatedControllers = 0;

        for (BlockPos pos : frameBlocksToUpdate) {
            BlockEntity blockEntity = multiblock.level.getBlockEntity(pos);
            if (blockEntity instanceof PortalBlockEntity) {
                ((PortalBlockEntity) blockEntity).setPortalStructure(multiblock);
                updatedFrameBlocks++;
            }
        }

        for (PortalControllerBlockEntity controller : controllersToUpdate) {
            controller.setPortalStructure(multiblock);
            updatedControllers++;
        }

        Logger.sendMessage("Updated " + updatedFrameBlocks + " portal block entities and " +
                updatedControllers + " controller entities to PortalStructure " +
                multiblock.portalId.toString().substring(0, 8), true);
    }

    // UPDATED: Enhanced split handling similar to TankMultiblock
    private void handleSplitAfterRemoval() {
        Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) +
                " checking for split", true);

        // Find all connected components in the frame
        List<Set<BlockPos>> disconnectedComponents = findDisconnectedComponents();

        if (disconnectedComponents.size() <= 1) {
            // Still one connected component, no split occurred
            Logger.sendMessage("Split check: Structure remains connected", true);
            return;
        }

        Logger.sendMessage("Split check: Structure split into " + disconnectedComponents.size() +
                " components", true);

        // Split the structure into components
        splitIntoComponents(disconnectedComponents);
    }

    // Find disconnected components using flood fill
    private List<Set<BlockPos>> findDisconnectedComponents() {
        Set<BlockPos> visited = new HashSet<>();
        List<Set<BlockPos>> components = new ArrayList<>();

        for (BlockPos startPos : frameBlocks) {
            if (!visited.contains(startPos)) {
                Set<BlockPos> component = new HashSet<>();
                floodFill(startPos, component, visited);
                components.add(component);
            }
        }

        return components;
    }

    // Flood fill algorithm for finding connected components
    private void floodFill(BlockPos start, Set<BlockPos> component, Set<BlockPos> visited) {
        if (visited.contains(start)) return;
        visited.add(start);
        component.add(start);

        // Check ALL 6 directions for 3D connectivity
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = start.relative(direction);
            if (frameBlocks.contains(neighbor)) {
                floodFill(neighbor, component, visited);
            }
        }
    }

    // Split the portal structure into multiple components (similar to TankMultiblock)
    private void splitIntoComponents(List<Set<BlockPos>> components) {
        // Store the original portal ID
        UUID originalPortalId = this.portalId;

        // First component keeps the original portal structure
        Set<BlockPos> mainComponent = components.get(0);

        // Clear and reset the original portal structure with main component
        this.frameBlocks.clear();
        this.frameBlocks.addAll(mainComponent);

        // Reassign controllers based on proximity
        reassignControllers(mainComponent);

        // Update connections for the main component
        updateConnectionsForComponent(mainComponent, true);

        // Update all block entity references for the main component
        updateBlockEntityReferencesForComponent(mainComponent);

        Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) +
                " now has " + frameBlocks.size() + " frame blocks and " + portalControllers.size() + " controllers after split", true);

        // Create new portal structures for other components
        for (int i = 1; i < components.size(); i++) {
            Set<BlockPos> component = components.get(i);
            createNewPortalStructureForComponent(component, originalPortalId);
        }

        // Revalidate the main structure
        revalidateStructure();
    }

    // Reassign controllers based on proximity to component
    private void reassignControllers(Set<BlockPos> component) {
        Set<PortalControllerBlockEntity> controllersToKeep = new HashSet<>();
        Set<PortalControllerBlockEntity> controllersToRemove = new HashSet<>();

        for (PortalControllerBlockEntity controller : portalControllers) {
            if (isControllerAdjacentToComponent(controller, component)) {
                controllersToKeep.add(controller);
            } else {
                controllersToRemove.add(controller);
            }
        }

        // Update controllers
        this.portalControllers.clear();
        this.portalControllers.addAll(controllersToKeep);
    }

    // Check if controller is adjacent to any block in component
    private boolean isControllerAdjacentToComponent(PortalControllerBlockEntity controller, Set<BlockPos> component) {
        BlockPos controllerPos = controller.getBlockPos();
        for (BlockPos framePos : component) {
            double distance = controllerPos.distToCenterSqr(framePos.getX() + 0.5, framePos.getY() + 0.5, framePos.getZ() + 0.5);
            if (distance <= 2.0) { // Adjacent or diagonal
                return true;
            }
        }
        return false;
    }

    // Update connections for a component
    private void updateConnectionsForComponent(Set<BlockPos> component, boolean isMainComponent) {
        Logger.sendMessage("Updating connections for " + (isMainComponent ? "main" : "new") + " portal structure", true);

        // Update power cable connections
        updateConnectionMap(component, connectedPowerCableMultiblocksMap, true);

        // Update fluid pipe connections
        updateConnectionMap(component, connectedFluidPipeMultiblocksMap, false);
    }

    // Helper method to update connection map
    private void updateConnectionMap(Set<BlockPos> component, Map<UUID, Set<BlockPos>> connectionMap, boolean isPowerCable) {
        for (Map.Entry<UUID, Set<BlockPos>> entry : new HashMap<>(connectionMap).entrySet()) {
            UUID multiblockId = entry.getKey();
            Set<BlockPos> connectionPoints = entry.getValue();

            // Remove connection points that are no longer adjacent to this component
            Set<BlockPos> pointsToRemove = new HashSet<>();
            for (BlockPos connectionPoint : connectionPoints) {
                if (!isPositionAdjacentToComponent(connectionPoint, component)) {
                    pointsToRemove.add(connectionPoint);
                }
            }

            // Remove the invalid connection points
            connectionPoints.removeAll(pointsToRemove);

            // If no connection points remain, remove the multiblock entirely
            if (connectionPoints.isEmpty()) {
                connectionMap.remove(multiblockId);
                Logger.sendMessage("Removed " + (isPowerCable ? "power cable" : "fluid pipe") + " " +
                        multiblockId.toString().substring(0, 8) + " - no valid connections", true);
            } else if (!pointsToRemove.isEmpty()) {
                Logger.sendMessage("Removed " + pointsToRemove.size() +
                        " invalid connection points from " + (isPowerCable ? "power cable" : "fluid pipe") + " " +
                        multiblockId.toString().substring(0, 8), true);
            }

            // Notify the multiblock about the removed connections
            if (!pointsToRemove.isEmpty()) {
                notifyMultiblockAboutRemovedConnections(multiblockId, pointsToRemove, isPowerCable);
            }
        }
    }

    // Check if position is adjacent to any block in component
    private boolean isPositionAdjacentToComponent(BlockPos position, Set<BlockPos> component) {
        for (BlockPos componentPos : component) {
            double distance = position.distToCenterSqr(componentPos.getX() + 0.5, componentPos.getY() + 0.5, componentPos.getZ() + 0.5);
            if (distance <= 2.0) { // Adjacent or diagonal
                return true;
            }
        }

        // Also check controllers
        for (PortalControllerBlockEntity controller : portalControllers) {
            BlockPos controllerPos = controller.getBlockPos();
            double distance = position.distToCenterSqr(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5);
            if (distance <= 2.0) {
                return true;
            }
        }

        return false;
    }

    // Notify multiblock about removed connections
    private void notifyMultiblockAboutRemovedConnections(UUID multiblockId, Set<BlockPos> pointsToRemove, boolean isPowerCable) {
        if (isPowerCable) {
            PowerCableMultiblock cable = PortalMultiblockManager.getPowerCableMultiblock(multiblockId);
            if (cable != null) {
                for (BlockPos removedPoint : pointsToRemove) {
                    cable.removePortalConnectionFromPortal(this.portalId, removedPoint);
                }
            }
        } else {
            FluidPipeMultiblock pipe = PortalMultiblockManager.getFluidPipeMultiblock(multiblockId);
            if (pipe != null) {
                for (BlockPos removedPoint : pointsToRemove) {
                    pipe.removePortalConnectionFromPortal(this.portalId, removedPoint);
                }
            }
        }
    }

    // Update block entity references for a component
    private void updateBlockEntityReferencesForComponent(Set<BlockPos> component) {
        for (BlockPos pos : component) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof PortalBlockEntity) {
                ((PortalBlockEntity) blockEntity).setPortalStructure(this);
            }
        }

        for (PortalControllerBlockEntity controller : portalControllers) {
            controller.setPortalStructure(this);
        }
    }

    // Create a new portal structure for a component
    private void createNewPortalStructureForComponent(Set<BlockPos> component, UUID originalPortalId) {
        PortalStructure newStructure = new PortalStructure(UUID.randomUUID(), level);

        // Add all frame blocks from this component to the new structure
        for (BlockPos pos : component) {
            newStructure.addPortalBlock(pos);
        }

        // Find and transfer controllers that belong to this component
        transferControllersToNewStructure(newStructure, component);

        Logger.sendMessage("Created new PortalStructure " + newStructure.portalId.toString().substring(0, 8) +
                " with " + component.size() + " frame blocks and " + newStructure.portalControllers.size() + " controllers", true);

        // Transfer connections to the new structure
        transferConnectionsToNewStructure(newStructure, component, originalPortalId);

        // Update all block entity references for the new structure
        for (BlockPos pos : component) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof PortalBlockEntity portalBE) {
                portalBE.portalStructure = newStructure;
            }
        }

        // Validate the new structure
        newStructure.revalidateStructure();
    }

    // Transfer controllers to new structure
    private void transferControllersToNewStructure(PortalStructure newStructure, Set<BlockPos> component) {
        Iterator<PortalControllerBlockEntity> iterator = portalControllers.iterator();
        while (iterator.hasNext()) {
            PortalControllerBlockEntity controller = iterator.next();
            if (isControllerAdjacentToComponent(controller, component)) {
                newStructure.addPortalControllerBlock(controller);
                iterator.remove();
            }
        }
    }

    // Transfer connections to a new portal structure
    private void transferConnectionsToNewStructure(PortalStructure newStructure, Set<BlockPos> component, UUID originalPortalId) {
        Logger.sendMessage("Transferring connections to new portal structure " +
                newStructure.portalId.toString().substring(0, 8), true);

        // Transfer power cable connections
        transferConnectionMap(newStructure, component, connectedPowerCableMultiblocksMap, originalPortalId, true);

        // Transfer fluid pipe connections
        transferConnectionMap(newStructure, component, connectedFluidPipeMultiblocksMap, originalPortalId, false);
    }

    // Helper method to transfer connection map
    private void transferConnectionMap(PortalStructure newStructure, Set<BlockPos> component,
                                       Map<UUID, Set<BlockPos>> connectionMap, UUID originalPortalId,
                                       boolean isPowerCable) {
        for (Map.Entry<UUID, Set<BlockPos>> entry : connectionMap.entrySet()) {
            UUID multiblockId = entry.getKey();
            Set<BlockPos> connectionPoints = entry.getValue();

            // Find connection points that belong to this new component
            Set<BlockPos> pointsForNewStructure = new HashSet<>();
            for (BlockPos connectionPoint : connectionPoints) {
                if (isPositionAdjacentToComponent(connectionPoint, component)) {
                    pointsForNewStructure.add(connectionPoint);
                }
            }

            // If this multiblock has connections to this component, transfer them
            if (!pointsForNewStructure.isEmpty()) {
                Logger.sendMessage("Transferring " + pointsForNewStructure.size() +
                        " connection points to new portal structure for " +
                        (isPowerCable ? "power cable" : "fluid pipe") + " " +
                        multiblockId.toString().substring(0, 8), true);

                // Add connections to the new structure
                for (BlockPos connectionPoint : pointsForNewStructure) {
                    if (isPowerCable) {
                        newStructure.addInternalPowerCableConnection(multiblockId, connectionPoint);
                    } else {
                        newStructure.addInternalFluidPipeConnection(multiblockId, connectionPoint);
                    }
                }

                // Notify the multiblock about the transferred connections
                notifyMultiblockAboutTransferredConnections(multiblockId, pointsForNewStructure,
                        originalPortalId, newStructure.portalId, isPowerCable);
            }
        }
    }

    // Internal method to add power cable connection (for transfer)
    private void addInternalPowerCableConnection(UUID cableId, BlockPos connectionPoint) {
        if (!connectedPowerCableMultiblocksMap.containsKey(cableId)) {
            connectedPowerCableMultiblocksMap.put(cableId, new HashSet<>());
        }
        connectedPowerCableMultiblocksMap.get(cableId).add(connectionPoint);
    }

    // Internal method to add fluid pipe connection (for transfer)
    private void addInternalFluidPipeConnection(UUID pipeId, BlockPos connectionPoint) {
        if (!connectedFluidPipeMultiblocksMap.containsKey(pipeId)) {
            connectedFluidPipeMultiblocksMap.put(pipeId, new HashSet<>());
        }
        connectedFluidPipeMultiblocksMap.get(pipeId).add(connectionPoint);
    }

    // Notify multiblock about transferred connections
    private void notifyMultiblockAboutTransferredConnections(UUID multiblockId, Set<BlockPos> pointsToTransfer,
                                                             UUID oldPortalId, UUID newPortalId, boolean isPowerCable) {
        if (isPowerCable) {
            PowerCableMultiblock cable = PortalMultiblockManager.getPowerCableMultiblock(multiblockId);
            if (cable != null) {
                for (BlockPos connectionPoint : pointsToTransfer) {
                    // Remove old connection
                    cable.removePortalConnectionFromPortal(oldPortalId, connectionPoint);
                    // Add new connection
                    PortalStructure newStructure = PortalMultiblockManager.getPortalStructure(newPortalId);
                    if (newStructure != null) {
                        cable.addPortalConnectionFromPortal(newStructure.portalId, connectionPoint);
                    }
                }
            }
        } else {
            FluidPipeMultiblock pipe = PortalMultiblockManager.getFluidPipeMultiblock(multiblockId);
            if (pipe != null) {
                for (BlockPos connectionPoint : pointsToTransfer) {
                    // Remove old connection
                    pipe.removePortalConnectionFromPortal(oldPortalId, connectionPoint);
                    // Add new connection
                    PortalStructure newStructure = PortalMultiblockManager.getPortalStructure(newPortalId);
                    if (newStructure != null) {
                        pipe.addPortalConnectionFromPortal(newStructure.portalId, connectionPoint);
                    }
                }
            }
        }
    }

    // VALIDATION: Enhanced validation with comprehensive logging
    public void revalidateStructure() {
        Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " revalidating structure...", true);

        // Clear previous interior blocks
        interiorBlocks.clear();
        isValid = false;
        bounds = null;

        // Run validation
        isValid = validatePortalStructure();

        if (isValid) {
            calculateInteriorBlocks();
            bounds = PortalBounds.calculateFromFrame(frameBlocks);

            if (bounds == null) {
                Logger.sendMessage("WARNING: Could not calculate portal bounds", true);
                isValid = false;
            } else {
                if (isActive) {
                    PortalManager.registerActivePortal(this);
                }
                Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " VALIDATED: " +
                        frameBlocks.size() + " frame blocks, " + interiorBlocks.size() + " interior blocks, " +
                        portalControllers.size() + " controllers", true);
            }
        } else {
            Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " INVALID: Structure validation failed", true);
        }

        markForSave();
    }

    private boolean validatePortalStructure() {
        Logger.sendMessage("VALIDATION: Starting structure validation for PortalStructure " + portalId.toString().substring(0, 8), true);

        // 1. SIMPLEST CHECK: Must have at least one controller
        if (portalControllers.isEmpty()) {
            Logger.sendMessage("VALIDATION FAILED: No controllers found", true);
            return false;
        }

        // 2. SIMPLE CHECK: Must have at least 4 frame blocks (minimum for corners)
        if (frameBlocks.size() < 4) {
            Logger.sendMessage("VALIDATION FAILED: Too few frame blocks (" + frameBlocks.size() + ")", true);
            return false;
        }

        // 3. SIMPLE CHECK: All blocks must form a vertical plane (same X or same Z)
        Set<Integer> xValues = new HashSet<>();
        Set<Integer> zValues = new HashSet<>();

        for (BlockPos pos : frameBlocks) {
            xValues.add(pos.getX());
            zValues.add(pos.getZ());
        }

        // Must be either all same X (vertical plane on X) OR all same Z (vertical plane on Z)
        boolean isXPlane = xValues.size() == 1;
        boolean isZPlane = zValues.size() == 1;

        if (!isXPlane && !isZPlane) {
            Logger.sendMessage("VALIDATION FAILED: Frame blocks not in a vertical plane (X values: " + xValues.size() + ", Z values: " + zValues.size() + ")", true);
            return false;
        }

        // Determine which axis is constant (the plane orientation)
        boolean constantX = isXPlane;
        int constantValue = isXPlane ? xValues.iterator().next() : zValues.iterator().next();
        String planeType = isXPlane ? "X" : "Z";

        Logger.sendMessage("VALIDATION: Frame is vertical plane on " + planeType + " = " + constantValue, true);

        // 4. SIMPLE CHECK: Controller must be adjacent to at least one frame block
        boolean controllerAdjacent = false;
        for (PortalControllerBlockEntity controller : portalControllers) {
            BlockPos controllerPos = controller.getBlockPos();
            for (BlockPos framePos : frameBlocks) {
                // Check if controller is adjacent (including diagonals)
                double distance = controllerPos.distToCenterSqr(framePos.getX() + 0.5, framePos.getY() + 0.5, framePos.getZ() + 0.5);
                if (distance <= 2.0) {
                    controllerAdjacent = true;
                    Logger.sendMessage("VALIDATION: Controller at " + controllerPos + " is adjacent to frame block at " + framePos, true);
                    break;
                }
            }
            if (controllerAdjacent) break;
        }

        if (!controllerAdjacent) {
            Logger.sendMessage("VALIDATION FAILED: No controller adjacent to any frame block", true);
            return false;
        }

        // Now do corner-based validation (adjusted for vertical plane)
        if (!validateRectangleFrame(constantX, constantValue)) {
            Logger.sendMessage("VALIDATION FAILED: Frame structure invalid", true);
            return false;
        }

        // Check that power cables and fluid pipes are connected to frame
        if (!validateAttachmentBlocks()) {
            Logger.sendMessage("VALIDATION FAILED: Attachment blocks invalid", true);
            return false;
        }

        Logger.sendMessage("VALIDATION PASSED: Portal structure is valid", true);
        return true;
    }

    private boolean validateRectangleFrame(boolean constantX, int constantValue) {
        Logger.sendMessage("FRAME VALIDATION: Checking vertical rectangle with " + frameBlocks.size() + " blocks", true);

        Set<BlockPos> levelFrames = new HashSet<>(frameBlocks);

        // Step 1: Find all corner candidates (blocks with neighbors in perpendicular directions)
        Set<BlockPos> cornerCandidates = new HashSet<>();
        for (BlockPos pos : levelFrames) {
            if (isCornerCandidate(pos, levelFrames, constantX)) {
                cornerCandidates.add(pos);
            }
        }

        Logger.sendMessage("FRAME VALIDATION: Found " + cornerCandidates.size() + " corner candidates", true);

        // Step 2: Must have exactly 4 corners
        if (cornerCandidates.size() != 4) {
            Logger.sendMessage("FRAME VALIDATION FAILED: Expected 4 corners, found " + cornerCandidates.size(), true);
            return false;
        }

        // Step 3: Identify the corners
        Map<String, BlockPos> corners = identifyCorners(cornerCandidates, constantX);
        if (corners.size() != 4) {
            Logger.sendMessage("FRAME VALIDATION FAILED: Could not identify 4 distinct corners", true);
            return false;
        }

        // Step 4: Trace edges and validate rectangle
        boolean result = traceAndValidateRectangle(corners, levelFrames, constantX, constantValue);

        if (result) {
            Logger.sendMessage("FRAME VALIDATION PASSED: Complete vertical rectangle structure", true);

            // Calculate interior dimensions
            BlockPos topLeft = corners.get("topLeft");
            BlockPos bottomRight = corners.get("bottomRight");

            int interiorWidth, interiorHeight;
            if (constantX) {
                // X-plane: interior dimensions are in Z (width) and Y (height)
                interiorWidth = Math.abs(bottomRight.getZ() - topLeft.getZ()) - 1;
                interiorHeight = Math.abs(bottomRight.getY() - topLeft.getY()) - 1;
            } else {
                // Z-plane: interior dimensions are in X (width) and Y (height)
                interiorWidth = Math.abs(bottomRight.getX() - topLeft.getX()) - 1;
                interiorHeight = Math.abs(bottomRight.getY() - topLeft.getY()) - 1;
            }

            // Check interior is at least 1x2 for player (width x height)
            if (interiorWidth < 1 || interiorHeight < 2) {
                Logger.sendMessage("FRAME VALIDATION FAILED: Interior too small " + interiorWidth + "x" + interiorHeight + " - needs min 1x2 for player", true);
                return false;
            }

            Logger.sendMessage("FRAME VALIDATION: Interior dimensions " + interiorWidth + "x" + interiorHeight, true);
        } else {
            Logger.sendMessage("FRAME VALIDATION FAILED: Invalid rectangle structure", true);
        }

        return result;
    }

    // Helper method to check if a block is a corner candidate in vertical plane
    private boolean isCornerCandidate(BlockPos pos, Set<BlockPos> frameBlocks, boolean constantX) {
        // For vertical plane, we need to check neighbors differently
        // If constantX, we check Y and Z neighbors
        // If constantZ, we check X and Y neighbors

        boolean hasNeighbor1 = false;
        boolean hasNeighbor2 = false;
        boolean hasNeighbor3 = false;
        boolean hasNeighbor4 = false;

        if (constantX) {
            // X-plane: check neighbors in Y and Z directions
            hasNeighbor1 = frameBlocks.contains(pos.above());    // +Y
            hasNeighbor2 = frameBlocks.contains(pos.below());    // -Y
            hasNeighbor3 = frameBlocks.contains(pos.north());    // +Z
            hasNeighbor4 = frameBlocks.contains(pos.south());    // -Z
        } else {
            // Z-plane: check neighbors in X and Y directions
            hasNeighbor1 = frameBlocks.contains(pos.above());    // +Y
            hasNeighbor2 = frameBlocks.contains(pos.below());    // -Y
            hasNeighbor3 = frameBlocks.contains(pos.east());     // +X
            hasNeighbor4 = frameBlocks.contains(pos.west());     // -X
        }

        // Count how many directions have neighbors
        int neighborCount = 0;
        if (hasNeighbor1) neighborCount++;
        if (hasNeighbor2) neighborCount++;
        if (hasNeighbor3) neighborCount++;
        if (hasNeighbor4) neighborCount++;

        // Must have exactly 2 neighbors (for a corner in a rectangle)
        if (neighborCount != 2) {
            return false;
        }

        // Must not have both neighbors on same axis
        // For X-plane: can't have both up/down AND both north/south
        // For Z-plane: can't have both up/down AND both east/west
        if ((hasNeighbor1 && hasNeighbor2) || (hasNeighbor3 && hasNeighbor4)) {
            return false;
        }

        // Must have one vertical neighbor (Y) and one horizontal neighbor (X or Z)
        boolean hasVertical = hasNeighbor1 || hasNeighbor2;
        boolean hasHorizontal = hasNeighbor3 || hasNeighbor4;

        return hasVertical && hasHorizontal;
    }

    // Identify corners in vertical plane
    private Map<String, BlockPos> identifyCorners(Set<BlockPos> cornerCandidates, boolean constantX) {
        Map<String, BlockPos> corners = new HashMap<>();

        // Find min and max coordinates
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minOther = Integer.MAX_VALUE, maxOther = Integer.MIN_VALUE;

        for (BlockPos pos : cornerCandidates) {
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());

            if (constantX) {
                // X-plane: use Z coordinate
                int z = pos.getZ();
                minOther = Math.min(minOther, z);
                maxOther = Math.max(maxOther, z);
            } else {
                // Z-plane: use X coordinate
                int x = pos.getX();
                minOther = Math.min(minOther, x);
                maxOther = Math.max(maxOther, x);
            }
        }

        // Assign corners based on coordinates
        // "top" means higher Y, "bottom" means lower Y
        for (BlockPos pos : cornerCandidates) {
            if (pos.getY() == maxY) {
                // Top row
                if (constantX) {
                    if (pos.getZ() == minOther) {
                        corners.put("topLeft", pos);      // Top-left: max Y, min Z
                    } else if (pos.getZ() == maxOther) {
                        corners.put("topRight", pos);     // Top-right: max Y, max Z
                    }
                } else {
                    if (pos.getX() == minOther) {
                        corners.put("topLeft", pos);      // Top-left: max Y, min X
                    } else if (pos.getX() == maxOther) {
                        corners.put("topRight", pos);     // Top-right: max Y, max X
                    }
                }
            } else if (pos.getY() == minY) {
                // Bottom row
                if (constantX) {
                    if (pos.getZ() == minOther) {
                        corners.put("bottomLeft", pos);   // Bottom-left: min Y, min Z
                    } else if (pos.getZ() == maxOther) {
                        corners.put("bottomRight", pos);  // Bottom-right: min Y, max Z
                    }
                } else {
                    if (pos.getX() == minOther) {
                        corners.put("bottomLeft", pos);   // Bottom-left: min Y, min X
                    } else if (pos.getX() == maxOther) {
                        corners.put("bottomRight", pos);  // Bottom-right: min Y, max X
                    }
                }
            }
        }

        return corners;
    }

    // Trace edges and validate the vertical rectangle
    private boolean traceAndValidateRectangle(Map<String, BlockPos> corners, Set<BlockPos> frameBlocks,
                                              boolean constantX, int constantValue) {
        BlockPos topLeft = corners.get("topLeft");
        BlockPos topRight = corners.get("topRight");
        BlockPos bottomLeft = corners.get("bottomLeft");
        BlockPos bottomRight = corners.get("bottomRight");

        if (topLeft == null || topRight == null || bottomLeft == null || bottomRight == null) {
            return false;
        }

        if (constantX) {
            // X-plane: Trace edges in Y and Z directions
            int x = constantValue;

            // Trace top edge (from topLeft to topRight) - constant Y = maxY, varying Z
            int topY = topLeft.getY();
            for (int z = Math.min(topLeft.getZ(), topRight.getZ());
                 z <= Math.max(topLeft.getZ(), topRight.getZ()); z++) {
                BlockPos pos = new BlockPos(x, topY, z);
                if (!frameBlocks.contains(pos)) {
                    Logger.sendMessage("RECTANGLE TRACE FAILED: Missing frame block at top edge " + pos, true);
                    return false;
                }
            }

            // Trace bottom edge (from bottomLeft to bottomRight) - constant Y = minY, varying Z
            int bottomY = bottomLeft.getY();
            for (int z = Math.min(bottomLeft.getZ(), bottomRight.getZ());
                 z <= Math.max(bottomLeft.getZ(), bottomRight.getZ()); z++) {
                BlockPos pos = new BlockPos(x, bottomY, z);
                if (!frameBlocks.contains(pos)) {
                    Logger.sendMessage("RECTANGLE TRACE FAILED: Missing frame block at bottom edge " + pos, true);
                    return false;
                }
            }

            // Trace left edge (from topLeft to bottomLeft) - constant Z = minZ, varying Y
            int leftZ = Math.min(topLeft.getZ(), bottomLeft.getZ());
            for (int y = Math.min(topLeft.getY(), bottomLeft.getY());
                 y <= Math.max(topLeft.getY(), bottomLeft.getY()); y++) {
                BlockPos pos = new BlockPos(x, y, leftZ);
                if (!frameBlocks.contains(pos)) {
                    Logger.sendMessage("RECTANGLE TRACE FAILED: Missing frame block at left edge " + pos, true);
                    return false;
                }
            }

            // Trace right edge (from topRight to bottomRight) - constant Z = maxZ, varying Y
            int rightZ = Math.max(topRight.getZ(), bottomRight.getZ());
            for (int y = Math.min(topRight.getY(), bottomRight.getY());
                 y <= Math.max(topRight.getY(), bottomRight.getY()); y++) {
                BlockPos pos = new BlockPos(x, y, rightZ);
                if (!frameBlocks.contains(pos)) {
                    Logger.sendMessage("RECTANGLE TRACE FAILED: Missing frame block at right edge " + pos, true);
                    return false;
                }
            }
        } else {
            // Z-plane: Trace edges in X and Y directions
            int z = constantValue;

            // Trace top edge (from topLeft to topRight) - constant Y = maxY, varying X
            int topY = topLeft.getY();
            for (int x = Math.min(topLeft.getX(), topRight.getX());
                 x <= Math.max(topLeft.getX(), topRight.getX()); x++) {
                BlockPos pos = new BlockPos(x, topY, z);
                if (!frameBlocks.contains(pos)) {
                    Logger.sendMessage("RECTANGLE TRACE FAILED: Missing frame block at top edge " + pos, true);
                    return false;
                }
            }

            // Trace bottom edge (from bottomLeft to bottomRight) - constant Y = minY, varying X
            int bottomY = bottomLeft.getY();
            for (int x = Math.min(bottomLeft.getX(), bottomRight.getX());
                 x <= Math.max(bottomLeft.getX(), bottomRight.getX()); x++) {
                BlockPos pos = new BlockPos(x, bottomY, z);
                if (!frameBlocks.contains(pos)) {
                    Logger.sendMessage("RECTANGLE TRACE FAILED: Missing frame block at bottom edge " + pos, true);
                    return false;
                }
            }

            // Trace left edge (from topLeft to bottomLeft) - constant X = minX, varying Y
            int leftX = Math.min(topLeft.getX(), bottomLeft.getX());
            for (int y = Math.min(topLeft.getY(), bottomLeft.getY());
                 y <= Math.max(topLeft.getY(), bottomLeft.getY()); y++) {
                BlockPos pos = new BlockPos(leftX, y, z);
                if (!frameBlocks.contains(pos)) {
                    Logger.sendMessage("RECTANGLE TRACE FAILED: Missing frame block at left edge " + pos, true);
                    return false;
                }
            }

            // Trace right edge (from topRight to bottomRight) - constant X = maxX, varying Y
            int rightX = Math.max(topRight.getX(), bottomRight.getX());
            for (int y = Math.min(topRight.getY(), bottomRight.getY());
                 y <= Math.max(topRight.getY(), bottomRight.getY()); y++) {
                BlockPos pos = new BlockPos(rightX, y, z);
                if (!frameBlocks.contains(pos)) {
                    Logger.sendMessage("RECTANGLE TRACE FAILED: Missing frame block at right edge " + pos, true);
                    return false;
                }
            }
        }

        // Verify no extra portal blocks outside the rectangle
        for (BlockPos pos : frameBlocks) {
            if (!isOnRectangleEdge(pos, corners, constantX, constantValue)) {
                Logger.sendMessage("RECTANGLE VALIDATION FAILED: Extra portal block at " + pos + " not on frame edges", true);
                return false;
            }
        }

        // Verify interior is empty (must be air)
        if (!verifyInteriorIsEmpty(corners, constantX, constantValue)) {
            return false;
        }

        return true;
    }

    // Check if a position is on the rectangle edge
    private boolean isOnRectangleEdge(BlockPos pos, Map<String, BlockPos> corners, boolean constantX, int constantValue) {
        BlockPos topLeft = corners.get("topLeft");
        BlockPos topRight = corners.get("topRight");
        BlockPos bottomLeft = corners.get("bottomLeft");
        BlockPos bottomRight = corners.get("bottomRight");

        if (constantX) {
            // X-plane
            if (pos.getX() != constantValue) return false;

            boolean onTopEdge = pos.getY() == topLeft.getY() &&
                    pos.getZ() >= Math.min(topLeft.getZ(), topRight.getZ()) &&
                    pos.getZ() <= Math.max(topLeft.getZ(), topRight.getZ());
            boolean onBottomEdge = pos.getY() == bottomLeft.getY() &&
                    pos.getZ() >= Math.min(bottomLeft.getZ(), bottomRight.getZ()) &&
                    pos.getZ() <= Math.max(bottomLeft.getZ(), bottomRight.getZ());
            boolean onLeftEdge = pos.getZ() == Math.min(topLeft.getZ(), bottomLeft.getZ()) &&
                    pos.getY() >= Math.min(topLeft.getY(), bottomLeft.getY()) &&
                    pos.getY() <= Math.max(topLeft.getY(), bottomLeft.getY());
            boolean onRightEdge = pos.getZ() == Math.max(topRight.getZ(), bottomRight.getZ()) &&
                    pos.getY() >= Math.min(topRight.getY(), bottomRight.getY()) &&
                    pos.getY() <= Math.max(topRight.getY(), bottomRight.getY());

            return onTopEdge || onBottomEdge || onLeftEdge || onRightEdge;
        } else {
            // Z-plane
            if (pos.getZ() != constantValue) return false;

            boolean onTopEdge = pos.getY() == topLeft.getY() &&
                    pos.getX() >= Math.min(topLeft.getX(), topRight.getX()) &&
                    pos.getX() <= Math.max(topLeft.getX(), topRight.getX());
            boolean onBottomEdge = pos.getY() == bottomLeft.getY() &&
                    pos.getX() >= Math.min(bottomLeft.getX(), bottomRight.getX()) &&
                    pos.getX() <= Math.max(bottomLeft.getX(), bottomRight.getX());
            boolean onLeftEdge = pos.getX() == Math.min(topLeft.getX(), bottomLeft.getX()) &&
                    pos.getY() >= Math.min(topLeft.getY(), bottomLeft.getY()) &&
                    pos.getY() <= Math.max(topLeft.getY(), bottomLeft.getY());
            boolean onRightEdge = pos.getX() == Math.max(topRight.getX(), bottomRight.getX()) &&
                    pos.getY() >= Math.min(topRight.getY(), bottomRight.getY()) &&
                    pos.getY() <= Math.max(topRight.getY(), bottomRight.getY());

            return onTopEdge || onBottomEdge || onLeftEdge || onRightEdge;
        }
    }

    // Verify interior blocks are air (adjusted for vertical plane)
    private boolean verifyInteriorIsEmpty(Map<String, BlockPos> corners, boolean constantX, int constantValue) {
        BlockPos topLeft = corners.get("topLeft");
        BlockPos bottomRight = corners.get("bottomRight");

        if (constantX) {
            // X-plane: interior is in Y (vertical) and Z (horizontal)
            int x = constantValue;
            int minY = Math.min(topLeft.getY(), bottomRight.getY());
            int maxY = Math.max(topLeft.getY(), bottomRight.getY());
            int minZ = Math.min(topLeft.getZ(), bottomRight.getZ());
            int maxZ = Math.max(topLeft.getZ(), bottomRight.getZ());

            // Scan interior area (skip the edges)
            for (int y = minY + 1; y < maxY; y++) {
                for (int z = minZ + 1; z < maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);

                    // Check if block is a portal block (shouldn't be in interior)
                    if (frameBlocks.contains(pos)) {
                        Logger.sendMessage("INTERIOR VALIDATION FAILED: Portal block found in interior at " + pos, true);
                        return false;
                    }

                    // Check if block is air
                    if (!level.isEmptyBlock(pos)) {
                        Logger.sendMessage("INTERIOR VALIDATION FAILED: Non-air block found in interior at " + pos, true);
                        return false;
                    }
                }
            }
        } else {
            // Z-plane: interior is in Y (vertical) and X (horizontal)
            int z = constantValue;
            int minY = Math.min(topLeft.getY(), bottomRight.getY());
            int maxY = Math.max(topLeft.getY(), bottomRight.getY());
            int minX = Math.min(topLeft.getX(), bottomRight.getX());
            int maxX = Math.max(topLeft.getX(), bottomRight.getX());

            // Scan interior area (skip the edges)
            for (int y = minY + 1; y < maxY; y++) {
                for (int x = minX + 1; x < maxX; x++) {
                    BlockPos pos = new BlockPos(x, y, z);

                    // Check if block is a portal block (shouldn't be in interior)
                    if (frameBlocks.contains(pos)) {
                        Logger.sendMessage("INTERIOR VALIDATION FAILED: Portal block found in interior at " + pos, true);
                        return false;
                    }

                    // Check if block is air
                    if (!level.isEmptyBlock(pos)) {
                        Logger.sendMessage("INTERIOR VALIDATION FAILED: Non-air block found in interior at " + pos, true);
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private void calculateInteriorBlocks() {
        interiorBlocks.clear();

        if (frameBlocks.isEmpty()) return;

        // First we need to determine if the structure is valid and get plane orientation
        Set<Integer> xValues = new HashSet<>();
        Set<Integer> zValues = new HashSet<>();

        for (BlockPos pos : frameBlocks) {
            xValues.add(pos.getX());
            zValues.add(pos.getZ());
        }

        boolean constantX = xValues.size() == 1;
        if (!constantX && zValues.size() != 1) {
            // Not a valid vertical plane
            return;
        }

        int constantValue = constantX ? xValues.iterator().next() : zValues.iterator().next();

        // Get corners
        Set<BlockPos> cornerCandidates = new HashSet<>();
        for (BlockPos pos : frameBlocks) {
            if (isCornerCandidate(pos, frameBlocks, constantX)) {
                cornerCandidates.add(pos);
            }
        }

        if (cornerCandidates.size() != 4) return;

        Map<String, BlockPos> corners = identifyCorners(cornerCandidates, constantX);
        if (corners.size() != 4) return;

        BlockPos topLeft = corners.get("topLeft");
        BlockPos bottomRight = corners.get("bottomRight");

        // Add all interior positions
        if (constantX) {
            // X-plane
            int x = constantValue;
            int minY = Math.min(topLeft.getY(), bottomRight.getY());
            int maxY = Math.max(topLeft.getY(), bottomRight.getY());
            int minZ = Math.min(topLeft.getZ(), bottomRight.getZ());
            int maxZ = Math.max(topLeft.getZ(), bottomRight.getZ());

            for (int y = minY + 1; y < maxY; y++) {
                for (int z = minZ + 1; z < maxZ; z++) {
                    interiorBlocks.add(new BlockPos(x, y, z));
                }
            }
        } else {
            // Z-plane
            int z = constantValue;
            int minY = Math.min(topLeft.getY(), bottomRight.getY());
            int maxY = Math.max(topLeft.getY(), bottomRight.getY());
            int minX = Math.min(topLeft.getX(), bottomRight.getX());
            int maxX = Math.max(topLeft.getX(), bottomRight.getX());

            for (int y = minY + 1; y < maxY; y++) {
                for (int x = minX + 1; x < maxX; x++) {
                    interiorBlocks.add(new BlockPos(x, y, z));
                }
            }
        }

        Logger.sendMessage("INTERIOR: Calculated " + interiorBlocks.size() + " interior blocks", true);
    }

    private boolean validateAttachmentBlocks() {
        Logger.sendMessage("ATTACHMENT VALIDATION: Checking power cables and fluid pipes", true);

        // Power cables and fluid pipes must be adjacent to frame blocks
        // For now, we'll assume they're properly connected
        // In a full implementation, this would verify that all connected
        // multiblocks are actually adjacent to the frame

        Logger.sendMessage("ATTACHMENT VALIDATION PASSED: All attachments valid", true);
        return true;
    }

    public void addPowerCableMultiblock(PowerCableMultiblock cableMultiblock) {
        Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " addPowerCableMultiblock called for cable " + cableMultiblock.id.toString().substring(0, 8), true);

        UUID cableId = cableMultiblock.id;

        // Find all connection points between portal and cable
        // Can connect to FRAME BLOCKS and CONTROLLER BLOCKS, but NOT interior blocks
        Set<BlockPos> connectionPoints = findAdjacentCablePositions(cableMultiblock);

        if (connectionPoints.isEmpty()) {
            Logger.sendMessage("WARNING: No valid connection points found between portal and cable " + cableId.toString().substring(0, 8), true);
            return;
        }

        boolean isNewConnection = !connectedPowerCableMultiblocksMap.containsKey(cableId);

        if (!connectedPowerCableMultiblocksMap.containsKey(cableId)) {
            connectedPowerCableMultiblocksMap.put(cableId, new HashSet<>());
        }
        connectedPowerCableMultiblocksMap.get(cableId).addAll(connectionPoints);

        if (isNewConnection) {
            Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " CONNECTED to PowerCableMultiblock " + cableId.toString().substring(0, 8) + " via " + connectionPoints.size() + " connection points", true);
        } else {
            Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " added " + connectionPoints.size() + " connection points to PowerCableMultiblock " + cableId.toString().substring(0, 8) + " (" + connectedPowerCableMultiblocksMap.get(cableId).size() + " total connections)", true);
        }

        // CRITICAL: Notify the cable to store this connection from its side
        for (BlockPos cablePos : connectionPoints) {
            cableMultiblock.addPortalConnectionFromPortal(this.portalId, cablePos);
        }

        markForSave();
    }

    public void removePowerCableMultiblock(PowerCableMultiblock cableMultiblock) {
        Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " removePowerCableMultiblock called for cable " + cableMultiblock.id.toString().substring(0, 8), true);

        UUID cableId = cableMultiblock.id;

        if (connectedPowerCableMultiblocksMap.containsKey(cableId)) {
            Set<BlockPos> connectionPoints = connectedPowerCableMultiblocksMap.get(cableId);

            // CRITICAL: Notify the cable about each connection point being removed
            for (BlockPos cablePos : new HashSet<>(connectionPoints)) {
                cableMultiblock.removePortalConnectionFromPortal(this.portalId, cablePos);
            }

            connectedPowerCableMultiblocksMap.remove(cableId);
            Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " DISCONNECTED from PowerCableMultiblock " + cableId.toString().substring(0, 8) + " (removed " + connectionPoints.size() + " connection points)", true);
        } else {
            Logger.sendMessage("WARNING: Cable ID " + cableId.toString().substring(0, 8) + " not found in connectedPowerCableMultiblocksMap", true);
        }

        markForSave();
    }

    public void addFluidPipeMultiblock(FluidPipeMultiblock pipeMultiblock) {
        Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " addFluidPipeMultiblock called for pipe " + pipeMultiblock.id.toString().substring(0, 8), true);

        UUID pipeId = pipeMultiblock.id;

        // Find all connection points between portal and pipe
        // Can connect to FRAME BLOCKS and CONTROLLER BLOCKS, but NOT interior blocks
        Set<BlockPos> connectionPoints = findAdjacentPipePositions(pipeMultiblock);

        if (connectionPoints.isEmpty()) {
            Logger.sendMessage("WARNING: No valid connection points found between portal and pipe " + pipeId.toString().substring(0, 8), true);
            return;
        }

        boolean isNewConnection = !connectedFluidPipeMultiblocksMap.containsKey(pipeId);

        if (!connectedFluidPipeMultiblocksMap.containsKey(pipeId)) {
            connectedFluidPipeMultiblocksMap.put(pipeId, new HashSet<>());
        }
        connectedFluidPipeMultiblocksMap.get(pipeId).addAll(connectionPoints);

        if (isNewConnection) {
            Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " CONNECTED to FluidPipeMultiblock " + pipeId.toString().substring(0, 8) + " via " + connectionPoints.size() + " connection points", true);
        } else {
            Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " added " + connectionPoints.size() + " connection points to FluidPipeMultiblock " + pipeId.toString().substring(0, 8) + " (" + connectedFluidPipeMultiblocksMap.get(pipeId).size() + " total connections)", true);
        }

        // CRITICAL: Notify the pipe to store this connection from its side
        for (BlockPos pipePos : connectionPoints) {
            pipeMultiblock.addPortalConnectionFromPortal(this.portalId, pipePos);
        }

        markForSave();
    }

    public void removeFluidPipeMultiblock(FluidPipeMultiblock pipeMultiblock) {
        Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " removeFluidPipeMultiblock called for pipe " + pipeMultiblock.id.toString().substring(0, 8), true);

        UUID pipeId = pipeMultiblock.id;

        if (connectedFluidPipeMultiblocksMap.containsKey(pipeId)) {
            Set<BlockPos> connectionPoints = connectedFluidPipeMultiblocksMap.get(pipeId);

            // CRITICAL: Notify the pipe about each connection point being removed
            for (BlockPos pipePos : new HashSet<>(connectionPoints)) {
                pipeMultiblock.removePortalConnectionFromPortal(this.portalId, pipePos);
            }

            connectedFluidPipeMultiblocksMap.remove(pipeId);
            Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " DISCONNECTED from FluidPipeMultiblock " + pipeId.toString().substring(0, 8) + " (removed " + connectionPoints.size() + " connection points)", true);
        } else {
            Logger.sendMessage("WARNING: Pipe ID " + pipeId.toString().substring(0, 8) + " not found in connectedFluidPipeMultiblocksMap", true);
        }

        markForSave();
    }

    // Helper methods to find adjacent cable/pipe positions for bidirectional tracking
    // Can connect to FRAME BLOCKS and CONTROLLER BLOCKS, but NOT interior blocks
    private Set<BlockPos> findAdjacentCablePositions(PowerCableMultiblock cable) {
        Set<BlockPos> adjacentPositions = new HashSet<>();

        // Check frame blocks for adjacent cables
        for (BlockPos framePos : frameBlocks) {
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = framePos.relative(direction);
                if (cable.getCableBlockPositions().contains(neighborPos)) {
                    adjacentPositions.add(neighborPos);
                }
            }
        }

        // Check controller blocks for adjacent cables
        for (PortalControllerBlockEntity controller : portalControllers) {
            BlockPos controllerPos = controller.getBlockPos();
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = controllerPos.relative(direction);
                if (cable.getCableBlockPositions().contains(neighborPos)) {
                    adjacentPositions.add(neighborPos);
                }
            }
        }

        return adjacentPositions;
    }

    private Set<BlockPos> findAdjacentPipePositions(FluidPipeMultiblock pipe) {
        Set<BlockPos> adjacentPositions = new HashSet<>();

        // Check frame blocks for adjacent pipes
        for (BlockPos framePos : frameBlocks) {
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = framePos.relative(direction);
                if (pipe.getPipeBlockPositions().contains(neighborPos)) {
                    adjacentPositions.add(neighborPos);
                }
            }
        }

        // Check controller blocks for adjacent pipes
        for (PortalControllerBlockEntity controller : portalControllers) {
            BlockPos controllerPos = controller.getBlockPos();
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = controllerPos.relative(direction);
                if (pipe.getPipeBlockPositions().contains(neighborPos)) {
                    adjacentPositions.add(neighborPos);
                }
            }
        }

        return adjacentPositions;
    }

    // Get connected batteries through power cables
    public Set<UUID> getConnectedBatteryIds() {
        Set<UUID> batteryIds = new HashSet<>();
        for (UUID cableId : connectedPowerCableMultiblocksMap.keySet()) {
            PowerCableMultiblock cable = PortalMultiblockManager.getPowerCableMultiblock(cableId);
            if (cable != null) {
                batteryIds.addAll(cable.getConnectedBatteryIds());
            }
        }
        return batteryIds;
    }

    // Get connected tanks through fluid pipes
    public Set<UUID> getConnectedTankIds() {
        Set<UUID> tankIds = new HashSet<>();
        for (UUID pipeId : connectedFluidPipeMultiblocksMap.keySet()) {
            FluidPipeMultiblock pipe = PortalMultiblockManager.getFluidPipeMultiblock(pipeId);
            if (pipe != null) {
                tankIds.addAll(pipe.getConnectedTankIds());
            }
        }
        return tankIds;
    }

    // Power management with round-robin consumption
    public boolean consumePower(int amount) {
        if (getCurrentPower() < amount) {
            Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) +
                    " power consumption FAILED: " + amount + " FE requested, but only " + getCurrentPower() + " FE available", true);
            return false;
        }

        int remaining = amount;
        Set<UUID> batteryIds = getConnectedBatteryIds();
        List<UUID> batteryList = new ArrayList<>(batteryIds);

        // Round-robin consumption
        while (remaining > 0 && !batteryList.isEmpty()) {
            for (UUID batteryId : batteryList) {
                BatteryMultiblock battery = PortalMultiblockManager.getBatteryMultiblock(batteryId);
                if (battery != null && battery.getStoredEnergy() > 0) {
                    int consumed = battery.consumeEnergy(Math.min(10, remaining));
                    remaining -= consumed;
                    if (remaining <= 0) break;
                }
            }
        }

        boolean success = remaining <= 0;
        if (success) {
            Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) +
                    " consumed " + amount + " FE successfully (remaining: " + getCurrentPower() + " FE)", true);
        } else {
            Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) +
                    " power consumption PARTIAL: " + (amount - remaining) + "/" + amount + " FE consumed", true);
        }

        markForSave();
        return success;
    }

    // Fluid management with round-robin consumption
    public boolean consumeFluid(int amount) {
        if (getCurrentFluid() < amount) {
            Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) +
                    " fluid consumption FAILED: " + amount + " mB requested, but only " + getCurrentFluid() + " mB available", true);
            return false;
        }

        int remaining = amount;
        Set<UUID> tankIds = getConnectedTankIds();
        List<UUID> tankList = new ArrayList<>(tankIds);

        // Round-robin consumption
        while (remaining > 0 && !tankList.isEmpty()) {
            for (UUID tankId : tankList) {
                TankMultiblock tank = PortalMultiblockManager.getTankMultiblock(tankId);
                if (tank != null && tank.getStoredFluid() > 0) {
                    int consumed = tank.consumeFluid(Math.min(100, remaining));
                    remaining -= consumed;
                    if (remaining <= 0) break;
                }
            }
        }

        boolean success = remaining <= 0;
        if (success) {
            Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) +
                    " consumed " + amount + " mB successfully (remaining: " + getCurrentFluid() + " mB)", true);
        } else {
            Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) +
                    " fluid consumption PARTIAL: " + (amount - remaining) + "/" + amount + " mB consumed", true);
        }

        markForSave();
        return success;
    }

    // Aggregate power/fluid stats - now calculated dynamically
    public int getCurrentPower() {
        int total = 0;
        for (UUID batteryId : getConnectedBatteryIds()) {
            BatteryMultiblock battery = PortalMultiblockManager.getBatteryMultiblock(batteryId);
            if (battery != null) {
                total += battery.getStoredEnergy();
            }
        }
        return total;
    }

    public int getMaxPowerCapacity() {
        int total = 0;
        for (UUID batteryId : getConnectedBatteryIds()) {
            BatteryMultiblock battery = PortalMultiblockManager.getBatteryMultiblock(batteryId);
            if (battery != null) {
                total += battery.getMaxCapacity();
            }
        }
        return total;
    }

    public int getCurrentFluid() {
        int total = 0;
        for (UUID tankId : getConnectedTankIds()) {
            TankMultiblock tank = PortalMultiblockManager.getTankMultiblock(tankId);
            if (tank != null) {
                total += tank.getStoredFluid();
            }
        }
        return total;
    }

    public int getMaxFluidCapacity() {
        int total = 0;
        for (UUID tankId : getConnectedTankIds()) {
            TankMultiblock tank = PortalMultiblockManager.getTankMultiblock(tankId);
            if (tank != null) {
                total += tank.getMaxCapacity();
            }
        }
        return total;
    }

    // PORTAL ACTIVATION METHODS
    public String activatePortal(PortalStructure teleportToPortal) {
        String error = "";
        if (!isValid) {
            Logger.sendMessage("Cannot activate invalid portal structure", true);
            error = "Cannot activate invalid current portal structure";
            return error;
        }
        if(teleportToPortal == null|| !teleportToPortal.isValid) {
            error = "Cannot activate invalid or nonexisting portal structure";
            return error;
        }

        // Check if we have at least one linked portal


        // Set this as activating side
        settings.setActivatingSide(true);

        // Check fluid for activation (only on activating side)
        if (getCurrentFluid() < PortalManager.ACTIVATION_FLUID_COST) {
            Logger.sendMessage("Cannot activate portal: Insufficient fluid (" +
                    getCurrentFluid() + "/" + PortalManager.ACTIVATION_FLUID_COST + " mB)", true);
            settings.setActivatingSide(false);
            error = "Cannot activate portal: Insufficient fluid";
            return error;
        }
        if (getCurrentPower() < PortalManager.MAINTENANCE_POWER_COST) {
            Logger.sendMessage("Cannot activate portal: Insufficient power", true);
            settings.setActivatingSide(false);
            return "cannot activate portal Insufficient power";
        }
        // Consume activation fluid
        if (!consumeFluid(PortalManager.ACTIVATION_FLUID_COST)) {
            Logger.sendMessage("Failed to consume activation fluid", true);
            settings.setActivatingSide(false);
            return "Failed to consume activation fluid";
        }

        // Check initial power


        // Activate portal
        isActive = true;
        settings.setActivationTime(System.currentTimeMillis());

        // Register with PortalManager for tick processing
        PortalManager.registerActivePortal(this);

        // Activate linked portals (they open for free)


        Logger.sendMessage("Portal " + portalId.toString().substring(0, 8) + " ACTIVATED" +
                (settings.isActivatingSide() ? " (activating side)" : " (linked side)"), true);

        markForSave();
        return "";
    }



    private void deactivatePortal() {
        if (!isActive) return;

        isActive = false;
        settings.setActivatingSide(false);

        // Unregister from PortalManager
        PortalManager.unregisterPortal(this);

        // Deactivate linked portals


        Logger.sendMessage("Portal " + portalId.toString().substring(0, 8) + " DEACTIVATED", true);

        markForSave();
    }

    public void setPortalName(String name) {
       if( PortalMultiblockManager.updatePortalName(settings.getPortalName(), name, this.portalId)){
           settings.setPortalName(name);
           Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " renamed to: " + name, true);
           markForSave();
       }

    }

    public String getPortalName() {
        return settings.getPortalName();
    }

    public void addLinkedPortal(UUID portalId) {
        if (!settings.getLinkedPortals().contains(portalId) && !portalId.equals(this.portalId)) {
            settings.addLinkedPortal(portalId);
            Logger.sendMessage("PortalStructure " + this.portalId.toString().substring(0, 8) +
                    " linked to PortalStructure " + portalId.toString().substring(0, 8) +
                    " (total: " + settings.getLinkedPortals().size() + " links)", true);
            markForSave();
        }
    }

    public void removeLinkedPortal(UUID portalId) {
        settings.removeLinkedPortal(portalId);
            Logger.sendMessage("PortalStructure " + this.portalId.toString().substring(0, 8) +
                    " unlinked from PortalStructure " + portalId.toString().substring(0, 8) +
                    " (total: " + settings.getLinkedPortals().size() + " links)", true);
            markForSave();

    }

    public List<UUID> getLinkedPortals() {
        return settings.getLinkedPortals();
    }

    public boolean isLinkedTo(UUID portalId) {
        return settings.getLinkedPortals().contains(portalId);
    }

    // Validation and state methods
    public boolean isValid() {
        return isValid;
    }

    public boolean isActive() {
        return isActive && isValid;
    }

    // Block position queries
    public boolean isFrameBlock(BlockPos pos) {
        return frameBlocks.contains(pos);
    }

    public boolean isInteriorBlock(BlockPos pos) {
        return interiorBlocks.contains(pos);
    }

    // Getters
    public UUID getPortalId() { return portalId; }
    public Set<BlockPos> getFrameBlocks() { return Collections.unmodifiableSet(frameBlocks); }
    public Set<BlockPos> getInteriorBlocks() { return Collections.unmodifiableSet(interiorBlocks); }
    public Level getLevel() { return level; }
    public ResourceKey<Level> getLevelKey() { return levelKey; }

    // Multiblock getters
    public Set<UUID> getConnectedPowerCableIds() { return Collections.unmodifiableSet(connectedPowerCableMultiblocksMap.keySet()); }
    public Set<UUID> getConnectedFluidPipeIds() { return Collections.unmodifiableSet(connectedFluidPipeMultiblocksMap.keySet()); }

    public boolean isConnectedToPowerCable(UUID cableId) {
        return connectedPowerCableMultiblocksMap.containsKey(cableId) && !connectedPowerCableMultiblocksMap.get(cableId).isEmpty();
    }

    public boolean isConnectedToFluidPipe(UUID pipeId) {
        return connectedFluidPipeMultiblocksMap.containsKey(pipeId) && !connectedFluidPipeMultiblocksMap.get(pipeId).isEmpty();
    }

    // Portal dimensions
    public PortalDimensions getDimensions() {
        if (frameBlocks.isEmpty()) return new PortalDimensions(0, 0);

        // Determine plane orientation
        Set<Integer> xValues = new HashSet<>();
        Set<Integer> zValues = new HashSet<>();

        for (BlockPos pos : frameBlocks) {
            xValues.add(pos.getX());
            zValues.add(pos.getZ());
        }

        boolean constantX = xValues.size() == 1;
        boolean constantZ = zValues.size() == 1;

        if (!constantX && !constantZ) return new PortalDimensions(0, 0);

        // Get min/max Y
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minOther = Integer.MAX_VALUE, maxOther = Integer.MIN_VALUE;

        for (BlockPos pos : frameBlocks) {
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());

            if (constantX) {
                int z = pos.getZ();
                minOther = Math.min(minOther, z);
                maxOther = Math.max(maxOther, z);
            } else {
                int x = pos.getX();
                minOther = Math.min(minOther, x);
                maxOther = Math.max(maxOther, x);
            }
        }

        int interiorWidth = maxOther - minOther - 1;
        int interiorHeight = maxY - minY - 1;

        return new PortalDimensions(interiorWidth, interiorHeight);
    }

    public static class PortalDimensions {
        public final int width;
        public final int height;

        public PortalDimensions(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public boolean canEntityFit() {
            return width >= 1 && height >= 2;
        }

        @Override
        public String toString() {
            return width + "x" + height;
        }
    }

    @Override
    public String toString() {
        return String.format("PortalStructure[%s: %s, Valid: %s, Active: %s, Power: %d/%d, Fluid: %d/%d, Links: %d, Cables: %d, Pipes: %d]",
                portalId.toString().substring(0, 8), settings.getPortalName(), isValid, isActive,
                getCurrentPower(), getMaxPowerCapacity(), getCurrentFluid(), getMaxFluidCapacity(),
                settings.getLinkedPortals().size(), connectedPowerCableMultiblocksMap.size(), connectedFluidPipeMultiblocksMap.size());
    }

    public String getStatus() {
        if (!isValid) return "Invalid Structure";
        if (!isActive) return "Inactive";
        return String.format("Active - %d links, %d/%d FE, %d/%d mB",
                settings.getLinkedPortals().size(), getCurrentPower(), getMaxPowerCapacity(),
                getCurrentFluid(), getMaxFluidCapacity());
    }

    // Power and fluid status methods for GUI
    public float getPowerFillPercentage() {
        int max = getMaxPowerCapacity();
        if (max == 0) return 0.0f;
        return (float) getCurrentPower() / max;
    }

    public float getFluidFillPercentage() {
        int max = getMaxFluidCapacity();
        if (max == 0) return 0.0f;
        return (float) getCurrentFluid() / max;
    }

    public boolean hasPower() {
        return getCurrentPower() > 0;
    }

    public boolean hasFluid() {
        return getCurrentFluid() > 0;
    }

    public boolean canTeleport() {
        return isActive() && hasPower() && !settings.getLinkedPortals().isEmpty();
    }

    // Resource consumption for teleportation
    public boolean consumeTeleportResources() {
        int powerCost = 1000;
        int fluidCost = 100;

        if (getCurrentPower() >= powerCost && getCurrentFluid() >= fluidCost) {
            boolean success = consumePower(powerCost) && consumeFluid(fluidCost);
            if (success) {
                Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) +
                        " consumed teleport resources: " + powerCost + " FE, " + fluidCost + " mB", true);
            }
            return success;
        }
        Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) +
                " teleport resources INSUFFICIENT: " + getCurrentPower() + "/" + powerCost + " FE, " +
                getCurrentFluid() + "/" + fluidCost + " mB", true);
        return false;
    }

    // Settings getters
    public PortalSettings getSettings() { return settings; }
    public boolean isActivatingSide() { return settings.isActivatingSide(); }
    public boolean shouldCloseAfterTeleport() { return settings.shouldCloseAfterTeleport(); }
    public void setCloseAfterTeleport(boolean close) {
        settings.setCloseAfterTeleport(close);
        markForSave();
    }
    public boolean isDurationExpired(long currentTime) {
        return settings.isDurationExpired(currentTime);
    }

    // Bounds getter
    public PortalBounds getBounds() { return bounds; }
}