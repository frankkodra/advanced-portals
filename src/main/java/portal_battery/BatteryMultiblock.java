package portal_battery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import portal_multiblock.PortalMultiblockManager;
import portal_power_cable.PortalPowerCableBlock;
import portal_power_cable.PortalPowerCableBlockEntity;
import portal_power_cable.PowerCableMultiblock;
import advanced_portals.Logger;

import java.util.*;

public class BatteryMultiblock {
    private final UUID multiblockId;
    private final Set<BlockPos> batteryBlocks;
    public Set<UUID> connectedPortalsId;
    private Level level = null;

    // Track power cable multiblock connections with specific battery blocks
    public Map<UUID, Set<BlockPos>> connectedCableMultiblocksMap;

    private int storedEnergy;
    private final int capacityPerBattery = 100000;

    public BatteryMultiblock(UUID multiblockId, Level level) {
        this.multiblockId = multiblockId;
        this.batteryBlocks = new HashSet<>();
        this.connectedPortalsId = new HashSet<>();
        this.level = level;
        this.storedEnergy = 0;
        this.connectedCableMultiblocksMap = new HashMap<>();

        PortalMultiblockManager.addBatteryMultiblock(this);
        Logger.sendMessage("BatteryMultiblock " + multiblockId.toString().substring(0, 8) + " created", true);
    }

    /**
     * Retrieves an existing BatteryMultiblock from the manager based on its ID,
     * or creates a new one if the ID is not found.
     * This is used for lazy loading from a saved BlockEntity's UUID.
     */
    public static BatteryMultiblock getOrCreateBatteryMultiblock(UUID multiblockId, Level level) {
        // 1. If ID is null (which shouldn't happen during a proper load), create a new one.
        if (multiblockId == null) {
            multiblockId = UUID.randomUUID();
            Logger.sendMessage(String.format("BatteryMultiblock: Generated new ID %s on load/tick (Error or new block).", multiblockId.toString().substring(0, 8)), true);
        }

        // 2. Check if the multiblock is already loaded in the manager.
        BatteryMultiblock existing = PortalMultiblockManager.getBatteryMultiblock(multiblockId);
        if (existing != null) {
            Logger.sendMessage(String.format("BatteryMultiblock: Found existing multiblock with ID %s on load/tick.", multiblockId.toString().substring(0, 8)), true);
            return existing;
        }

        // 3. If not found, recreate it with the saved ID. The constructor handles adding it to the manager.
        BatteryMultiblock newMultiblock = new BatteryMultiblock(multiblockId, level);
        Logger.sendMessage(String.format("BatteryMultiblock: Recreated multiblock with ID %s from saved data on load/tick.", multiblockId.toString().substring(0, 8)), true);
        return newMultiblock;
    }

    public BatteryMultiblock(String batteryId, String[] portalIdsArr, BlockPos pos) {
        this.multiblockId = UUID.fromString(batteryId);
        this.batteryBlocks = new HashSet<>();
        this.batteryBlocks.add(pos);
        this.connectedPortalsId = new HashSet<>();
        this.connectedCableMultiblocksMap = new HashMap<>();
        this.storedEnergy = 0;

        Set<UUID> set = new HashSet<>();
        for(String portalId : portalIdsArr) {
            if(!portalId.trim().isEmpty()) {
                set.add(UUID.fromString(portalId));
            }
        }
        this.connectedPortalsId.addAll(set);

        PortalMultiblockManager.addBatteryMultiblock(this);
        Logger.sendMessage("BatteryMultiblock " + multiblockId.toString().substring(0, 8) + " recreated from save with 1 battery at " + pos, true);
    }

    // FIXED: Get multiblock from block entity instead of block
    public static BatteryMultiblock addCreateOrMergeMultiblockForBlockPlaced(BlockPos pos, Level level) {
        Set<BatteryMultiblock> adjacentMultiblocks = findAdjacentBatteryMultiblocks(pos, level);
        Set<PowerCableMultiblock> adjacentCables = findAdjacentPowerCables(pos, level);

        Logger.sendMessage("Placing battery at " + pos + " - Found " + adjacentMultiblocks.size() + " adjacent BatteryMultiblocks", true);

        BatteryMultiblock resultMultiblock;

        if (adjacentMultiblocks.isEmpty()) {
            // No adjacent batteries - create new multiblock
            resultMultiblock = new BatteryMultiblock(UUID.randomUUID(), level);
            resultMultiblock.addBattery(pos);
            Logger.sendMessage("New BatteryMultiblock " + resultMultiblock.multiblockId.toString().substring(0, 8) +
                    " created with 1 battery at " + pos, true);
        }
        else {
            // Always merge all adjacent multiblocks
            resultMultiblock = mergeAllAdjacentMultiblocks(adjacentMultiblocks);
            resultMultiblock.addBattery(pos);
            Logger.sendMessage("BatteryMultiblock " + resultMultiblock.multiblockId.toString().substring(0, 8) +
                    " added battery at " + pos + " (total: " + resultMultiblock.batteryBlocks.size() + " batteries)", true);
        }

        // FIXED: Update the block entity reference instead of block reference
        updateBlockEntityMultiblockReference(pos, level, resultMultiblock);

        // Establish connections to nearby power cables
        for (PowerCableMultiblock cable : adjacentCables) {
            resultMultiblock.addCableConnection(cable, pos);
        }

        return resultMultiblock;
    }

    public void addBattery(BlockPos pos) {
        batteryBlocks.add(pos);
    }

    // FIXED: Get multiblock from block entity instead of block
    private static Set<BatteryMultiblock> findAdjacentBatteryMultiblocks(BlockPos pos, Level level) {
        Set<BatteryMultiblock> multiblocks = new HashSet<>();

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            Block block = level.getBlockState(neighborPos).getBlock();

            if (block instanceof PortalBatteryBlock) {
                // FIXED: Get multiblock from block entity instead of block field
                BatteryMultiblock neighborMultiblock = getMultiblockFromBlockEntity(neighborPos, level);
                if (neighborMultiblock != null) {
                    multiblocks.add(neighborMultiblock);
                    Logger.sendMessage("Found adjacent battery at " + neighborPos + " belonging to BatteryMultiblock " +
                            neighborMultiblock.multiblockId.toString().substring(0, 8), true);
                } else {
                    Logger.sendMessage("WARNING: Battery at " + neighborPos + " has null multiblock reference in block entity!", true);
                }
            }
        }

        return multiblocks;
    }

    // FIXED: Helper method to get multiblock from block entity
    public static BatteryMultiblock getMultiblockFromBlockEntity(BlockPos pos, Level level) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof PortalBatteryBlockEntity) {
            return ((PortalBatteryBlockEntity) blockEntity).getBatteryMultiblock();
        }
        return null;
    }

    // FIXED: Helper method to update block entity reference
    private static void updateBlockEntityMultiblockReference(BlockPos pos, Level level, BatteryMultiblock multiblock) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof PortalBatteryBlockEntity) {
            ((PortalBatteryBlockEntity) blockEntity).setBatteryMultiblock(multiblock);
            Logger.sendMessage("Updated block entity reference at " + pos + " to BatteryMultiblock " +
                    multiblock.multiblockId.toString().substring(0, 8), true);
        } else {
            Logger.sendMessage("ERROR: No block entity found at " + pos + " to update multiblock reference!", true);
        }
    }

    // Find all adjacent power cables
    private static Set<PowerCableMultiblock> findAdjacentPowerCables(BlockPos pos, Level level) {
        Set<PowerCableMultiblock> cables = new HashSet<>();

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockEntity block = level.getBlockEntity(neighborPos); // Check neighbor's block entity

            // Check if the neighbor block entity exists and is a PortalPowerCableBlockEntity
            if (block instanceof PortalPowerCableBlockEntity) {
                // Check if the PowerCableMultiblock has been lazy-loaded/set on the BE
                PowerCableMultiblock cableMultiblock = ((PortalPowerCableBlockEntity) block).getMultiblock();
                if (cableMultiblock != null) {
                    cables.add(cableMultiblock);
                }
            }
        }

        return cables;
    }

    // FIXED: Always merge all multiblocks
    // FIXED: Always merge all multiblocks with proper block entity reference updates
    private static BatteryMultiblock mergeAllAdjacentMultiblocks(Set<BatteryMultiblock> multiblocksToMerge) {
        if (multiblocksToMerge.isEmpty()) {
            return null;
        }

        Iterator<BatteryMultiblock> iterator = multiblocksToMerge.iterator();
        BatteryMultiblock mainMultiblock = iterator.next();

        // If there's only one multiblock, just return it
        if (multiblocksToMerge.size() == 1) {
            Logger.sendMessage("Only one adjacent BatteryMultiblock " + mainMultiblock.multiblockId.toString().substring(0, 8) +
                    " found", true);
            return mainMultiblock;
        }

        // Multiple multiblocks - merge them all
        Logger.sendMessage("Merging " + multiblocksToMerge.size() + " BatteryMultiblocks into " +
                mainMultiblock.multiblockId.toString().substring(0, 8), true);

        int totalBatteriesBefore = mainMultiblock.batteryBlocks.size();
        int totalCableConnectionsBefore = mainMultiblock.connectedCableMultiblocksMap.size();
        int totalEnergyBefore = mainMultiblock.storedEnergy;

        // Store all block positions that need reference updates
        Set<BlockPos> allPositionsToUpdate = new HashSet<>();

        // Merge all other multiblocks into the main one
        while (iterator.hasNext()) {
            BatteryMultiblock otherMultiblock = iterator.next();
            allPositionsToUpdate.addAll(otherMultiblock.batteryBlocks);

            // Skip if it's the same multiblock
            if (otherMultiblock.multiblockId.equals(mainMultiblock.multiblockId)) {
                continue;
            }

            // Store stats for logging
            int batteriesToAdd = otherMultiblock.batteryBlocks.size();
            int cableConnectionsToAdd = otherMultiblock.connectedCableMultiblocksMap.size();
            int energyToAdd = otherMultiblock.storedEnergy;

            // Perform the merge
            mainMultiblock.mergeWith(otherMultiblock);

            // Remove the merged multiblock from manager
            PortalMultiblockManager.removeBatteryMultiblock(otherMultiblock);

            Logger.sendMessage("Merged BatteryMultiblock " + otherMultiblock.multiblockId.toString().substring(0, 8) +
                    " into " + mainMultiblock.multiblockId.toString().substring(0, 8) +
                    " (+" + batteriesToAdd + " batteries, +" + cableConnectionsToAdd + " cable connections, +" + energyToAdd + " FE)", true);
        }

        // CRITICAL: Update all block entity references for the merged batteries
        updateAllBlockEntityReferences(mainMultiblock, allPositionsToUpdate);

        // Log final results
        int totalBatteriesAdded = mainMultiblock.batteryBlocks.size() - totalBatteriesBefore;
        int totalCableConnectionsAdded = mainMultiblock.connectedCableMultiblocksMap.size() - totalCableConnectionsBefore;
        int totalEnergyAdded = mainMultiblock.storedEnergy - totalEnergyBefore;

        Logger.sendMessage("Merge complete: " + totalBatteriesAdded + " total batteries added, " +
                totalCableConnectionsAdded + " cable connections added, " + totalEnergyAdded + " FE transferred", true);

        return mainMultiblock;
    }

    // Add this helper method
    private static void updateAllBlockEntityReferences(BatteryMultiblock multiblock, Set<BlockPos> positionsToUpdate) {
        if (multiblock.level == null) return;

        int updatedCount = 0;
        for (BlockPos pos : positionsToUpdate) {
            BlockEntity blockEntity = multiblock.level.getBlockEntity(pos);
            if (blockEntity instanceof PortalBatteryBlockEntity) {
                ((PortalBatteryBlockEntity) blockEntity).setBatteryMultiblock(multiblock);
                updatedCount++;
            }
        }
        Logger.sendMessage("Updated " + updatedCount + " block entity references to BatteryMultiblock " +
                multiblock.multiblockId.toString().substring(0, 8), true);
    }
    // Improved battery removal with proper splitting
    public void handleBatteryBlockBreak(BlockPos removedPos) {
        if (this.batteryBlocks.remove(removedPos)) {
            // Log the removal (subtraction) (per user request)
            Logger.sendMessage(String.format("BatteryMultiblock: Subtracted block at [%d, %d, %d]. Total blocks: %d. Multiblock ID: %s",
                    removedPos.getX(), removedPos.getY(), removedPos.getZ(), this.batteryBlocks.size(), this.multiblockId.toString().substring(0, 8)), true);

            // Remove cable connections through this battery
            removeCableConnectionsForBattery(removedPos);

            // If no batteries left, destroy the multiblock
            if (batteryBlocks.isEmpty()) {
                PortalMultiblockManager.removeBatteryMultiblock(this);
                Logger.sendMessage("BatteryMultiblock " + multiblockId.toString().substring(0, 8) + " DESTROYED (no batteries remaining)", true);
                return;
            }

            // Check if the multiblock needs to split into multiple components
            List<Set<BlockPos>> disconnectedComponents = findDisconnectedComponents();

            if (disconnectedComponents.size() > 1) {
                Logger.sendMessage("BatteryMultiblock " + multiblockId.toString().substring(0, 8) +
                        " splitting into " + disconnectedComponents.size() + " components", true);
                splitIntoComponents(disconnectedComponents);
            } else {
                Logger.sendMessage("BatteryMultiblock " + multiblockId.toString().substring(0, 8) +
                        " now has " + batteryBlocks.size() + " batteries (no split needed)", true);
            }
        }
    }

    // Remove cable connections for a specific battery
    private void removeCableConnectionsForBattery(BlockPos batteryPos) {
        Set<UUID> cablesToUpdate = new HashSet<>();

        // Find all cables connected through this battery
        for (Map.Entry<UUID, Set<BlockPos>> entry : connectedCableMultiblocksMap.entrySet()) {
            UUID cableId = entry.getKey();
            Set<BlockPos> connectionPoints = entry.getValue();

            if (connectionPoints.contains(batteryPos)) {
                cablesToUpdate.add(cableId);
            }
        }

        // Remove this battery from all cable connections
        for (UUID cableId : cablesToUpdate) {
            Set<BlockPos> connectionPoints = connectedCableMultiblocksMap.get(cableId);
            if (connectionPoints != null) {
                connectionPoints.remove(batteryPos);

                if (connectionPoints.isEmpty()) {
                    connectedCableMultiblocksMap.remove(cableId);
                    Logger.sendMessage("Disconnected from PowerCableMultiblock " + cableId.toString().substring(0, 8) +
                            " (no more connection points)", true);
                } else {
                    Logger.sendMessage("Removed connection point to PowerCableMultiblock " + cableId.toString().substring(0, 8) +
                            " at " + batteryPos + " (" + connectionPoints.size() + " connection points remain)", true);
                }
            }
        }
    }

    // Find all disconnected components using flood fill
    private List<Set<BlockPos>> findDisconnectedComponents() {
        Set<BlockPos> visited = new HashSet<>();
        List<Set<BlockPos>> components = new ArrayList<>();

        for (BlockPos startPos : batteryBlocks) {
            if (!visited.contains(startPos)) {
                Set<BlockPos> component = new HashSet<>();
                floodFill(startPos, component, visited);
                components.add(component);
            }
        }

        return components;
    }

    // Flood fill algorithm to find connected components
    private void floodFill(BlockPos start, Set<BlockPos> component, Set<BlockPos> visited) {
        if (visited.contains(start)) return;

        visited.add(start);
        component.add(start);

        // Check all 6 directions (up, down, north, south, east, west)
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = start.relative(direction);
            if (batteryBlocks.contains(neighbor)) {
                floodFill(neighbor, component, visited);
            }
        }
    }

    // Split this multiblock into multiple components
    private void splitIntoComponents(List<Set<BlockPos>> components) {
        // The first component keeps the original multiblock
        Set<BlockPos> mainComponent = components.get(0);
        this.batteryBlocks.clear();
        this.batteryBlocks.addAll(mainComponent);

        // Distribute energy proportionally
        redistributeEnergyForSplit(components);

        // Create new multiblocks for the other components
        for (int i = 1; i < components.size(); i++) {
            Set<BlockPos> component = components.get(i);
            BatteryMultiblock newMultiblock = new BatteryMultiblock(UUID.randomUUID(), level);
            newMultiblock.batteryBlocks.addAll(component);

            // Distribute cable connections to the new multiblock
            distributeCableConnectionsForSplit(newMultiblock, component);

            Logger.sendMessage("Created new BatteryMultiblock " + newMultiblock.multiblockId.toString().substring(0, 8) +
                    " with " + component.size() + " batteries from split", true);
        }

        // CRITICAL: Update BlockEntity references for the blocks that stayed in the main multiblock
        updateAllBlockEntityReferences(this, mainComponent);

        Logger.sendMessage("BatteryMultiblock " + multiblockId.toString().substring(0, 8) +
                " now has " + batteryBlocks.size() + " batteries after split", true);
    }

    // Redistribute energy when splitting
    private void redistributeEnergyForSplit(List<Set<BlockPos>> components) {
        int totalBatteries = components.stream().mapToInt(Set::size).sum();
        if (totalBatteries == 0) return;

        int originalEnergy = this.storedEnergy;
        this.storedEnergy = (originalEnergy * this.batteryBlocks.size()) / totalBatteries;

        Logger.sendMessage("Redistributed energy: " + originalEnergy + " FE -> " + this.storedEnergy + " FE for main component", true);
    }

    // Distribute cable connections when splitting
    private void distributeCableConnectionsForSplit(BatteryMultiblock newMultiblock, Set<BlockPos> component) {
        Iterator<Map.Entry<UUID, Set<BlockPos>>> iterator = connectedCableMultiblocksMap.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Set<BlockPos>> entry = iterator.next();
            UUID cableId = entry.getKey();
            Set<BlockPos> connectionPoints = entry.getValue();

            // Find connection points that belong to the new component
            Set<BlockPos> pointsForNewMultiblock = new HashSet<>();
            for (BlockPos point : connectionPoints) {
                if (component.contains(point)) {
                    pointsForNewMultiblock.add(point);
                }
            }

            // If the new component has connection points, transfer them
            if (!pointsForNewMultiblock.isEmpty()) {
                // Remove from current multiblock
                connectionPoints.removeAll(pointsForNewMultiblock);

                // Add to new multiblock
                newMultiblock.connectedCableMultiblocksMap.put(cableId, pointsForNewMultiblock);

                // If no connection points left, remove the entry
                if (connectionPoints.isEmpty()) {
                    iterator.remove();
                }

                // CRITICAL: Update BlockEntity references for the blocks in the new multiblock
                updateAllBlockEntityReferences(newMultiblock, component);

                Logger.sendMessage("Transferred " + pointsForNewMultiblock.size() + " connection points to new multiblock for cable " +
                        cableId.toString().substring(0, 8), true);
            }
        }
    }

    // Add cable connection
    public void addCableConnection(PowerCableMultiblock cable, BlockPos batteryPos) {
        if (cable == null) return;

        UUID cableId = cable.id;

        // Check if this specific connection already exists
        if (connectedCableMultiblocksMap.containsKey(cableId) &&
                connectedCableMultiblocksMap.get(cableId).contains(batteryPos)) {
            // Connection already exists, avoid infinite recursion
            return;
        }

        boolean isNewConnection = !connectedCableMultiblocksMap.containsKey(cableId);

        if (!connectedCableMultiblocksMap.containsKey(cableId)) {
            connectedCableMultiblocksMap.put(cableId, new HashSet<>());
        }
        connectedCableMultiblocksMap.get(cableId).add(batteryPos);

        if (isNewConnection) {
            Logger.sendMessage("BatteryMultiblock " + multiblockId.toString().substring(0, 8) +
                    " CONNECTED to PowerCableMultiblock " + cableId.toString().substring(0, 8) +
                    " via battery at " + batteryPos, true);
            // Bidirectional connection - only if it's a new connection
            cable.addBatteryConnection(this, batteryPos);
        } else {
            Logger.sendMessage("BatteryMultiblock " + multiblockId.toString().substring(0, 8) +
                    " added connection to PowerCableMultiblock " + cableId.toString().substring(0, 8) +
                    " at " + batteryPos + " (" + connectedCableMultiblocksMap.get(cableId).size() + " total connections)", true);
        }
    }

    public void removeCableConnection(PowerCableMultiblock cable, BlockPos batteryPos) {
        if (cable == null) return;

        UUID cableId = cable.id;

        if (connectedCableMultiblocksMap.containsKey(cableId)) {
            Set<BlockPos> connectionPoints = connectedCableMultiblocksMap.get(cableId);
            boolean hadConnection = connectionPoints.contains(batteryPos);
            connectionPoints.remove(batteryPos);

            if (connectionPoints.isEmpty()) {
                connectedCableMultiblocksMap.remove(cableId);
                Logger.sendMessage("BatteryMultiblock " + multiblockId.toString().substring(0, 8) +
                        " DISCONNECTED from PowerCableMultiblock " + cableId.toString().substring(0, 8) +
                        " (no more connection points)", true);
            } else if (hadConnection) {
                Logger.sendMessage("BatteryMultiblock " + multiblockId.toString().substring(0, 8) +
                        " removed connection point to PowerCableMultiblock " + cableId.toString().substring(0, 8) +
                        " at " + batteryPos + " (" + connectionPoints.size() + " connection points remain)", true);
            }
        }
    }

    public void mergeWith(BatteryMultiblock other) {
        this.batteryBlocks.addAll(other.batteryBlocks);
        this.connectedPortalsId.addAll(other.connectedPortalsId);
        this.storedEnergy += other.storedEnergy;

        // Merge cable connections
        for (Map.Entry<UUID, Set<BlockPos>> entry : other.connectedCableMultiblocksMap.entrySet()) {
            UUID cableId = entry.getKey();
            Set<BlockPos> batteryPositions = entry.getValue();

            if (!this.connectedCableMultiblocksMap.containsKey(cableId)) {
                this.connectedCableMultiblocksMap.put(cableId, new HashSet<>());
            }
            this.connectedCableMultiblocksMap.get(cableId).addAll(batteryPositions);
        }
    }

    public void addBatteries(Set<BlockPos> batteryBlocks) {
        this.batteryBlocks.addAll(batteryBlocks);
    }

    public boolean connectToPortal(UUID portalId) {
        this.connectedPortalsId.add(portalId);
        return true;
    }

    public void disconnectFromPortal(UUID portalId) {
        this.connectedPortalsId.remove(portalId);
    }

    public int consumeEnergy(int amount) {
        int energyToConsume = Math.min(amount, storedEnergy);
        storedEnergy -= energyToConsume;
        return energyToConsume;
    }

    public int addEnergy(int amount) {
        int capacity = getMaxCapacity();
        int spaceAvailable = capacity - storedEnergy;
        int energyToAdd = Math.min(amount, spaceAvailable);
        storedEnergy += energyToAdd;
        return energyToAdd;
    }

    public int getStoredEnergy() {
        return storedEnergy;
    }

    public int getMaxCapacity() {
        return batteryBlocks.size() * capacityPerBattery;
    }

    public UUID getMultiblockId() {
        return multiblockId;
    }

    public Set<UUID> getConnectedPortalsId() {
        return connectedPortalsId;
    }

    public Set<BlockPos> getBatteryBlocks() {
        return Collections.unmodifiableSet(batteryBlocks);
    }

    public Level getLevel() {
        return level;
    }

    public boolean isEmpty() {
        return batteryBlocks.isEmpty();
    }

    public boolean isAdjacentTo(BlockPos pos) {
        for (BlockPos batteryPos : batteryBlocks) {
            if (batteryPos.distSqr(pos) <= 2.0) {
                return true;
            }
        }
        return false;
    }

    public static BatteryMultiblock recreateMultiblock(String batteryId, String[] portalIdsArr, BlockPos pos) {
        if (PortalMultiblockManager.batteryMultiblocks.containsKey(batteryId)) {
            BatteryMultiblock multiblock = PortalMultiblockManager.batteryMultiblocks.get(batteryId);
            multiblock.batteryBlocks.add(pos);
            return multiblock;
        } else {
            // Placeholder for Level
            return new BatteryMultiblock(UUID.fromString(batteryId), null);
        }
    }

    public Set<UUID> getConnectedCableIds() {
        return connectedCableMultiblocksMap.keySet();
    }

    public boolean isConnectedToCable(UUID cableId) {
        return connectedCableMultiblocksMap.containsKey(cableId) &&
                !connectedCableMultiblocksMap.get(cableId).isEmpty();
    }

    @Override
    public String toString() {
        return String.format("BatteryMultiblock[%s: %d batteries, %d/%d FE, Connected Cables: %d]",
                multiblockId.toString().substring(0, 8),
                batteryBlocks.size(), storedEnergy, getMaxCapacity(),
                connectedCableMultiblocksMap.size());
    }
}