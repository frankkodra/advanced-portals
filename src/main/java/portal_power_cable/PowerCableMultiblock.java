package portal_power_cable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import portal_battery.BatteryMultiblock;
import portal_block.PortalBlock;
import portal_block.PortalBlockEntity;
import portal_multiblock.PortalMultiblockManager;
import portal_multiblock.PortalStructure;
import advanced_portals.Logger;

import java.util.*;

public class PowerCableMultiblock {
    public Set<PortalPowerCableBlock> connectedCables;
    public Set<PortalStructure> connectedPortalStructures;
    public Set<BatteryMultiblock> connectedBatteryMultiblocks;
    // Track battery multiblock connections with specific cable blocks
    public Map<UUID, Set<BlockPos>> connectedBatteryMultiblocksMap;

    public UUID id;
    private Level level;

    // Track our cable block positions
    private Set<BlockPos> cableBlockPositions;

    public PowerCableMultiblock(UUID id, Level level) {
        this.id = id;
        this.level = level;
        this.connectedCables = new HashSet<>();
        this.connectedPortalStructures = new HashSet<>();
        this.connectedBatteryMultiblocks = new HashSet<>();
        this.connectedBatteryMultiblocksMap = new HashMap<>();
        this.cableBlockPositions = new HashSet<>();

        PortalMultiblockManager.addPowerCableMultiblock(this);
        Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) + " created", true);
    }
    /// constructor for loading saved multiblocks


    // Static creation method with lazy initialization
    public static PowerCableMultiblock getOrCreatePowerCableMultiblock(UUID multiblockId,Level level) {
        PowerCableMultiblock existing = PortalMultiblockManager.getPowerCableMultiblock(multiblockId);
        if (existing != null) {
            Logger.sendMessage("mulitblock: "+multiblockId+" already exists returning existing mulitblock",true);
            return existing;
        } else {
            Logger.sendMessage("mulitblock: "+multiblockId+" doesnt exists returning new mulitblock with that id",true);

            PowerCableMultiblock newMultiblock = new PowerCableMultiblock(multiblockId,level);
            PortalMultiblockManager.addPowerCableMultiblock(newMultiblock);
            return newMultiblock;
        }
    }

    // FIXED: Get multiblock from block entity instead of block
    public static PowerCableMultiblock addCreateOrMergeForBlock(BlockPos pos, Level level) {
        Set<PowerCableMultiblock> adjacentMultiblocks = findAdjacentPowerCableMultiblocks(pos, level);
        Set<PortalStructure> adjacentPortals = findAdjacentPortalStructures(pos, level);
        Set<BatteryMultiblock> adjacentBatteries = findAdjacentBatteries(pos, level);

        Logger.sendMessage("Placing cable at " + pos + " - Found " + adjacentMultiblocks.size() + " adjacent PowerCableMultiblocks", true);

        PowerCableMultiblock resultMultiblock;

        if (adjacentMultiblocks.isEmpty()) {
            // No adjacent cables - create new multiblock
            resultMultiblock = new PowerCableMultiblock(UUID.randomUUID(), level);
            resultMultiblock.addCablePosition(pos);
            Logger.sendMessage("New PowerCableMultiblock " + resultMultiblock.id.toString().substring(0, 8) +
                    " created with 1 cable at " + pos, true);
        }
        else {
            // Always merge all adjacent multiblocks
            resultMultiblock = mergeAllAdjacentMultiblocks(adjacentMultiblocks);
            resultMultiblock.addCablePosition(pos);
            Logger.sendMessage("PowerCableMultiblock " + resultMultiblock.id.toString().substring(0, 8) +
                    " added cable at " + pos + " (total: " + resultMultiblock.cableBlockPositions.size() + " cables)", true);
        }

        // FIXED: Update the block entity reference instead of block reference
        updateBlockEntityMultiblockReference(pos, level, resultMultiblock);

        // Connect to adjacent portal structures
        for (PortalStructure portal : adjacentPortals) {
            resultMultiblock.connectedPortalStructures.add(portal);
            portal.powerCablesMultiblocks.add(resultMultiblock);
            Logger.sendMessage("PowerCableMultiblock " + resultMultiblock.id.toString().substring(0, 8) +
                    " connected to PortalStructure", true);
        }

        // Establish connections to nearby batteries
        for (BatteryMultiblock battery : adjacentBatteries) {
            resultMultiblock.addBatteryConnection(battery, pos);
        }

        return resultMultiblock;
    }

    // FIXED: Get multiblock from block entity instead of block
    private static Set<PowerCableMultiblock> findAdjacentPowerCableMultiblocks(BlockPos pos, Level level) {
        Set<PowerCableMultiblock> multiblocks = new HashSet<>();

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            Block block = level.getBlockState(neighborPos).getBlock();

            if (block instanceof PortalPowerCableBlock) {
                // FIXED: Get multiblock from block entity instead of block field
                PowerCableMultiblock neighborMultiblock = getMultiblockFromBlockEntity(neighborPos, level);
                if (neighborMultiblock != null) {
                    multiblocks.add(neighborMultiblock);
                    Logger.sendMessage("Found adjacent cable at " + neighborPos + " belonging to PowerCableMultiblock " +
                            neighborMultiblock.id.toString().substring(0, 8), true);
                } else {
                    Logger.sendMessage("WARNING: Cable at " + neighborPos + " has null multiblock reference in block entity!", true);
                }
            }
        }

        return multiblocks;
    }

    // FIXED: Helper method to get multiblock from block entity
    public static PowerCableMultiblock getMultiblockFromBlockEntity(BlockPos pos, Level level) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof PortalPowerCableBlockEntity) {
            return ((PortalPowerCableBlockEntity) blockEntity).getMultiblock();
        }
        return null;
    }

    // FIXED: Helper method to update block entity reference
    private static void updateBlockEntityMultiblockReference(BlockPos pos, Level level, PowerCableMultiblock multiblock) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof PortalPowerCableBlockEntity) {
            ((PortalPowerCableBlockEntity) blockEntity).setMultiblock(multiblock);
            Logger.sendMessage("Updated block entity reference at " + pos + " to PowerCableMultiblock " +
                    multiblock.id.toString().substring(0, 8), true);
        } else {
            Logger.sendMessage("ERROR: No block entity found at " + pos + " to update multiblock reference!", true);
        }
    }

    // Find all adjacent portal structures
    private static Set<PortalStructure> findAdjacentPortalStructures(BlockPos pos, Level level) {
        Set<PortalStructure> portals = new HashSet<>();

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockEntity block = level.getBlockEntity(neighborPos);

            if (block instanceof PortalBlockEntity) {
                PortalStructure portalStructure = ((PortalBlockEntity) block).portalStructure;
                if (portalStructure != null) {
                    portals.add(portalStructure);
                }
            }
        }

        return portals;
    }

    // Find all adjacent batteries
    private static Set<BatteryMultiblock> findAdjacentBatteries(BlockPos pos, Level level) {
        Set<BatteryMultiblock> batteries = new HashSet<>();

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            Block block = level.getBlockState(neighborPos).getBlock();

            if (block instanceof portal_battery.PortalBatteryBlock) {
                // FIXED: Get multiblock from block entity instead of block field
                BatteryMultiblock batteryMultiblock = portal_battery.BatteryMultiblock.getMultiblockFromBlockEntity(neighborPos, level);
                if (batteryMultiblock != null) {
                    batteries.add(batteryMultiblock);
                }
            }
        }

        return batteries;
    }

    // FIXED: Always merge all multiblocks
    private static PowerCableMultiblock mergeAllAdjacentMultiblocks(Set<PowerCableMultiblock> multiblocksToMerge) {
        if (multiblocksToMerge.isEmpty()) {
            return null;
        }

        Iterator<PowerCableMultiblock> iterator = multiblocksToMerge.iterator();
        PowerCableMultiblock mainMultiblock = iterator.next();

        // If there's only one multiblock, just return it
        if (multiblocksToMerge.size() == 1) {
            Logger.sendMessage("Only one adjacent PowerCableMultiblock " + mainMultiblock.id.toString().substring(0, 8) +
                    " found", true);
            return mainMultiblock;
        }

        // Multiple multiblocks - merge them all
        Logger.sendMessage("Merging " + multiblocksToMerge.size() + " PowerCableMultiblocks into " +
                mainMultiblock.id.toString().substring(0, 8), true);

        int totalCablesBefore = mainMultiblock.cableBlockPositions.size();
        int totalBatteryConnectionsBefore = mainMultiblock.connectedBatteryMultiblocksMap.size();
        int totalPortalConnectionsBefore = mainMultiblock.connectedPortalStructures.size();

        // Store all block positions that need reference updates
        Set<BlockPos> allPositionsToUpdate = new HashSet<>();

        // Merge all other multiblocks into the main one
        while (iterator.hasNext()) {
            PowerCableMultiblock otherMultiblock = iterator.next();
            allPositionsToUpdate.addAll(otherMultiblock.cableBlockPositions);

            // Skip if it's the same multiblock
            if (otherMultiblock.id.equals(mainMultiblock.id)) {
                continue;
            }

            // Store stats for logging
            int cablesToAdd = otherMultiblock.cableBlockPositions.size();
            int batteryConnectionsToAdd = otherMultiblock.connectedBatteryMultiblocksMap.size();
            int portalConnectionsToAdd = otherMultiblock.connectedPortalStructures.size();

            // Collect positions that need reference updates

            // Perform the merge
            mainMultiblock.mergeWith(otherMultiblock);

            // Remove the merged multiblock from manager
            PortalMultiblockManager.removePowerCableMultiblock(otherMultiblock);

            Logger.sendMessage("Merged PowerCableMultiblock " + otherMultiblock.id.toString().substring(0, 8) +
                    " into " + mainMultiblock.id.toString().substring(0, 8) +
                    " (+" + cablesToAdd + " cables, +" + batteryConnectionsToAdd + " battery connections, +" + portalConnectionsToAdd + " portal connections)", true);
        }

        // CRITICAL: Update all block entity references for the merged cables
        updateAllBlockEntityReferences(mainMultiblock, allPositionsToUpdate);

        // Log final results
        int totalCablesAdded = mainMultiblock.cableBlockPositions.size() - totalCablesBefore;
        int totalBatteryConnectionsAdded = mainMultiblock.connectedBatteryMultiblocksMap.size() - totalBatteryConnectionsBefore;
        int totalPortalConnectionsAdded = mainMultiblock.connectedPortalStructures.size() - totalPortalConnectionsBefore;

        Logger.sendMessage("Merge complete: " + totalCablesAdded + " total cables added, " +
                totalBatteryConnectionsAdded + " battery connections added, " +
                totalPortalConnectionsAdded + " portal connections added", true);

        return mainMultiblock;
    }

    // Add this helper method
    private static void updateAllBlockEntityReferences(PowerCableMultiblock multiblock, Set<BlockPos> positionsToUpdate) {
        if (multiblock.level == null) return;

        int updatedCount = 0;
        for (BlockPos pos : positionsToUpdate) {
            BlockEntity blockEntity = multiblock.level.getBlockEntity(pos);
            if (blockEntity instanceof PortalPowerCableBlockEntity) {
                ((PortalPowerCableBlockEntity) blockEntity).setMultiblock(multiblock);
                updatedCount++;
            }
        }
        Logger.sendMessage("Updated " + updatedCount + " block entity references to PowerCableMultiblock " +
                multiblock.id.toString().substring(0, 8), true);
    }

    // Improved cable removal with proper splitting
    public void handleCableBlockBreak(BlockPos removedPos) {
        Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) +
                " removing cable at " + removedPos + " (currently " + cableBlockPositions.size() + " cables)", true);

        // Remove from cable blocks
        cableBlockPositions.remove(removedPos);

        // Remove battery connections through this cable
        removeBatteryConnectionsForCable(removedPos);

        // Remove from connected cables set
        connectedCables.removeIf(cable -> {
            // This will be properly reconnected during load cycle
            return true;
        });

        // If no cables left, destroy the multiblock
        if (cableBlockPositions.isEmpty()) {
            PortalMultiblockManager.removePowerCableMultiblock(this);
            Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) + " DESTROYED (no cables remaining)", true);
            return;
        }

        // Check if the multiblock needs to split into multiple components
        List<Set<BlockPos>> disconnectedComponents = findDisconnectedComponents();

        if (disconnectedComponents.size() > 1) {
            Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) +
                    " splitting into " + disconnectedComponents.size() + " components", true);
            splitIntoComponents(disconnectedComponents);
        } else {
            Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) +
                    " now has " + cableBlockPositions.size() + " cables (no split needed)", true);
        }
    }

    // Remove battery connections for a specific cable
    private void removeBatteryConnectionsForCable(BlockPos cablePos) {
        Set<UUID> batteriesToUpdate = new HashSet<>();

        // Find all batteries connected through this cable
        for (Map.Entry<UUID, Set<BlockPos>> entry : connectedBatteryMultiblocksMap.entrySet()) {
            UUID batteryId = entry.getKey();
            Set<BlockPos> connectionPoints = entry.getValue();

            if (connectionPoints.contains(cablePos)) {
                batteriesToUpdate.add(batteryId);
            }
        }

        // Remove this cable from all battery connections
        for (UUID batteryId : batteriesToUpdate) {
            Set<BlockPos> connectionPoints = connectedBatteryMultiblocksMap.get(batteryId);
            if (connectionPoints != null) {
                connectionPoints.remove(cablePos);

                if (connectionPoints.isEmpty()) {
                    connectedBatteryMultiblocksMap.remove(batteryId);
                    connectedBatteryMultiblocks.removeIf(battery -> battery.getMultiblockId().equals(batteryId));
                    Logger.sendMessage("Disconnected from BatteryMultiblock " + batteryId.toString().substring(0, 8) +
                            " (no more connection points)", true);
                } else {
                    Logger.sendMessage("Removed connection point to BatteryMultiblock " + batteryId.toString().substring(0, 8) +
                            " at " + cablePos + " (" + connectionPoints.size() + " connection points remain)", true);
                }
            }
        }
    }

    // Find all disconnected components using flood fill
    private List<Set<BlockPos>> findDisconnectedComponents() {
        Set<BlockPos> visited = new HashSet<>();
        List<Set<BlockPos>> components = new ArrayList<>();

        for (BlockPos startPos : cableBlockPositions) {
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
            if (cableBlockPositions.contains(neighbor)) {
                floodFill(neighbor, component, visited);
            }
        }
    }

    // Split this multiblock into multiple components
    private void splitIntoComponents(List<Set<BlockPos>> components) {
        // The first component keeps the original multiblock
        Set<BlockPos> mainComponent = components.get(0);
        this.cableBlockPositions.clear();
        this.cableBlockPositions.addAll(mainComponent);

        // Create new multiblocks for the other components
        for (int i = 1; i < components.size(); i++) {
            Set<BlockPos> component = components.get(i);
            PowerCableMultiblock newMultiblock = new PowerCableMultiblock(UUID.randomUUID(), level);
            newMultiblock.cableBlockPositions.addAll(component);

            // Distribute battery connections to the new multiblock
            distributeBatteryConnectionsForSplit(newMultiblock, component);

            // Distribute portal connections to the new multiblock
            distributePortalConnectionsForSplit(newMultiblock, component);

            Logger.sendMessage("Created new PowerCableMultiblock " + newMultiblock.id.toString().substring(0, 8) +
                    " with " + component.size() + " cables from split", true);
        }

        Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) +
                " now has " + cableBlockPositions.size() + " cables after split", true);
    }

    // Distribute battery connections when splitting
    private void distributeBatteryConnectionsForSplit(PowerCableMultiblock newMultiblock, Set<BlockPos> component) {
        Iterator<Map.Entry<UUID, Set<BlockPos>>> iterator = connectedBatteryMultiblocksMap.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Set<BlockPos>> entry = iterator.next();
            UUID batteryId = entry.getKey();
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
                newMultiblock.connectedBatteryMultiblocksMap.put(batteryId, pointsForNewMultiblock);

                // Add to simple set for backward compatibility
                BatteryMultiblock battery = getBatteryById(batteryId);
                if (battery != null) {
                    newMultiblock.connectedBatteryMultiblocks.add(battery);
                }

                // If no connection points left, remove the entry
                if (connectionPoints.isEmpty()) {
                    iterator.remove();
                    connectedBatteryMultiblocks.removeIf(battery1 -> battery.getMultiblockId().equals(batteryId));
                }

                Logger.sendMessage("Transferred " + pointsForNewMultiblock.size() + " battery connection points to new multiblock for battery " +
                        batteryId.toString().substring(0, 8), true);
            }
        }
    }

    // Distribute portal connections when splitting
    private void distributePortalConnectionsForSplit(PowerCableMultiblock newMultiblock, Set<BlockPos> component) {
        // For portal connections, we need to check which portals are adjacent to the new component
        Set<PortalStructure> portalsForNewMultiblock = new HashSet<>();

        for (BlockPos cablePos : component) {
            Set<PortalStructure> adjacentPortals = findAdjacentPortalStructures(cablePos, level);
            portalsForNewMultiblock.addAll(adjacentPortals);
        }

        // Transfer portal connections
        for (PortalStructure portal : portalsForNewMultiblock) {
            newMultiblock.connectedPortalStructures.add(portal);
            portal.powerCablesMultiblocks.add(newMultiblock);
            Logger.sendMessage("Transferred portal connection to new multiblock for portal " +
                    portal.getPortalId().toString().substring(0, 8), true);
        }
    }

    // Helper method to get battery by ID
    private BatteryMultiblock getBatteryById(UUID batteryId) {
        for (BatteryMultiblock battery : connectedBatteryMultiblocks) {
            if (battery.getMultiblockId().equals(batteryId)) {
                return battery;
            }
        }
        return null;
    }

    // Add cable position (used when blocks load or place)
    public void addCablePosition(BlockPos pos) {
        cableBlockPositions.add(pos);

        // Reconnect block reference
        if (level != null) {
            Block block = level.getBlockState(pos).getBlock();
            if (block instanceof PortalPowerCableBlock) {
                // FIXED: Update block entity reference instead of block field
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity instanceof PortalPowerCableBlockEntity) {
                    ((PortalPowerCableBlockEntity) blockEntity).setMultiblock(this);
                }
                connectedCables.add((PortalPowerCableBlock) block);
            }
        }

        Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) +
                " added cable at " + pos + " (total: " + cableBlockPositions.size() + " cables)", true);
    }

    // Add battery connection
    // Add battery connection
    public void addBatteryConnection(BatteryMultiblock battery, BlockPos cablePos) {
        if (battery == null) return;

        UUID batteryId = battery.getMultiblockId();

        // Check if this specific connection already exists
        if (connectedBatteryMultiblocksMap.containsKey(batteryId) &&
                connectedBatteryMultiblocksMap.get(batteryId).contains(cablePos)) {
            // Connection already exists, avoid infinite recursion
            return;
        }

        boolean isNewConnection = !connectedBatteryMultiblocksMap.containsKey(batteryId);

        if (!connectedBatteryMultiblocksMap.containsKey(batteryId)) {
            connectedBatteryMultiblocksMap.put(batteryId, new HashSet<>());
        }
        connectedBatteryMultiblocksMap.get(batteryId).add(cablePos);

        // Add to simple set for backward compatibility
        connectedBatteryMultiblocks.add(battery);

        if (isNewConnection) {
            Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) +
                    " CONNECTED to BatteryMultiblock " + batteryId.toString().substring(0, 8) +
                    " via cable at " + cablePos, true);
        } else {
            Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) +
                    " added connection to BatteryMultiblock " + batteryId.toString().substring(0, 8) +
                    " at " + cablePos + " (" + connectedBatteryMultiblocksMap.get(batteryId).size() + " total connections)", true);
        }

        // Bidirectional connection - only if it's a new connection
        if (isNewConnection) {
            battery.addCableConnection(this, cablePos);
        }
    }

    public void removeBatteryConnection(BatteryMultiblock battery, BlockPos cablePos) {
        if (battery == null) return;

        UUID batteryId = battery.getMultiblockId();

        if (connectedBatteryMultiblocksMap.containsKey(batteryId)) {
            Set<BlockPos> connectionPoints = connectedBatteryMultiblocksMap.get(batteryId);
            boolean hadConnection = connectionPoints.contains(cablePos);
            connectionPoints.remove(cablePos);

            if (connectionPoints.isEmpty()) {
                connectedBatteryMultiblocksMap.remove(batteryId);
                connectedBatteryMultiblocks.remove(battery);
                Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) +
                        " DISCONNECTED from BatteryMultiblock " + batteryId.toString().substring(0, 8) +
                        " (no more connection points)", true);
            } else if (hadConnection) {
                Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) +
                        " removed connection point to BatteryMultiblock " + batteryId.toString().substring(0, 8) +
                        " at " + cablePos + " (" + connectionPoints.size() + " connection points remain)", true);
            }
        }
    }

    public void mergeWith(PowerCableMultiblock other) {
        // Add all cable positions
        this.cableBlockPositions.addAll(other.cableBlockPositions);

        // Update block entity references for the merged cables


        this.connectedCables.addAll(other.connectedCables);
        this.connectedPortalStructures.addAll(other.connectedPortalStructures);
        this.connectedBatteryMultiblocks.addAll(other.connectedBatteryMultiblocks);

        // Merge battery connection maps
        for (Map.Entry<UUID, Set<BlockPos>> entry : other.connectedBatteryMultiblocksMap.entrySet()) {
            UUID batteryId = entry.getKey();
            Set<BlockPos> cablePositions = entry.getValue();

            if (!this.connectedBatteryMultiblocksMap.containsKey(batteryId)) {
                this.connectedBatteryMultiblocksMap.put(batteryId, new HashSet<>());
            }
            this.connectedBatteryMultiblocksMap.get(batteryId).addAll(cablePositions);
        }

        // Update portal references
        for (PortalStructure portal : other.connectedPortalStructures) {
            portal.powerCablesMultiblocks.remove(other);
            portal.powerCablesMultiblocks.add(this);
        }
    }

    // Rest of existing methods
    private void addPortalStructure(Set<PortalStructure> connectedPortalStructures) {
        this.connectedPortalStructures.addAll(connectedPortalStructures);
    }

    private void addBatteryMultiblocks(Set<BatteryMultiblock> batteryMultiblocks) {
        this.connectedBatteryMultiblocks.addAll(batteryMultiblocks);
    }

    private void addCables(Set<PortalPowerCableBlock> connectedCables) {
        this.connectedCables.addAll(connectedCables);
    }

    public void removeCable(PortalPowerCableBlock portalPowerCableBlock) {
        connectedCables.remove(portalPowerCableBlock);
    }

    public Set<UUID> getConnectedBatteryIds() {
        return connectedBatteryMultiblocksMap.keySet();
    }

    public boolean isConnectedToBattery(UUID batteryId) {
        return connectedBatteryMultiblocksMap.containsKey(batteryId) &&
                !connectedBatteryMultiblocksMap.get(batteryId).isEmpty();
    }

    public Set<BlockPos> getCableBlockPositions() {
        return Collections.unmodifiableSet(cableBlockPositions);
    }
}
