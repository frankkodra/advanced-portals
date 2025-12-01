package portal_battery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import portal_multiblock.PortalMultiblockManager;
import portal_power_cable.PowerCableMultiblock;
import portal_power_cable.PortalPowerCableBlockEntity;
import advanced_portals.Logger;

import java.util.*;

public class BatteryMultiblock {
    private final UUID multiblockId;
    private final Set<BlockPos> batteryBlocks;
    public Set<UUID> connectedPortalsId;
    private Level level = null;

    // Track cable multiblock connections with specific battery blocks
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

    public static BatteryMultiblock getOrCreateBatteryMultiblock(UUID multiblockId, Level level) {
        if (multiblockId == null) {
            multiblockId = UUID.randomUUID();
            Logger.sendMessage(String.format("BatteryMultiblock: Generated new ID %s on load/tick", multiblockId.toString().substring(0, 8)), true);
        }

        BatteryMultiblock existing = PortalMultiblockManager.getBatteryMultiblock(multiblockId);
        if (existing != null) {
            Logger.sendMessage(String.format("BatteryMultiblock: Found existing multiblock with ID %s on load/tick.", multiblockId.toString().substring(0, 8)), true);
            return existing;
        }

        BatteryMultiblock newMultiblock = new BatteryMultiblock(multiblockId, level);
        Logger.sendMessage(String.format("BatteryMultiblock: Recreated multiblock with ID %s from saved data on load/tick.", multiblockId.toString().substring(0, 8)), true);
        return newMultiblock;
    }

    public static BatteryMultiblock addCreateOrMergeMultiblockForBlockPlaced(BlockPos pos, Level level) {
        Set<BatteryMultiblock> adjacentMultiblocks = findAdjacentBatteryMultiblocks(pos, level);
        Logger.sendMessage("Placing battery at " + pos + " - Found " + adjacentMultiblocks.size() + " adjacent BatteryMultiblocks", true);

        BatteryMultiblock resultMultiblock;

        if (adjacentMultiblocks.isEmpty()) {
            resultMultiblock = new BatteryMultiblock(UUID.randomUUID(), level);
            resultMultiblock.addBattery(pos);
            Logger.sendMessage("New BatteryMultiblock " + resultMultiblock.multiblockId.toString().substring(0, 8) + " created with 1 battery at " + pos, true);
        } else {
            resultMultiblock = mergeAllAdjacentMultiblocks(adjacentMultiblocks);
            resultMultiblock.addBattery(pos);
            Logger.sendMessage("BatteryMultiblock " + resultMultiblock.multiblockId.toString().substring(0, 8) + " added battery at " + pos + " (total: " + resultMultiblock.batteryBlocks.size() + " batteries)", true);
        }

        updateBlockEntityMultiblockReference(pos, level, resultMultiblock);
        return resultMultiblock;
    }

    public void addBattery(BlockPos pos) {
        batteryBlocks.add(pos);
    }

    private static Set<BatteryMultiblock> findAdjacentBatteryMultiblocks(BlockPos pos, Level level) {
        Set<BatteryMultiblock> multiblocks = new HashSet<>();
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            Block block = level.getBlockState(neighborPos).getBlock();
            if (block instanceof PortalBatteryBlock) {
                BatteryMultiblock neighborMultiblock = getMultiblockFromBlockEntity(neighborPos, level);
                if (neighborMultiblock != null) {
                    multiblocks.add(neighborMultiblock);
                    Logger.sendMessage("Found adjacent battery at " + neighborPos + " belonging to BatteryMultiblock " + neighborMultiblock.multiblockId.toString().substring(0, 8), true);
                }
            }
        }
        return multiblocks;
    }

    public static BatteryMultiblock getMultiblockFromBlockEntity(BlockPos pos, Level level) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof PortalBatteryBlockEntity) {
            return ((PortalBatteryBlockEntity) blockEntity).getBatteryMultiblock();
        }
        return null;
    }

    private static void updateBlockEntityMultiblockReference(BlockPos pos, Level level, BatteryMultiblock multiblock) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof PortalBatteryBlockEntity) {
            ((PortalBatteryBlockEntity) blockEntity).setBatteryMultiblock(multiblock);
            Logger.sendMessage("Updated block entity reference at " + pos + " to BatteryMultiblock " + multiblock.multiblockId.toString().substring(0, 8), true);
        }
    }

    private static BatteryMultiblock mergeAllAdjacentMultiblocks(Set<BatteryMultiblock> multiblocksToMerge) {
        if (multiblocksToMerge.isEmpty()) return null;
        Iterator<BatteryMultiblock> iterator = multiblocksToMerge.iterator();
        BatteryMultiblock mainMultiblock = iterator.next();

        if (multiblocksToMerge.size() == 1) {
            Logger.sendMessage("Only one adjacent BatteryMultiblock " + mainMultiblock.multiblockId.toString().substring(0, 8) + " found", true);
            return mainMultiblock;
        }

        Logger.sendMessage("Merging " + multiblocksToMerge.size() + " BatteryMultiblocks into " + mainMultiblock.multiblockId.toString().substring(0, 8), true);
        int totalBatteriesBefore = mainMultiblock.batteryBlocks.size();
        int totalEnergyBefore = mainMultiblock.storedEnergy;

        Set<BlockPos> allPositionsToUpdate = new HashSet<>();
        while (iterator.hasNext()) {
            BatteryMultiblock otherMultiblock = iterator.next();
            allPositionsToUpdate.addAll(otherMultiblock.batteryBlocks);

            if (otherMultiblock.multiblockId.equals(mainMultiblock.multiblockId)) continue;

            int batteriesToAdd = otherMultiblock.batteryBlocks.size();
            int energyToAdd = otherMultiblock.storedEnergy;
            mainMultiblock.mergeWith(otherMultiblock);
            PortalMultiblockManager.removeBatteryMultiblock(otherMultiblock);

            Logger.sendMessage("Merged BatteryMultiblock " + otherMultiblock.multiblockId.toString().substring(0, 8) + " into " + mainMultiblock.multiblockId.toString().substring(0, 8) + " (+" + batteriesToAdd + " batteries, +" + energyToAdd + " FE)", true);
        }

        updateAllBlockEntityReferences(mainMultiblock, allPositionsToUpdate);
        int totalBatteriesAdded = mainMultiblock.batteryBlocks.size() - totalBatteriesBefore;
        int totalEnergyAdded = mainMultiblock.storedEnergy - totalEnergyBefore;
        Logger.sendMessage("Merge complete: " + totalBatteriesAdded + " total batteries added, " + totalEnergyAdded + " FE transferred", true);

        return mainMultiblock;
    }

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
        Logger.sendMessage("Updated " + updatedCount + " block entity references to BatteryMultiblock " + multiblock.multiblockId.toString().substring(0, 8), true);
    }

    public void handleBatteryBlockBreak(BlockPos removedPos) {
        Logger.sendMessage("BatteryMultiblock " + multiblockId.toString().substring(0, 8) + " handling break at " + removedPos, true);

        if (this.batteryBlocks.remove(removedPos)) {
            Logger.sendMessage(String.format("BatteryMultiblock: Subtracted block at [%d, %d, %d]. Total blocks: %d. Multiblock ID: %s",
                    removedPos.getX(), removedPos.getY(), removedPos.getZ(), this.batteryBlocks.size(), this.multiblockId.toString().substring(0, 8)), true);

            // SIMPLE: Remove cable connections for this battery
            removeCableConnectionsForBattery(removedPos);

            if (batteryBlocks.isEmpty()) {
                PortalMultiblockManager.removeBatteryMultiblock(this);
                Logger.sendMessage("BatteryMultiblock " + multiblockId.toString().substring(0, 8) + " DESTROYED (no batteries remaining)", true);
                return;
            }

            // Check if the multiblock needs to split into multiple components
            List<Set<BlockPos>> disconnectedComponents = findDisconnectedComponents();
            if (disconnectedComponents.size() > 1) {
                Logger.sendMessage("BatteryMultiblock " + multiblockId.toString().substring(0, 8) + " splitting into " + disconnectedComponents.size() + " components", true);
                splitIntoComponents(disconnectedComponents);
            } else {
                Logger.sendMessage("BatteryMultiblock " + multiblockId.toString().substring(0, 8) + " now has " + batteryBlocks.size() + " batteries (no split needed)", true);
            }
        }
    }

    // SIMPLE: Just check all neighboring cables and remove connections
    private void removeCableConnectionsForBattery(BlockPos batteryPos) {
        Logger.sendMessage("Removing cable connections for battery " + batteryPos, true);
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = batteryPos.relative(direction);
            BlockEntity blockEntity = level.getBlockEntity(neighborPos);

            if (blockEntity instanceof PortalPowerCableBlockEntity) {
                PowerCableMultiblock cableMultiblock = ((PortalPowerCableBlockEntity) blockEntity).getMultiblock();
                if (cableMultiblock != null) {
                    Logger.sendMessage("Found adjacent cable at " + neighborPos + " - removing connection", true);
                    cableMultiblock.removeBatteryConnection(this, neighborPos);
                }
            }
        }
    }

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

    private void floodFill(BlockPos start, Set<BlockPos> component, Set<BlockPos> visited) {
        if (visited.contains(start)) return;
        visited.add(start);
        component.add(start);
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = start.relative(direction);
            if (batteryBlocks.contains(neighbor)) {
                floodFill(neighbor, component, visited);
            }
        }
    }

    private void splitIntoComponents(List<Set<BlockPos>> components) {
        // Store the original multiblock ID for connection cleanup
        UUID originalMultiblockId = this.multiblockId;

        // First component keeps the original multiblock
        Set<BlockPos> mainComponent = components.get(0);
        this.batteryBlocks.clear();
        this.batteryBlocks.addAll(mainComponent);
        redistributeEnergyForSplit(components);

        // Create new multiblocks for other components
        for (int i = 1; i < components.size(); i++) {
            Set<BlockPos> component = components.get(i);
            BatteryMultiblock newMultiblock = new BatteryMultiblock(UUID.randomUUID(), level);
            newMultiblock.batteryBlocks.addAll(component);

            Logger.sendMessage("Created new BatteryMultiblock " + newMultiblock.multiblockId.toString().substring(0, 8) + " with " + component.size() + " batteries from split", true);

            // CRITICAL: Update cable connections for the new multiblock
            updateCableConnectionsForNewMultiblock(newMultiblock, component, originalMultiblockId);

            // Update all block entity references for the new multiblock
            for (BlockPos pos : component) {
                updateBlockEntityMultiblockReference(pos, level, newMultiblock);
            }
        }

        // CRITICAL: Update cable connections for the main multiblock (remove connections to batteries that are no longer in this multiblock)
        updateCableConnectionsForMainMultiblock(mainComponent, originalMultiblockId);

        updateAllBlockEntityReferences(this, mainComponent);
        Logger.sendMessage("BatteryMultiblock " + multiblockId.toString().substring(0, 8) + " now has " + batteryBlocks.size() + " batteries after split", true);
    }

    // CRITICAL: Update cable connections for the main multiblock after split
    private void updateCableConnectionsForMainMultiblock(Set<BlockPos> mainComponent, UUID originalMultiblockId) {
        Logger.sendMessage("Updating cable connections for main multiblock after split", true);

        // For each cable that was connected to the original multiblock
        for (Map.Entry<UUID, Set<BlockPos>> entry : new HashMap<>(connectedCableMultiblocksMap).entrySet()) {
            UUID cableId = entry.getKey();
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

            // If no connection points remain, remove the cable entirely
            if (connectionPoints.isEmpty()) {
                connectedCableMultiblocksMap.remove(cableId);
                Logger.sendMessage("Removed cable " + cableId.toString().substring(0, 8) + " from main multiblock - no valid connections", true);
            } else if (!pointsToRemove.isEmpty()) {
                Logger.sendMessage("Removed " + pointsToRemove.size() + " invalid connection points from cable " + cableId.toString().substring(0, 8), true);
            }

            // Notify the cable multiblock about the removed connections
            if (!pointsToRemove.isEmpty()) {
                PowerCableMultiblock cable = PortalMultiblockManager.getPowerCableMultiblock(cableId);
                if (cable != null) {
                    for (BlockPos removedPoint : pointsToRemove) {
                        cable.removeBatteryConnection(this, removedPoint);
                    }
                }
            }
        }
    }

    // CRITICAL: Set up cable connections for a new multiblock after split
    private void updateCableConnectionsForNewMultiblock(BatteryMultiblock newMultiblock, Set<BlockPos> component, UUID originalMultiblockId) {
        Logger.sendMessage("Setting up cable connections for new multiblock " + newMultiblock.multiblockId.toString().substring(0, 8), true);

        // Find all cables that were connected to batteries in this component through the original multiblock
        for (Map.Entry<UUID, Set<BlockPos>> entry : connectedCableMultiblocksMap.entrySet()) {
            UUID cableId = entry.getKey();
            Set<BlockPos> connectionPoints = entry.getValue();

            // Find connection points that belong to this new component
            Set<BlockPos> pointsForNewMultiblock = new HashSet<>();
            for (BlockPos connectionPoint : connectionPoints) {
                if (component.contains(connectionPoint)) {
                    pointsForNewMultiblock.add(connectionPoint);
                }
            }

            // If this cable has connections to batteries in the new component, transfer them
            if (!pointsForNewMultiblock.isEmpty()) {
                Logger.sendMessage("Transferring " + pointsForNewMultiblock.size() + " connection points to new multiblock for cable " + cableId.toString().substring(0, 8), true);

                // Add connections to the new multiblock
                for (BlockPos connectionPoint : pointsForNewMultiblock) {
                    newMultiblock.addCableConnectionFromCable(cableId, connectionPoint);
                }

                // Notify the cable multiblock about the new connections
                PowerCableMultiblock cable = PortalMultiblockManager.getPowerCableMultiblock(cableId);
                if (cable != null) {
                    // The cable will update its connection map to point to the new multiblock ID
                    for (BlockPos connectionPoint : pointsForNewMultiblock) {
                        // Remove the old connection (to original multiblock)
                        cable.removeBatteryConnection(this, connectionPoint);
                        // Add the new connection (to new multiblock)
                        cable.addBatteryConnection(newMultiblock, connectionPoint);
                    }
                }
            }
        }
    }

    private void redistributeEnergyForSplit(List<Set<BlockPos>> components) {
        int totalBatteries = components.stream().mapToInt(Set::size).sum();
        if (totalBatteries == 0) return;
        int originalEnergy = this.storedEnergy;
        this.storedEnergy = (originalEnergy * this.batteryBlocks.size()) / totalBatteries;
        Logger.sendMessage("Redistributed energy: " + originalEnergy + " FE -> " + this.storedEnergy + " FE for main component", true);
    }

    public void addCableConnectionFromCable(UUID cableId, BlockPos batteryPos) {
        Logger.sendMessage("BatteryMultiblock " + multiblockId.toString().substring(0, 8) + " connected to PowerCableMultiblock " + cableId.toString().substring(0, 8) + " at " + batteryPos, true);
        if (!connectedCableMultiblocksMap.containsKey(cableId)) {
            connectedCableMultiblocksMap.put(cableId, new HashSet<>());
        }
        connectedCableMultiblocksMap.get(cableId).add(batteryPos);
    }

    public void removeCableConnectionFromCable(UUID cableId, BlockPos batteryPos) {
        Logger.sendMessage("BatteryMultiblock " + multiblockId.toString().substring(0, 8) + " disconnected from PowerCableMultiblock " + cableId.toString().substring(0, 8) + " at " + batteryPos, true);
        if (connectedCableMultiblocksMap.containsKey(cableId)) {
            Set<BlockPos> connectionPoints = connectedCableMultiblocksMap.get(cableId);
            connectionPoints.remove(batteryPos);
            if (connectionPoints.isEmpty()) {
                connectedCableMultiblocksMap.remove(cableId);
            }
        }
    }

    public void mergeWith(BatteryMultiblock other) {
        Logger.sendMessage("BatteryMultiblock " + multiblockId.toString().substring(0, 8) + " merging with " + other.multiblockId.toString().substring(0, 8), true);

        // Merge basic properties
        this.batteryBlocks.addAll(other.batteryBlocks);
        this.connectedPortalsId.addAll(other.connectedPortalsId);
        this.storedEnergy += other.storedEnergy;

        // CRITICAL: Merge cable connections
        for (Map.Entry<UUID, Set<BlockPos>> entry : other.connectedCableMultiblocksMap.entrySet()) {
            UUID cableId = entry.getKey();
            Set<BlockPos> batteryPositions = entry.getValue();

            if (!this.connectedCableMultiblocksMap.containsKey(cableId)) {
                this.connectedCableMultiblocksMap.put(cableId, new HashSet<>());
            }
            this.connectedCableMultiblocksMap.get(cableId).addAll(batteryPositions);

            Logger.sendMessage("Merged " + batteryPositions.size() + " connection points from cable " + cableId.toString().substring(0, 8), true);

            // CRITICAL: Notify the cable multiblock to update its connection from the old multiblock ID to the new one
            PowerCableMultiblock cable = PortalMultiblockManager.getPowerCableMultiblock(cableId);
            if (cable != null) {
                for (BlockPos batteryPos : batteryPositions) {
                    // Remove connection to the old multiblock
                    cable.removeBatteryConnection(other, batteryPos);
                    // Add connection to the new merged multiblock
                    cable.addBatteryConnection(this, batteryPos);
                }
                Logger.sendMessage("Updated cable " + cableId.toString().substring(0, 8) + " connections from old multiblock to merged multiblock", true);
            }
        }

        Logger.sendMessage("BatteryMultiblock " + multiblockId.toString().substring(0, 8) + " merged with " + other.multiblockId.toString().substring(0, 8) + " - added " + other.batteryBlocks.size() + " batteries, " + other.connectedCableMultiblocksMap.size() + " cable connections, " + other.storedEnergy + " FE", true);
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

    public Set<UUID> getConnectedCableIds() {
        return connectedCableMultiblocksMap.keySet();
    }

    public boolean isConnectedToCable(UUID cableId) {
        return connectedCableMultiblocksMap.containsKey(cableId) && !connectedCableMultiblocksMap.get(cableId).isEmpty();
    }

    @Override
    public String toString() {
        return String.format("BatteryMultiblock[%s: %d batteries, %d/%d FE]", multiblockId.toString().substring(0, 8), batteryBlocks.size(), storedEnergy, getMaxCapacity());
    }
}