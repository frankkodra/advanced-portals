package portal_fluid_tank;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import portal_fluid_pipe.FluidPipeMultiblock;
import portal_fluid_pipe.PortalFluidPipeBlockEntity;
import portal_multiblock.PortalMultiblockManager;
import advanced_portals.Logger;
import java.util.*;

public class TankMultiblock {
    private final UUID multiblockId;
    private final Set<BlockPos> tankBlocks;
    public Set<UUID> connectedPortalsId;
    private Level level = null;

    // Track pipe multiblock connections with specific tank blocks
    public Map<UUID, Set<BlockPos>> connectedPipeMultiblocksMap;

    private int storedFluid;
    private final int capacityPerTank = 16000;

    public TankMultiblock(UUID multiblockId, Level level) {
        this.multiblockId = multiblockId;
        this.tankBlocks = new HashSet<>();
        this.connectedPortalsId = new HashSet<>();
        this.level = level;
        this.storedFluid = 0;
        this.connectedPipeMultiblocksMap = new HashMap<>();

        PortalMultiblockManager.addTankMultiblock(this);
        Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) + " created", true);
    }

    public static TankMultiblock getOrCreateTankMultiblock(UUID multiblockId, Level level) {
        if (multiblockId == null) {
            multiblockId = UUID.randomUUID();
            Logger.sendMessage(String.format("TankMultiblock: Generated new ID %s on load/tick", multiblockId.toString().substring(0, 8)), true);
        }

        TankMultiblock existing = PortalMultiblockManager.getTankMultiblock(multiblockId);
        if (existing != null) {
            Logger.sendMessage(String.format("TankMultiblock: Found existing multiblock with ID %s on load/tick.", multiblockId.toString().substring(0, 8)), true);
            return existing;
        }

        TankMultiblock newMultiblock = new TankMultiblock(multiblockId, level);
        Logger.sendMessage(String.format("TankMultiblock: Recreated multiblock with ID %s from saved data on load/tick.", multiblockId.toString().substring(0, 8)), true);
        return newMultiblock;
    }

    public static TankMultiblock addCreateOrMergeMultiblockForBlockPlaced(BlockPos pos, Level level) {
        Set<TankMultiblock> adjacentMultiblocks = findAdjacentTankMultiblocks(pos, level);
        Logger.sendMessage("Placing tank at " + pos + " - Found " + adjacentMultiblocks.size() + " adjacent TankMultiblocks", true);

        TankMultiblock resultMultiblock;

        if (adjacentMultiblocks.isEmpty()) {
            resultMultiblock = new TankMultiblock(UUID.randomUUID(), level);
            resultMultiblock.addTank(pos);
            Logger.sendMessage("New TankMultiblock " + resultMultiblock.multiblockId.toString().substring(0, 8) + " created with 1 tank at " + pos, true);
        } else {
            resultMultiblock = mergeAllAdjacentMultiblocks(adjacentMultiblocks);
            resultMultiblock.addTank(pos);
            Logger.sendMessage("TankMultiblock " + resultMultiblock.multiblockId.toString().substring(0, 8) + " added tank at " + pos + " (total: " + resultMultiblock.tankBlocks.size() + " tanks)", true);
        }

        updateBlockEntityMultiblockReference(pos, level, resultMultiblock);
        return resultMultiblock;
    }

    public void addTank(BlockPos pos) {
        tankBlocks.add(pos);
        Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) + " added tank at " + pos + " (total: " + tankBlocks.size() + " tanks)", true);
    }

    private static Set<TankMultiblock> findAdjacentTankMultiblocks(BlockPos pos, Level level) {
        Set<TankMultiblock> multiblocks = new HashSet<>();
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            Block block = level.getBlockState(neighborPos).getBlock();
            if (block instanceof PortalFluidTankBlock) {
                TankMultiblock neighborMultiblock = getMultiblockFromBlockEntity(neighborPos, level);
                if (neighborMultiblock != null) {
                    multiblocks.add(neighborMultiblock);
                    Logger.sendMessage("Found adjacent tank at " + neighborPos + " belonging to TankMultiblock " + neighborMultiblock.multiblockId.toString().substring(0, 8), true);
                }
            }
        }
        return multiblocks;
    }

    public static TankMultiblock getMultiblockFromBlockEntity(BlockPos pos, Level level) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof PortalFluidTankBlockEntity) {
            return ((PortalFluidTankBlockEntity) blockEntity).getTankMultiblock();
        }
        return null;
    }

    private static void updateBlockEntityMultiblockReference(BlockPos pos, Level level, TankMultiblock multiblock) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof PortalFluidTankBlockEntity) {
            ((PortalFluidTankBlockEntity) blockEntity).setTankMultiblock(multiblock);
            Logger.sendMessage("Updated tank block entity reference at " + pos + " to TankMultiblock " + multiblock.multiblockId.toString().substring(0, 8), true);
        }
    }

    private static TankMultiblock mergeAllAdjacentMultiblocks(Set<TankMultiblock> multiblocksToMerge) {
        if (multiblocksToMerge.isEmpty()) return null;
        Iterator<TankMultiblock> iterator = multiblocksToMerge.iterator();
        TankMultiblock mainMultiblock = iterator.next();

        if (multiblocksToMerge.size() == 1) {
            Logger.sendMessage("Only one adjacent TankMultiblock " + mainMultiblock.multiblockId.toString().substring(0, 8) + " found", true);
            return mainMultiblock;
        }

        Logger.sendMessage("Merging " + multiblocksToMerge.size() + " TankMultiblocks into " + mainMultiblock.multiblockId.toString().substring(0, 8), true);
        int totalTanksBefore = mainMultiblock.tankBlocks.size();
        int totalFluidBefore = mainMultiblock.storedFluid;

        Set<BlockPos> allPositionsToUpdate = new HashSet<>();
        while (iterator.hasNext()) {
            TankMultiblock otherMultiblock = iterator.next();
            allPositionsToUpdate.addAll(otherMultiblock.tankBlocks);

            if (otherMultiblock.multiblockId.equals(mainMultiblock.multiblockId)) continue;

            int tanksToAdd = otherMultiblock.tankBlocks.size();
            int fluidToAdd = otherMultiblock.storedFluid;
            mainMultiblock.mergeWith(otherMultiblock);
            PortalMultiblockManager.removeTankMultiblock(otherMultiblock);

            Logger.sendMessage("Merged TankMultiblock " + otherMultiblock.multiblockId.toString().substring(0, 8) + " into " + mainMultiblock.multiblockId.toString().substring(0, 8) + " (+" + tanksToAdd + " tanks, +" + fluidToAdd + " mB)", true);
        }

        updateAllBlockEntityReferences(mainMultiblock, allPositionsToUpdate);
        int totalTanksAdded = mainMultiblock.tankBlocks.size() - totalTanksBefore;
        int totalFluidAdded = mainMultiblock.storedFluid - totalFluidBefore;
        Logger.sendMessage("Tank merge complete: " + totalTanksAdded + " total tanks added, " + totalFluidAdded + " mB transferred", true);

        return mainMultiblock;
    }

    private static void updateAllBlockEntityReferences(TankMultiblock multiblock, Set<BlockPos> positionsToUpdate) {
        if (multiblock.level == null) return;
        int updatedCount = 0;
        for (BlockPos pos : positionsToUpdate) {
            BlockEntity blockEntity = multiblock.level.getBlockEntity(pos);
            if (blockEntity instanceof PortalFluidTankBlockEntity) {
                ((PortalFluidTankBlockEntity) blockEntity).setTankMultiblock(multiblock);
                updatedCount++;
            }
        }
        Logger.sendMessage("Updated " + updatedCount + " tank block entity references to TankMultiblock " + multiblock.multiblockId.toString().substring(0, 8), true);
    }

    public void handleTankBlockBreak(BlockPos removedPos) {
        Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) + " removing tank at " + removedPos + " (currently " + tankBlocks.size() + " tanks)", true);

        if (this.tankBlocks.remove(removedPos)) {
            // SIMPLE: Remove pipe connections for this tank
            removePipeConnectionsForTank(removedPos);

            if (tankBlocks.isEmpty()) {
                PortalMultiblockManager.removeTankMultiblock(this);
                Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) + " DESTROYED (no tanks remaining)", true);
                return;
            }

            // Check if the multiblock needs to split into multiple components
            List<Set<BlockPos>> disconnectedComponents = findDisconnectedComponents();
            if (disconnectedComponents.size() > 1) {
                Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) + " splitting into " + disconnectedComponents.size() + " components", true);
                splitIntoComponents(disconnectedComponents);
            } else {
                Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) + " now has " + tankBlocks.size() + " tanks (no split needed)", true);
            }
        }
    }

    // SIMPLE: Just check all neighboring pipes and remove connections
    private void removePipeConnectionsForTank(BlockPos tankPos) {
        Logger.sendMessage("Removing pipe connections for tank " + tankPos, true);
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = tankPos.relative(direction);
            BlockEntity blockEntity = level.getBlockEntity(neighborPos);

            if (blockEntity instanceof PortalFluidPipeBlockEntity) {
                FluidPipeMultiblock pipeMultiblock = ((PortalFluidPipeBlockEntity) blockEntity).getMultiblock();
                if (pipeMultiblock != null) {
                    Logger.sendMessage("Found adjacent pipe at " + neighborPos + " - removing connection", true);
                    pipeMultiblock.removeTankConnection(this, neighborPos);
                }
            }
        }
    }

    private List<Set<BlockPos>> findDisconnectedComponents() {
        Set<BlockPos> visited = new HashSet<>();
        List<Set<BlockPos>> components = new ArrayList<>();
        for (BlockPos startPos : tankBlocks) {
            if (!visited.contains(startPos)) {
                Set<BlockPos> component = new HashSet<>();
                floodFill(startPos, component, visited);
                components.add(component);
            }
        }
        return components;
    }

    private void floodFill(BlockPos start, Set<BlockPos> component, Set<BlockPos> visited) {
        if (visited.contains(start)) return;
        visited.add(start);
        component.add(start);
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = start.relative(direction);
            if (tankBlocks.contains(neighbor)) {
                floodFill(neighbor, component, visited);
            }
        }
    }

    private void splitIntoComponents(List<Set<BlockPos>> components) {
        // Store the original multiblock ID for connection cleanup
        UUID originalMultiblockId = this.multiblockId;

        // First component keeps the original multiblock
        Set<BlockPos> mainComponent = components.get(0);
        this.tankBlocks.clear();
        this.tankBlocks.addAll(mainComponent);
        redistributeFluidForSplit(components);

        // Create new multiblocks for other components
        for (int i = 1; i < components.size(); i++) {
            Set<BlockPos> component = components.get(i);
            TankMultiblock newMultiblock = new TankMultiblock(UUID.randomUUID(), level);
            newMultiblock.tankBlocks.addAll(component);

            Logger.sendMessage("Created new TankMultiblock " + newMultiblock.multiblockId.toString().substring(0, 8) + " with " + component.size() + " tanks from split", true);

            // CRITICAL: Update pipe connections for the new multiblock
            updatePipeConnectionsForNewMultiblock(newMultiblock, component, originalMultiblockId);

            // Update all block entity references for the new multiblock
            for (BlockPos pos : component) {
                updateBlockEntityMultiblockReference(pos, level, newMultiblock);
            }
        }

        // CRITICAL: Update pipe connections for the main multiblock (remove connections to tanks that are no longer in this multiblock)
        updatePipeConnectionsForMainMultiblock(mainComponent, originalMultiblockId);

        updateAllBlockEntityReferences(this, mainComponent);
        Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) + " now has " + tankBlocks.size() + " tanks after split", true);
    }

    // CRITICAL: Update pipe connections for the main multiblock after split
    private void updatePipeConnectionsForMainMultiblock(Set<BlockPos> mainComponent, UUID originalMultiblockId) {
        Logger.sendMessage("Updating pipe connections for main multiblock after split", true);

        // For each pipe that was connected to the original multiblock
        for (Map.Entry<UUID, Set<BlockPos>> entry : new HashMap<>(connectedPipeMultiblocksMap).entrySet()) {
            UUID pipeId = entry.getKey();
            Set<BlockPos> connectionPoints = entry.getValue();

            // Remove connection points that are no longer in this multiblock
            Set<BlockPos> pointsToRemove = new HashSet<>();
            for (BlockPos connectionPoint : connectionPoints) {
                if (!mainComponent.contains(connectionPoint)) {
                    pointsToRemove.add(connectionPoint);
                }
            }

            // Remove the invalid connection points
            connectionPoints.removeAll(pointsToRemove);

            // If no connection points remain, remove the pipe entirely
            if (connectionPoints.isEmpty()) {
                connectedPipeMultiblocksMap.remove(pipeId);
                Logger.sendMessage("Removed pipe " + pipeId.toString().substring(0, 8) + " from main multiblock - no valid connections", true);
            } else if (!pointsToRemove.isEmpty()) {
                Logger.sendMessage("Removed " + pointsToRemove.size() + " invalid connection points from pipe " + pipeId.toString().substring(0, 8), true);
            }

            // Notify the pipe multiblock about the removed connections
            if (!pointsToRemove.isEmpty()) {
                FluidPipeMultiblock pipe = PortalMultiblockManager.getFluidPipeMultiblock(pipeId);
                if (pipe != null) {
                    for (BlockPos removedPoint : pointsToRemove) {
                        pipe.removeTankConnection(this, removedPoint);
                    }
                }
            }
        }
    }

    // CRITICAL: Set up pipe connections for a new multiblock after split
    private void updatePipeConnectionsForNewMultiblock(TankMultiblock newMultiblock, Set<BlockPos> component, UUID originalMultiblockId) {
        Logger.sendMessage("Setting up pipe connections for new multiblock " + newMultiblock.multiblockId.toString().substring(0, 8), true);

        // Find all pipes that were connected to tanks in this component through the original multiblock
        for (Map.Entry<UUID, Set<BlockPos>> entry : connectedPipeMultiblocksMap.entrySet()) {
            UUID pipeId = entry.getKey();
            Set<BlockPos> connectionPoints = entry.getValue();

            // Find connection points that belong to this new component
            Set<BlockPos> pointsForNewMultiblock = new HashSet<>();
            for (BlockPos connectionPoint : connectionPoints) {
                if (component.contains(connectionPoint)) {
                    pointsForNewMultiblock.add(connectionPoint);
                }
            }

            // If this pipe has connections to tanks in the new component, transfer them
            if (!pointsForNewMultiblock.isEmpty()) {
                Logger.sendMessage("Transferring " + pointsForNewMultiblock.size() + " connection points to new multiblock for pipe " + pipeId.toString().substring(0, 8), true);

                // Add connections to the new multiblock
                for (BlockPos connectionPoint : pointsForNewMultiblock) {
                    newMultiblock.addPipeConnectionFromPipe(pipeId, connectionPoint);
                }

                // Notify the pipe multiblock about the new connections
                FluidPipeMultiblock pipe = PortalMultiblockManager.getFluidPipeMultiblock(pipeId);
                if (pipe != null) {
                    // The pipe will update its connection map to point to the new multiblock ID
                    for (BlockPos connectionPoint : pointsForNewMultiblock) {
                        // Remove the old connection (to original multiblock)
                        pipe.removeTankConnection(this, connectionPoint);
                        // Add the new connection (to new multiblock)
                        pipe.addTankConnection(newMultiblock, connectionPoint);
                    }
                }
            }
        }
    }

    private void redistributeFluidForSplit(List<Set<BlockPos>> components) {
        int totalTanks = components.stream().mapToInt(Set::size).sum();
        if (totalTanks == 0) return;
        int originalFluid = this.storedFluid;
        this.storedFluid = (originalFluid * this.tankBlocks.size()) / totalTanks;
        Logger.sendMessage("Redistributed fluid: " + originalFluid + " mB -> " + this.storedFluid + " mB for main component", true);
    }

    public void addPipeConnectionFromPipe(UUID pipeId, BlockPos tankPos) {
        Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) + " connected to FluidPipeMultiblock " + pipeId.toString().substring(0, 8) + " at " + tankPos, true);
        if (!connectedPipeMultiblocksMap.containsKey(pipeId)) {
            connectedPipeMultiblocksMap.put(pipeId, new HashSet<>());
        }
        connectedPipeMultiblocksMap.get(pipeId).add(tankPos);
    }

    public void removePipeConnectionFromPipe(UUID pipeId, BlockPos tankPos) {
        Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) + " disconnected from FluidPipeMultiblock " + pipeId.toString().substring(0, 8) + " at " + tankPos, true);
        if (connectedPipeMultiblocksMap.containsKey(pipeId)) {
            Set<BlockPos> connectionPoints = connectedPipeMultiblocksMap.get(pipeId);
            connectionPoints.remove(tankPos);
            if (connectionPoints.isEmpty()) {
                connectedPipeMultiblocksMap.remove(pipeId);
            }
        }
    }

    public void mergeWith(TankMultiblock other) {
        Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) + " merging with " + other.multiblockId.toString().substring(0, 8), true);

        // Merge basic properties
        this.tankBlocks.addAll(other.tankBlocks);
        this.connectedPortalsId.addAll(other.connectedPortalsId);
        this.storedFluid += other.storedFluid;

        // CRITICAL: Merge pipe connections
        for (Map.Entry<UUID, Set<BlockPos>> entry : other.connectedPipeMultiblocksMap.entrySet()) {
            UUID pipeId = entry.getKey();
            Set<BlockPos> tankPositions = entry.getValue();

            if (!this.connectedPipeMultiblocksMap.containsKey(pipeId)) {
                this.connectedPipeMultiblocksMap.put(pipeId, new HashSet<>());
            }
            this.connectedPipeMultiblocksMap.get(pipeId).addAll(tankPositions);

            Logger.sendMessage("Merged " + tankPositions.size() + " connection points from pipe " + pipeId.toString().substring(0, 8), true);

            // CRITICAL: Notify the pipe multiblock to update its connection from the old multiblock ID to the new one
            FluidPipeMultiblock pipe = PortalMultiblockManager.getFluidPipeMultiblock(pipeId);
            if (pipe != null) {
                for (BlockPos tankPos : tankPositions) {
                    // Remove connection to the old multiblock
                    pipe.removeTankConnection(other, tankPos);
                    // Add connection to the new merged multiblock
                    pipe.addTankConnection(this, tankPos);
                }
                Logger.sendMessage("Updated pipe " + pipeId.toString().substring(0, 8) + " connections from old multiblock to merged multiblock", true);
            }
        }

        Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) + " merged with " + other.multiblockId.toString().substring(0, 8) + " - added " + other.tankBlocks.size() + " tanks, " + other.connectedPipeMultiblocksMap.size() + " pipe connections, " + other.storedFluid + " mB", true);
    }

    public boolean connectToPortal(UUID portalId) {
        this.connectedPortalsId.add(portalId);
        Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) + " connected to portal " + portalId.toString().substring(0, 8), true);
        return true;
    }

    public void disconnectFromPortal(UUID portalId) {
        this.connectedPortalsId.remove(portalId);
        Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) + " disconnected from portal " + portalId.toString().substring(0, 8), true);
    }

    public int consumeFluid(int amount) {
        int fluidToConsume = Math.min(amount, storedFluid);
        storedFluid -= fluidToConsume;
        Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) + " consumed " + fluidToConsume + " mB (remaining: " + storedFluid + "/" + getMaxCapacity() + " mB)", true);
        return fluidToConsume;
    }

    public int addFluid(int amount) {
        int capacity = getMaxCapacity();
        int spaceAvailable = capacity - storedFluid;
        int fluidToAdd = Math.min(amount, spaceAvailable);
        storedFluid += fluidToAdd;
        Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) + " added " + fluidToAdd + " mB (now: " + storedFluid + "/" + getMaxCapacity() + " mB)", true);
        return fluidToAdd;
    }

    public int getStoredFluid() {
        return storedFluid;
    }

    public int getMaxCapacity() {
        return tankBlocks.size() * capacityPerTank;
    }

    public UUID getMultiblockId() {
        return multiblockId;
    }

    public Set<UUID> getConnectedPortalsId() {
        return connectedPortalsId;
    }

    public Set<BlockPos> getTankBlocks() {
        return Collections.unmodifiableSet(tankBlocks);
    }

    public Level getLevel() {
        return level;
    }

    public boolean isEmpty() {
        return tankBlocks.isEmpty();
    }

    public Set<UUID> getConnectedPipeIds() {
        return connectedPipeMultiblocksMap.keySet();
    }

    public boolean isConnectedToPipe(UUID pipeId) {
        return connectedPipeMultiblocksMap.containsKey(pipeId) && !connectedPipeMultiblocksMap.get(pipeId).isEmpty();
    }

    @Override
    public String toString() {
        return String.format("TankMultiblock[%s: %d tanks, %d/%d mB, Connected Pipes: %d, Connected Portals: %d]", multiblockId.toString().substring(0, 8), tankBlocks.size(), storedFluid, getMaxCapacity(), connectedPipeMultiblocksMap.size(), connectedPortalsId.size());
    }
}