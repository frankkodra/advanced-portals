package portal_power_cable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import portal_battery.BatteryMultiblock;
import portal_battery.PortalBatteryBlock;
import portal_multiblock.PortalMultiblockManager;
import portal_multiblock.PortalStructure;
import advanced_portals.Logger;
import java.util.*;

public class PowerCableMultiblock {
    public UUID id;
    private Level level;
    private Set<BlockPos> cableBlockPositions;
    public Map<UUID, Set<BlockPos>> connectedBatteryMultiblocksMap;
    public Set<BatteryMultiblock> connectedBatteryMultiblocks;

    // CRITICAL: Track portal structure connections bidirectionally
    public Map<UUID, Set<BlockPos>> connectedPortalStructuresMap;

    public PowerCableMultiblock(UUID id, Level level) {
        this.id = id;
        this.level = level;
        this.cableBlockPositions = new HashSet<>();
        this.connectedBatteryMultiblocksMap = new HashMap<>();
        this.connectedBatteryMultiblocks = new HashSet<>();
        this.connectedPortalStructuresMap = new HashMap<>();

        PortalMultiblockManager.addPowerCableMultiblock(this);
        Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) + " created", true);
    }

    public static PowerCableMultiblock getOrCreatePowerCableMultiblock(UUID multiblockId, Level level) {
        PowerCableMultiblock existing = PortalMultiblockManager.getPowerCableMultiblock(multiblockId);
        if (existing != null) {
            Logger.sendMessage("PowerCableMultiblock: Found existing multiblock with ID " + multiblockId.toString().substring(0, 8), true);
            return existing;
        } else {
            Logger.sendMessage("PowerCableMultiblock: Creating new multiblock with ID " + multiblockId.toString().substring(0, 8), true);
            PowerCableMultiblock newMultiblock = new PowerCableMultiblock(multiblockId, level);
            PortalMultiblockManager.addPowerCableMultiblock(newMultiblock);
            return newMultiblock;
        }
    }

    public static PowerCableMultiblock addCreateOrMergeForBlock(BlockPos pos, Level level) {
        Set<PowerCableMultiblock> adjacentMultiblocks = findAdjacentPowerCableMultiblocks(pos, level);
        Set<BatteryMultiblock> adjacentBatteries = findAdjacentBatteries(pos, level);

        Logger.sendMessage("Placing cable at " + pos + " - Found " + adjacentMultiblocks.size() + " adjacent PowerCableMultiblocks", true);

        PowerCableMultiblock resultMultiblock;

        if (adjacentMultiblocks.isEmpty()) {
            resultMultiblock = new PowerCableMultiblock(UUID.randomUUID(), level);
            resultMultiblock.addCablePosition(pos);
            Logger.sendMessage("New PowerCableMultiblock " + resultMultiblock.id.toString().substring(0, 8) + " created with 1 cable at " + pos, true);
        } else {
            resultMultiblock = mergeAllAdjacentMultiblocks(adjacentMultiblocks);
            resultMultiblock.addCablePosition(pos);
            Logger.sendMessage("PowerCableMultiblock " + resultMultiblock.id.toString().substring(0, 8) + " added cable at " + pos + " (total: " + resultMultiblock.cableBlockPositions.size() + " cables)", true);
        }

        updateBlockEntityMultiblockReference(pos, level, resultMultiblock);

        Logger.sendMessage("Establishing connections to " + adjacentBatteries.size() + " adjacent batteries for cable at " + pos, true);
        for (BatteryMultiblock battery : adjacentBatteries) {
            resultMultiblock.addBatteryConnection(battery, pos);
        }

        return resultMultiblock;
    }

    public static void scanAndConnectToNearbyBatteries(PowerCableMultiblock cable, BlockPos pos, Level level) {
        Set<BatteryMultiblock> batteries = findAdjacentBatteriesForRepopulation(pos, level);
        Logger.sendMessage("REPOPULATION: Scanning for batteries near cable at " + pos + " - found " + batteries.size() + " batteries", true);
        for (BatteryMultiblock battery : batteries) {
            cable.addBatteryConnection(battery, pos);
        }
    }

    private static Set<PowerCableMultiblock> findAdjacentPowerCableMultiblocks(BlockPos pos, Level level) {
        Set<PowerCableMultiblock> multiblocks = new HashSet<>();
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            Block block = level.getBlockState(neighborPos).getBlock();
            if (block instanceof PortalPowerCableBlock) {
                PowerCableMultiblock neighborMultiblock = getMultiblockFromBlockEntity(neighborPos, level);
                if (neighborMultiblock != null) {
                    multiblocks.add(neighborMultiblock);
                    Logger.sendMessage("Found adjacent cable at " + neighborPos + " belonging to PowerCableMultiblock " + neighborMultiblock.id.toString().substring(0, 8), true);
                }
            }
        }
        return multiblocks;
    }

    public static PowerCableMultiblock getMultiblockFromBlockEntity(BlockPos pos, Level level) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof PortalPowerCableBlockEntity) {
            return ((PortalPowerCableBlockEntity) blockEntity).getMultiblock();
        }
        return null;
    }

    private static void updateBlockEntityMultiblockReference(BlockPos pos, Level level, PowerCableMultiblock multiblock) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof PortalPowerCableBlockEntity) {
            ((PortalPowerCableBlockEntity) blockEntity).setMultiblock(multiblock);
            Logger.sendMessage("Updated block entity reference at " + pos + " to PowerCableMultiblock " + multiblock.id.toString().substring(0, 8), true);
        }
    }

    private static Set<BatteryMultiblock> findAdjacentBatteries(BlockPos pos, Level level) {
        Set<BatteryMultiblock> batteries = new HashSet<>();
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            Block block = level.getBlockState(neighborPos).getBlock();
            if (block instanceof PortalBatteryBlock) {
                BatteryMultiblock batteryMultiblock = BatteryMultiblock.getMultiblockFromBlockEntity(neighborPos, level);
                if (batteryMultiblock != null) {
                    batteries.add(batteryMultiblock);
                    Logger.sendMessage("Found adjacent battery " + batteryMultiblock.getMultiblockId().toString().substring(0, 8) + " at " + neighborPos, true);
                }
            }
        }
        return batteries;
    }

    private static Set<BatteryMultiblock> findAdjacentBatteriesForRepopulation(BlockPos pos, Level level) {
        Set<BatteryMultiblock> batteries = new HashSet<>();
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            Block block = level.getBlockState(neighborPos).getBlock();
            if (block instanceof PortalBatteryBlock) {
                BlockEntity blockEntity = level.getBlockEntity(neighborPos);
                if (blockEntity instanceof portal_battery.PortalBatteryBlockEntity batteryBE) {
                    BatteryMultiblock batteryMultiblock = null;
                    batteryMultiblock = BatteryMultiblock.getMultiblockFromBlockEntity(neighborPos, level);
                    if (batteryMultiblock == null && batteryBE.batteryMultiblockId != null) {
                        batteryMultiblock = BatteryMultiblock.getOrCreateBatteryMultiblock(batteryBE.batteryMultiblockId, level);
                        batteryMultiblock.addBattery(neighborPos);
                        batteryBE.setBatteryMultiblock(batteryMultiblock);
                        Logger.sendMessage("REPOPULATION: Restored battery multiblock at " + neighborPos + " for cable at " + pos, true);
                    }
                    if (batteryMultiblock != null) {
                        batteries.add(batteryMultiblock);
                    }
                }
            }
        }
        return batteries;
    }

    private static PowerCableMultiblock mergeAllAdjacentMultiblocks(Set<PowerCableMultiblock> multiblocksToMerge) {
        if (multiblocksToMerge.isEmpty()) return null;
        Iterator<PowerCableMultiblock> iterator = multiblocksToMerge.iterator();
        PowerCableMultiblock mainMultiblock = iterator.next();

        if (multiblocksToMerge.size() == 1) {
            Logger.sendMessage("Only one adjacent PowerCableMultiblock " + mainMultiblock.id.toString().substring(0, 8) + " found", true);
            return mainMultiblock;
        }

        Logger.sendMessage("Merging " + multiblocksToMerge.size() + " PowerCableMultiblocks into " + mainMultiblock.id.toString().substring(0, 8), true);
        int totalCablesBefore = mainMultiblock.cableBlockPositions.size();
        int totalBatteryConnectionsBefore = mainMultiblock.connectedBatteryMultiblocksMap.size();
        int totalPortalConnectionsBefore = mainMultiblock.connectedPortalStructuresMap.size();

        Set<BlockPos> allPositionsToUpdate = new HashSet<>();
        while (iterator.hasNext()) {
            PowerCableMultiblock otherMultiblock = iterator.next();
            allPositionsToUpdate.addAll(otherMultiblock.cableBlockPositions);

            if (otherMultiblock.id.equals(mainMultiblock.id)) continue;

            int cablesToAdd = otherMultiblock.cableBlockPositions.size();
            int batteryConnectionsToAdd = otherMultiblock.connectedBatteryMultiblocksMap.size();
            int portalConnectionsToAdd = otherMultiblock.connectedPortalStructuresMap.size();
            mainMultiblock.mergeWith(otherMultiblock);
            PortalMultiblockManager.removePowerCableMultiblock(otherMultiblock);

            Logger.sendMessage("Merged PowerCableMultiblock " + otherMultiblock.id.toString().substring(0, 8) + " into " + mainMultiblock.id.toString().substring(0, 8) + " (+" + cablesToAdd + " cables, +" + batteryConnectionsToAdd + " battery connections, +" + portalConnectionsToAdd + " portal connections)", true);
        }

        updateAllBlockEntityReferences(mainMultiblock, allPositionsToUpdate);
        int totalCablesAdded = mainMultiblock.cableBlockPositions.size() - totalCablesBefore;
        int totalBatteryConnectionsAdded = mainMultiblock.connectedBatteryMultiblocksMap.size() - totalBatteryConnectionsBefore;
        int totalPortalConnectionsAdded = mainMultiblock.connectedPortalStructuresMap.size() - totalPortalConnectionsBefore;
        Logger.sendMessage("Merge complete: " + totalCablesAdded + " total cables added, " + totalBatteryConnectionsAdded + " battery connections added, " + totalPortalConnectionsAdded + " portal connections added", true);

        return mainMultiblock;
    }

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
        Logger.sendMessage("Updated " + updatedCount + " block entity references to PowerCableMultiblock " + multiblock.id.toString().substring(0, 8), true);
    }

    public void handleCableBlockBreak(BlockPos removedPos) {
        Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) + " removing cable at " + removedPos + " (currently " + cableBlockPositions.size() + " cables)", true);
        cableBlockPositions.remove(removedPos);

        // CRITICAL: Remove portal connections for this cable
        removePortalConnectionsForCable(removedPos);
        removeBatteryConnectionsForCable(removedPos);

        if (cableBlockPositions.isEmpty()) {
            PortalMultiblockManager.removePowerCableMultiblock(this);
            Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) + " DESTROYED (no cables remaining)", true);
        }
    }

    private void removePortalConnectionsForCable(BlockPos cablePos) {
        Logger.sendMessage("Removing portal connections for cable " + cablePos, true);
        Set<UUID> portalsToUpdate = new HashSet<>();

        for (Map.Entry<UUID, Set<BlockPos>> entry : connectedPortalStructuresMap.entrySet()) {
            UUID portalId = entry.getKey();
            Set<BlockPos> connectionPoints = entry.getValue();
            if (connectionPoints.contains(cablePos)) {
                portalsToUpdate.add(portalId);
            }
        }

        for (UUID portalId : portalsToUpdate) {
            Set<BlockPos> connectionPoints = connectedPortalStructuresMap.get(portalId);
            if (connectionPoints != null) {
                connectionPoints.remove(cablePos);
                if (connectionPoints.isEmpty()) {
                    connectedPortalStructuresMap.remove(portalId);
                    Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) + " DISCONNECTED from PortalStructure " + portalId.toString().substring(0, 8) + " (no more connection points)", true);

                    // CRITICAL: Notify the portal structure about the disconnection
                    PortalStructure portal = PortalMultiblockManager.getPortalStructure(portalId);
                    if (portal != null) {
                        portal.removePowerCableMultiblock(this);
                    }
                } else {
                    Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) + " removed connection point to PortalStructure " + portalId.toString().substring(0, 8) + " at " + cablePos + " (" + connectionPoints.size() + " connection points remain)", true);
                }
            }
        }
    }

    private void removeBatteryConnectionsForCable(BlockPos cablePos) {
        Logger.sendMessage("Removing battery connections for cable " + cablePos, true);
        Set<UUID> batteriesToUpdate = new HashSet<>();
        for (Map.Entry<UUID, Set<BlockPos>> entry : connectedBatteryMultiblocksMap.entrySet()) {
            UUID batteryId = entry.getKey();
            Set<BlockPos> connectionPoints = entry.getValue();
            if (connectionPoints.contains(cablePos)) {
                batteriesToUpdate.add(batteryId);
            }
        }

        for (UUID batteryId : batteriesToUpdate) {
            Set<BlockPos> connectionPoints = connectedBatteryMultiblocksMap.get(batteryId);
            if (connectionPoints != null) {
                connectionPoints.remove(cablePos);
                if (connectionPoints.isEmpty()) {
                    connectedBatteryMultiblocksMap.remove(batteryId);
                    connectedBatteryMultiblocks.removeIf(battery -> battery.getMultiblockId().equals(batteryId));
                    Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) + " DISCONNECTED from BatteryMultiblock " + batteryId.toString().substring(0, 8) + " (no more connection points)", true);
                } else {
                    Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) + " removed connection point to BatteryMultiblock " + batteryId.toString().substring(0, 8) + " at " + cablePos + " (" + connectionPoints.size() + " connection points remain)", true);
                }
            }
        }
    }

    public void addCablePosition(BlockPos pos) {
        cableBlockPositions.add(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof PortalPowerCableBlockEntity) {
            ((PortalPowerCableBlockEntity) blockEntity).setMultiblock(this);
        }
        Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) + " added cable at " + pos + " (total: " + cableBlockPositions.size() + " cables)", true);
    }

    public void addBatteryConnection(BatteryMultiblock battery, BlockPos cablePos) {
        Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) + " addBatteryConnection called for battery " + battery.getMultiblockId().toString().substring(0, 8) + " at CABLE position " + cablePos, true);

        if (battery == null) return;

        UUID batteryId = battery.getMultiblockId();

        // Check if this specific connection already exists
        if (connectedBatteryMultiblocksMap.containsKey(batteryId) && connectedBatteryMultiblocksMap.get(batteryId).contains(cablePos)) {
            Logger.sendMessage("Connection already exists at cable position " + cablePos + " for battery " + batteryId.toString().substring(0, 8), true);
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
            Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) + " CONNECTED to BatteryMultiblock " + batteryId.toString().substring(0, 8) + " via cable at " + cablePos, true);
        } else {
            Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) + " added connection to BatteryMultiblock " + batteryId.toString().substring(0, 8) + " at " + cablePos + " (" + connectedBatteryMultiblocksMap.get(batteryId).size() + " total connections)", true);
        }

        // CRITICAL: Notify the battery to store this connection from its side
        battery.addCableConnectionFromCable(this.id, cablePos);
    }

    public void removeBatteryConnection(BatteryMultiblock battery, BlockPos cablePos) {
        Logger.sendMessage("Removing battery connections for cable " + cablePos + " (currently " + connectedBatteryMultiblocksMap.size() + " battery connections)", true);
        if (battery == null) return;

        UUID batteryId = battery.getMultiblockId();

        Logger.sendMessage("Looking for battery ID " + batteryId.toString().substring(0, 8) + " in connectedBatteryMultiblocksMap", true);

        if (connectedBatteryMultiblocksMap.containsKey(batteryId)) {
            Set<BlockPos> connectionPoints = connectedBatteryMultiblocksMap.get(batteryId);
            boolean hadConnection = connectionPoints.contains(cablePos);

            Logger.sendMessage("Found battery connection. Connection points before: " + connectionPoints, true);

            connectionPoints.remove(cablePos);

            if (connectionPoints.isEmpty()) {
                connectedBatteryMultiblocksMap.remove(batteryId);
                connectedBatteryMultiblocks.remove(battery);
                Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) + " DISCONNECTED from BatteryMultiblock " + batteryId.toString().substring(0, 8) + " (no more connection points)", true);
            } else if (hadConnection) {
                Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) + " removed connection point to BatteryMultiblock " + batteryId.toString().substring(0, 8) + " at " + cablePos + " (" + connectionPoints.size() + " connection points remain)", true);
            }
        } else {
            Logger.sendMessage("WARNING: Battery ID " + batteryId.toString().substring(0, 8) + " not found in connectedBatteryMultiblocksMap", true);
        }
    }

    // NEW METHOD: Add portal connection from portal side
    public void addPortalConnectionFromPortal(UUID portalId, BlockPos cablePos) {
        Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) + " connected to PortalStructure " + portalId.toString().substring(0, 8) + " at " + cablePos, true);
        if (!connectedPortalStructuresMap.containsKey(portalId)) {
            connectedPortalStructuresMap.put(portalId, new HashSet<>());
        }
        connectedPortalStructuresMap.get(portalId).add(cablePos);
    }

    // NEW METHOD: Remove portal connection from portal side
    public void removePortalConnectionFromPortal(UUID portalId, BlockPos cablePos) {
        Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) + " disconnected from PortalStructure " + portalId.toString().substring(0, 8) + " at " + cablePos, true);
        if (connectedPortalStructuresMap.containsKey(portalId)) {
            Set<BlockPos> connectionPoints = connectedPortalStructuresMap.get(portalId);
            connectionPoints.remove(cablePos);
            if (connectionPoints.isEmpty()) {
                connectedPortalStructuresMap.remove(portalId);
            }
        }
    }

    public void mergeWith(PowerCableMultiblock other) {
        Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) + " merging with " + other.id.toString().substring(0, 8), true);

        // Add all cable positions
        this.cableBlockPositions.addAll(other.cableBlockPositions);

        // CRITICAL: Merge battery connection maps
        for (Map.Entry<UUID, Set<BlockPos>> entry : other.connectedBatteryMultiblocksMap.entrySet()) {
            UUID batteryId = entry.getKey();
            Set<BlockPos> cablePositions = entry.getValue();

            if (!this.connectedBatteryMultiblocksMap.containsKey(batteryId)) {
                this.connectedBatteryMultiblocksMap.put(batteryId, new HashSet<>());
            }
            this.connectedBatteryMultiblocksMap.get(batteryId).addAll(cablePositions);

            Logger.sendMessage("Merged " + cablePositions.size() + " connection points to battery " + batteryId.toString().substring(0, 8), true);

            // Notify the battery multiblock to update its connection
            BatteryMultiblock battery = PortalMultiblockManager.getBatteryMultiblock(batteryId);
            if (battery != null) {
                for (BlockPos cablePos : cablePositions) {
                    battery.removeCableConnectionFromCable(other.id, cablePos);
                    battery.addCableConnectionFromCable(this.id, cablePos);
                }
                Logger.sendMessage("Updated battery " + batteryId.toString().substring(0, 8) + " connections from old cable multiblock to merged cable multiblock", true);
            }
        }

        // CRITICAL: Merge portal connection maps
        for (Map.Entry<UUID, Set<BlockPos>> entry : other.connectedPortalStructuresMap.entrySet()) {
            UUID portalId = entry.getKey();
            Set<BlockPos> cablePositions = entry.getValue();

            if (!this.connectedPortalStructuresMap.containsKey(portalId)) {
                this.connectedPortalStructuresMap.put(portalId, new HashSet<>());
            }
            this.connectedPortalStructuresMap.get(portalId).addAll(cablePositions);

            Logger.sendMessage("Merged " + cablePositions.size() + " connection points to portal " + portalId.toString().substring(0, 8), true);

            // CRITICAL: Notify the portal structure to update its connection
            PortalStructure portal = PortalMultiblockManager.getPortalStructure(portalId);
            if (portal != null) {
                for (BlockPos cablePos : cablePositions) {
                    portal.removePowerCableMultiblock(other);
                    portal.addPowerCableMultiblock(this);
                }
                Logger.sendMessage("Updated portal " + portalId.toString().substring(0, 8) + " connections from old cable multiblock to merged cable multiblock", true);
            }
        }

        Logger.sendMessage("PowerCableMultiblock " + id.toString().substring(0, 8) + " merged with " + other.id.toString().substring(0, 8) + " - added " + other.cableBlockPositions.size() + " cables, " + other.connectedBatteryMultiblocksMap.size() + " battery connections, " + other.connectedPortalStructuresMap.size() + " portal connections", true);
    }

    public Set<UUID> getConnectedBatteryIds() {
        return connectedBatteryMultiblocksMap.keySet();
    }

    public boolean isConnectedToBattery(UUID batteryId) {
        return connectedBatteryMultiblocksMap.containsKey(batteryId) && !connectedBatteryMultiblocksMap.get(batteryId).isEmpty();
    }

    public Set<BlockPos> getCableBlockPositions() {
        return Collections.unmodifiableSet(cableBlockPositions);
    }

    // NEW METHOD: Get connected portal IDs
    public Set<UUID> getConnectedPortalIds() {
        return connectedPortalStructuresMap.keySet();
    }

    // NEW METHOD: Check if connected to portal
    public boolean isConnectedToPortal(UUID portalId) {
        return connectedPortalStructuresMap.containsKey(portalId) && !connectedPortalStructuresMap.get(portalId).isEmpty();
    }
}