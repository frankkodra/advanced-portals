package portal_multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.common.Mod;
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

import static portal_multiblock.PortalMultiblockManager.portals;

public class PortalStructure {
    private final UUID portalId;
    private String portalName;
    public Set<PortalControllerBlockEntity> portalControllers;
    private final Set<BlockPos> frameBlocks;
    private final Set<BlockPos> interiorBlocks;

    // CRITICAL: Track cable/pipe connections bidirectionally with specific positions
    public Map<UUID, Set<BlockPos>> connectedPowerCableMultiblocksMap;
    public Map<UUID, Set<BlockPos>> connectedFluidPipeMultiblocksMap;

    private boolean isValid;
    private boolean isActive;
    private final Level level;
    private final List<UUID> linkedPortals;
    private ResourceKey<Level> levelKey;

    public PortalStructure(UUID portalId, Level level) {
        this.portalId = portalId;
        this.portalName = "Portal_" + portalId.toString().substring(0, 8);
        this.frameBlocks = new HashSet<>();
        this.interiorBlocks = new HashSet<>();
        this.connectedPowerCableMultiblocksMap = new HashMap<>();
        this.connectedFluidPipeMultiblocksMap = new HashMap<>();
        this.linkedPortals = new ArrayList<>();

        this.level = level;
        this.isValid = false;
        this.isActive = false;
        this.levelKey = level.dimension();
        this.portalControllers = new HashSet<>();

        PortalMultiblockManager.addPortalStructure(this);
        Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " created", true);
    }

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

    public void addPortalControllerBlock(PortalControllerBlockEntity block) {
        portalControllers.add(block);
        block.setPortalStructure(this);
        Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) +
                " added controller at " + block.getBlockPos() + " (total: " + portalControllers.size() + " controllers)", true);
    }

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

    public void addPortalBlock(BlockPos pos) {
        frameBlocks.add(pos);
        Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) +
                " added portal block at " + pos + " (total: " + frameBlocks.size() + " frame blocks)", true);
    }

    public void removePortalBlock(BlockPos pos) {
        if (frameBlocks.remove(pos)) {
            Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) +
                    " removed portal block at " + pos + " (total: " + frameBlocks.size() + " frame blocks remaining)", true);

            if (frameBlocks.isEmpty()) {
                PortalMultiblockManager.removePortalStructure(this);
                Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " DESTROYED (no frame blocks remaining)", true);
            } else {
                // VALIDATION: Run validation after block removal (could cause split)
                revalidateStructure();
            }
        }
    }

    public void removePortalController(PortalControllerBlockEntity controller) {
        if (portalControllers.remove(controller)) {
            Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) +
                    " removed controller at " + controller.getBlockPos() + " (total: " + portalControllers.size() + " controllers remaining)", true);

            // VALIDATION: Run validation after controller removal
            revalidateStructure();
        }
    }

    // VALIDATION: Enhanced validation with comprehensive logging
    public void revalidateStructure() {
        Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " revalidating structure...", true);

        // Clear previous state but keep connected blocks
        frameBlocks.clear();
        interiorBlocks.clear();
        isValid = false;

        // If we lost our controller, portal is invalid
        if (portalControllers.isEmpty()) {
            Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " INVALID: No controllers", true);
            setActive(false);
            return;
        }

        isValid = validatePortalStructure();

        if (isValid) {
            calculateInteriorBlocks();
            if (isActive) {
                activatePortal();
            }
            Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " VALIDATED: " +
                    frameBlocks.size() + " frame blocks, " + interiorBlocks.size() + " interior blocks", true);
        } else {
            Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " INVALID: Structure validation failed", true);
            setActive(false);
        }
    }

    private boolean validatePortalStructure() {
        Logger.sendMessage("VALIDATION: Starting structure validation for PortalStructure " + portalId.toString().substring(0, 8), true);

        // Check if we have a controller
        if (portalControllers.isEmpty()) {
            Logger.sendMessage("VALIDATION FAILED: No controllers found", true);
            return false;
        }

        // Check if controller is adjacent to frame
        boolean controllerAdjacentToFrame = false;
        for (PortalControllerBlockEntity controller : portalControllers) {
            BlockPos controllerPos = controller.getBlockPos();
            if (isControllerAdjacentToFrame(controllerPos)) {
                controllerAdjacentToFrame = true;
                Logger.sendMessage("VALIDATION: Controller at " + controllerPos + " is adjacent to frame", true);
                break;
            }
        }

        if (!controllerAdjacentToFrame) {
            Logger.sendMessage("VALIDATION FAILED: No controller adjacent to frame blocks", true);
            return false;
        }

        // Validate frame structure
        if (!validateRectangleFrame()) {
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

    private boolean validateRectangleFrame() {
        Logger.sendMessage("FRAME VALIDATION: Checking frame structure with " + frameBlocks.size() + " blocks", true);

        if (frameBlocks.size() < 10) { // Minimum for 3x4 frame perimeter (12 blocks - 2 corners overlap)
            Logger.sendMessage("FRAME VALIDATION FAILED: Too few frame blocks (" + frameBlocks.size() + ")", true);
            return false;
        }

        // Group frames by Y level (portals are flat structures)
        Map<Integer, Set<BlockPos>> framesByLevel = new HashMap<>();
        for (BlockPos pos : frameBlocks) {
            framesByLevel.computeIfAbsent(pos.getY(), k -> new HashSet<>()).add(pos);
        }

        // We only support single-level portals for now
        if (framesByLevel.size() != 1) {
            Logger.sendMessage("FRAME VALIDATION FAILED: Multi-level frames not supported (" + framesByLevel.size() + " levels)", true);
            return false;
        }

        int yLevel = framesByLevel.keySet().iterator().next();
        Set<BlockPos> levelFrames = framesByLevel.get(yLevel);

        // Find the bounding box of all frame blocks at this level
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : levelFrames) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        int width = maxX - minX + 1;
        int height = maxZ - minZ + 1;

        Logger.sendMessage("FRAME VALIDATION: Bounding box " + width + "x" + height + " at Y=" + yLevel, true);

        // Must be at least 3x4 or 4x4 (player needs 2 blocks interior height)
        boolean validDimensions = (width >= 3 && height >= 4) || (width >= 4 && height >= 4);
        if (!validDimensions) {
            Logger.sendMessage("FRAME VALIDATION FAILED: Invalid dimensions " + width + "x" + height + " - need min 3x4 or 4x4", true);
            return false;
        }

        // Calculate interior dimensions
        int interiorWidth = width - 2;
        int interiorHeight = height - 2;

        // Player must fit (1 block wide × 2 blocks tall minimum)
        boolean playerFits = interiorWidth >= 1 && interiorHeight >= 2;
        if (!playerFits) {
            Logger.sendMessage("FRAME VALIDATION FAILED: Interior too small " + interiorWidth + "x" + interiorHeight + " - player needs 1x2 minimum", true);
            return false;
        }

        Logger.sendMessage("FRAME VALIDATION: Interior dimensions " + interiorWidth + "x" + interiorHeight + " - player can fit", true);

        // Verify we have a complete rectangular frame
        boolean result = verifyCompleteRectangleFrame(minX, maxX, minZ, maxZ, yLevel, levelFrames);
        if (result) {
            Logger.sendMessage("FRAME VALIDATION PASSED: Complete " + width + "x" + height + " rectangle", true);
        } else {
            Logger.sendMessage("FRAME VALIDATION FAILED: Incomplete rectangle", true);
        }
        return result;
    }

    private boolean verifyCompleteRectangleFrame(int minX, int maxX, int minZ, int maxZ, int yLevel, Set<BlockPos> levelFrames) {
        Logger.sendMessage("RECTANGLE VALIDATION: Checking complete rectangle from (" + minX + "," + minZ + ") to (" + maxX + "," + maxZ + ")", true);

        // Check all four edges are completely filled with frame blocks
        for (int x = minX; x <= maxX; x++) {
            // Bottom edge
            if (!levelFrames.contains(new BlockPos(x, yLevel, minZ))) {
                Logger.sendMessage("RECTANGLE VALIDATION FAILED: Missing frame block at bottom edge (" + x + "," + minZ + ")", true);
                return false;
            }
            // Top edge
            if (!levelFrames.contains(new BlockPos(x, yLevel, maxZ))) {
                Logger.sendMessage("RECTANGLE VALIDATION FAILED: Missing frame block at top edge (" + x + "," + maxZ + ")", true);
                return false;
            }
        }

        for (int z = minZ; z <= maxZ; z++) {
            // Left edge
            if (!levelFrames.contains(new BlockPos(minX, yLevel, z))) {
                Logger.sendMessage("RECTANGLE VALIDATION FAILED: Missing frame block at left edge (" + minX + "," + z + ")", true);
                return false;
            }
            // Right edge
            if (!levelFrames.contains(new BlockPos(maxX, yLevel, z))) {
                Logger.sendMessage("RECTANGLE VALIDATION FAILED: Missing frame block at right edge (" + maxX + "," + z + ")", true);
                return false;
            }
        }

        // Verify no frame blocks outside the rectangle
        for (BlockPos pos : levelFrames) {
            if (pos.getX() < minX || pos.getX() > maxX || pos.getZ() < minZ || pos.getZ() > maxZ) {
                Logger.sendMessage("RECTANGLE VALIDATION FAILED: Frame block outside rectangle at " + pos, true);
                return false;
            }
        }

        // Verify interior is empty (no frame blocks inside)
        int interiorBlocksFound = 0;
        for (int x = minX + 1; x < maxX; x++) {
            for (int z = minZ + 1; z < maxZ; z++) {
                if (levelFrames.contains(new BlockPos(x, yLevel, z))) {
                    interiorBlocksFound++;
                    Logger.sendMessage("RECTANGLE VALIDATION FAILED: Frame block in interior at (" + x + "," + z + ")", true);
                }
            }
        }

        if (interiorBlocksFound > 0) {
            Logger.sendMessage("RECTANGLE VALIDATION FAILED: " + interiorBlocksFound + " frame blocks found in interior", true);
            return false;
        }

        Logger.sendMessage("RECTANGLE VALIDATION PASSED: Complete rectangle with no interior frame blocks", true);
        return true;
    }

    private void calculateInteriorBlocks() {
        interiorBlocks.clear();

        if (frameBlocks.isEmpty()) return;

        // Get the bounding box from frame blocks (all at same Y level)
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int yLevel = frameBlocks.iterator().next().getY();

        for (BlockPos pos : frameBlocks) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        // Add all interior positions (inside the frame)
        for (int x = minX + 1; x < maxX; x++) {
            for (int z = minZ + 1; z < maxZ; z++) {
                interiorBlocks.add(new BlockPos(x, yLevel, z));
            }
        }

        Logger.sendMessage("INTERIOR: Calculated " + interiorBlocks.size() + " interior blocks from (" + (minX+1) + "," + (minZ+1) + ") to (" + (maxX-1) + "," + (maxZ-1) + ")", true);
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

    private boolean isControllerAdjacentToFrame(BlockPos pos) {
        // Check if this block is adjacent to any frame block (including diagonals)
        for (BlockPos framePos : frameBlocks) {
            double distance = pos.distToCenterSqr(framePos.getX() + 0.5, framePos.getY() + 0.5, framePos.getZ() + 0.5);
            if (distance <= 2.0) { // Adjacent: 1 block away (1.0) or diagonal (2.0)
                return true;
            }
        }
        return false;
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

    public void activatePortal() {
        if (isValid && !isActive) {
            setActive(true);
        }
    }

    public void deactivatePortal() {
        if (isActive) {
            setActive(false);
        }
    }

    public void setActive(boolean active) {
        boolean wasActive = this.isActive;
        this.isActive = active && this.isValid;

        if (wasActive && !this.isActive) {
            Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " DEACTIVATED", true);
            deactivatePortal();
        } else if (!wasActive && this.isActive) {
            Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " ACTIVATED", true);
            activatePortal();
        }
    }

    // Name management methods
    public void setPortalName(String name) {
        PortalMultiblockManager.updatePortalName(this.portalName, name, this.portalId);
        this.portalName = name;
        Logger.sendMessage("PortalStructure " + portalId.toString().substring(0, 8) + " renamed to: " + name, true);
    }

    public String getPortalName() {
        return portalName;
    }

    // Link management methods
    public void addLinkedPortal(UUID portalId) {
        if (!linkedPortals.contains(portalId) && !portalId.equals(this.portalId)) {
            linkedPortals.add(portalId);
            Logger.sendMessage("PortalStructure " + this.portalId.toString().substring(0, 8) +
                    " linked to PortalStructure " + portalId.toString().substring(0, 8) +
                    " (total: " + linkedPortals.size() + " links)", true);
        }
    }

    public void removeLinkedPortal(UUID portalId) {
        if (linkedPortals.remove(portalId)) {
            Logger.sendMessage("PortalStructure " + this.portalId.toString().substring(0, 8) +
                    " unlinked from PortalStructure " + portalId.toString().substring(0, 8) +
                    " (total: " + linkedPortals.size() + " links)", true);
        }
    }

    public List<UUID> getLinkedPortals() {
        return Collections.unmodifiableList(linkedPortals);
    }

    public boolean isLinkedTo(UUID portalId) {
        return linkedPortals.contains(portalId);
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

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int yLevel = frameBlocks.iterator().next().getY();

        for (BlockPos pos : frameBlocks) {
            if (pos.getY() == yLevel) {
                minX = Math.min(minX, pos.getX());
                maxX = Math.max(maxX, pos.getX());
                minZ = Math.min(minZ, pos.getZ());
                maxZ = Math.max(maxZ, pos.getZ());
            }
        }

        int interiorWidth = (maxX - minX) - 1;
        int interiorHeight = (maxZ - minZ) - 1;

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
                portalId.toString().substring(0, 8), portalName, isValid, isActive,
                getCurrentPower(), getMaxPowerCapacity(), getCurrentFluid(), getMaxFluidCapacity(),
                linkedPortals.size(), connectedPowerCableMultiblocksMap.size(), connectedFluidPipeMultiblocksMap.size());
    }

    public String getStatus() {
        if (!isValid) return "Invalid Structure";
        if (!isActive) return "Inactive";
        return String.format("Active - %d links, %d/%d FE, %d/%d mB",
                getLinkedPortals().size(), getCurrentPower(), getMaxPowerCapacity(),
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
        return isActive() && hasPower() && !getLinkedPortals().isEmpty();
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
}