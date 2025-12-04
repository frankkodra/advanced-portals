package portal_fluid_pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import portal_fluid_tank.TankMultiblock;
import portal_multiblock.PortalMultiblockManager;
import portal_multiblock.PortalStructure;
import advanced_portals.Logger;
import java.util.*;

public class FluidPipeMultiblock {
    public UUID id;
    private Level level;
    private Set<BlockPos> pipeBlockPositions;
    public Map<UUID, Set<BlockPos>> connectedTankMultiblocksMap;
    public Set<TankMultiblock> connectedTankMultiblocks;

    // CRITICAL: Track portal structure connections bidirectionally
    public Map<UUID, Set<BlockPos>> connectedPortalStructuresMap;

    public FluidPipeMultiblock(UUID id, Level level) {
        this.id = id;
        this.level = level;
        this.pipeBlockPositions = new HashSet<>();
        this.connectedTankMultiblocksMap = new HashMap<>();
        this.connectedTankMultiblocks = new HashSet<>();
        this.connectedPortalStructuresMap = new HashMap<>();

        PortalMultiblockManager.addFluidPipeMultiblock(this);
        Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) + " created", true);
    }

    public static FluidPipeMultiblock getOrCreateFluidPipeMultiblock(UUID multiblockId, Level level) {
        FluidPipeMultiblock existing = PortalMultiblockManager.getFluidPipeMultiblock(multiblockId);
        if (existing != null) {
            Logger.sendMessage("FluidPipeMultiblock: Found existing multiblock with ID " + multiblockId.toString().substring(0, 8), true);
            return existing;
        } else {
            Logger.sendMessage("FluidPipeMultiblock: Creating new multiblock with ID " + multiblockId.toString().substring(0, 8), true);
            FluidPipeMultiblock newMultiblock = new FluidPipeMultiblock(multiblockId, level);
            PortalMultiblockManager.addFluidPipeMultiblock(newMultiblock);
            return newMultiblock;
        }
    }

    public static FluidPipeMultiblock addCreateOrMergeForBlock(BlockPos pos, Level level) {
        Set<FluidPipeMultiblock> adjacentMultiblocks = findAdjacentFluidPipeMultiblocks(pos, level);
        Set<TankMultiblock> adjacentTanks = findAdjacentTanks(pos, level);

        Logger.sendMessage("Placing fluid pipe at " + pos + " - Found " + adjacentMultiblocks.size() + " adjacent FluidPipeMultiblocks", true);

        FluidPipeMultiblock resultMultiblock;

        if (adjacentMultiblocks.isEmpty()) {
            resultMultiblock = new FluidPipeMultiblock(UUID.randomUUID(), level);
            resultMultiblock.addPipePosition(pos);
            Logger.sendMessage("New FluidPipeMultiblock " + resultMultiblock.id.toString().substring(0, 8) + " created with 1 pipe at " + pos, true);
        } else {
            resultMultiblock = mergeAllAdjacentMultiblocks(adjacentMultiblocks);
            resultMultiblock.addPipePosition(pos);
            Logger.sendMessage("FluidPipeMultiblock " + resultMultiblock.id.toString().substring(0, 8) + " added pipe at " + pos + " (total: " + resultMultiblock.pipeBlockPositions.size() + " pipes)", true);
        }

        updateBlockEntityMultiblockReference(pos, level, resultMultiblock);

        Logger.sendMessage("Establishing connections to " + adjacentTanks.size() + " adjacent tanks for pipe at " + pos, true);
        for (TankMultiblock tank : adjacentTanks) {
            resultMultiblock.addTankConnection(tank, pos);
        }

        return resultMultiblock;
    }

    public static void scanAndConnectToNearbyTanks(FluidPipeMultiblock pipe, BlockPos pos, Level level) {
        Set<TankMultiblock> tanks = findAdjacentTanksForRepopulation(pipe,pos, level);
        Logger.sendMessage("REPOPULATION: Scanning for tanks near pipe at " + pos + " - found " + tanks.size() + " tanks", true);
        for (TankMultiblock tank : tanks) {
            pipe.addTankConnection(tank, pos);
        }
    }

    private static Set<FluidPipeMultiblock> findAdjacentFluidPipeMultiblocks(BlockPos pos, Level level) {
        Set<FluidPipeMultiblock> multiblocks = new HashSet<>();
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            Block block = level.getBlockState(neighborPos).getBlock();
            if (block instanceof PortalFluidPipeBlock) {
                FluidPipeMultiblock neighborMultiblock = getMultiblockFromBlockEntity(neighborPos, level);
                if (neighborMultiblock != null) {
                    multiblocks.add(neighborMultiblock);
                    Logger.sendMessage("Found adjacent fluid pipe at " + neighborPos + " belonging to FluidPipeMultiblock " + neighborMultiblock.id.toString().substring(0, 8), true);
                }
            }
        }
        return multiblocks;
    }

    public static FluidPipeMultiblock getMultiblockFromBlockEntity(BlockPos pos, Level level) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof PortalFluidPipeBlockEntity) {
            return ((PortalFluidPipeBlockEntity) blockEntity).getMultiblock();
        }
        return null;
    }

    private static void updateBlockEntityMultiblockReference(BlockPos pos, Level level, FluidPipeMultiblock multiblock) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof PortalFluidPipeBlockEntity) {
            ((PortalFluidPipeBlockEntity) blockEntity).setMultiblock(multiblock);
            Logger.sendMessage("Updated fluid pipe block entity reference at " + pos + " to FluidPipeMultiblock " + multiblock.id.toString().substring(0, 8), true);
        }
    }

    private static Set<TankMultiblock> findAdjacentTanks(BlockPos pos, Level level) {
        Set<TankMultiblock> tanks = new HashSet<>();
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            Block block = level.getBlockState(neighborPos).getBlock();
            if (block instanceof portal_fluid_tank.PortalFluidTankBlock) {
                TankMultiblock tankMultiblock = TankMultiblock.getMultiblockFromBlockEntity(neighborPos, level);
                if (tankMultiblock != null) {
                    tanks.add(tankMultiblock);
                    Logger.sendMessage("Found adjacent fluid tank at " + neighborPos + " belonging to TankMultiblock " + tankMultiblock.getMultiblockId().toString().substring(0, 8), true);
                }
            }
        }
        return tanks;
    }

    private static Set<TankMultiblock> findAdjacentTanksForRepopulation(FluidPipeMultiblock pipe,BlockPos pos, Level level) {
        Set<TankMultiblock> tanks = new HashSet<>();
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            Block block = level.getBlockState(neighborPos).getBlock();
            if (block instanceof portal_fluid_tank.PortalFluidTankBlock) {
                BlockEntity blockEntity = level.getBlockEntity(neighborPos);
                if (blockEntity instanceof portal_fluid_tank.PortalFluidTankBlockEntity tankBE) {
                    TankMultiblock tankMultiblock = null;
                    tankMultiblock = TankMultiblock.getMultiblockFromBlockEntity(neighborPos, level);
                    if (tankMultiblock == null && tankBE.tankMultiblockId != null) {
                        tankMultiblock = TankMultiblock.getOrCreateTankMultiblock(tankBE.tankMultiblockId, level);
                        tankMultiblock.addTank(neighborPos);
                        tankBE.setTankMultiblock(tankMultiblock);
                        Logger.sendMessage("REPOPULATION: Restored tank multiblock at " + neighborPos + " for pipe at " + pos, true);
                    }
                    if (tankMultiblock != null) {
                        tanks.add(tankMultiblock);

                    }
                }
            }
        }
        return tanks;
    }

    private static FluidPipeMultiblock mergeAllAdjacentMultiblocks(Set<FluidPipeMultiblock> multiblocksToMerge) {
        if (multiblocksToMerge.isEmpty()) return null;
        Iterator<FluidPipeMultiblock> iterator = multiblocksToMerge.iterator();
        FluidPipeMultiblock mainMultiblock = iterator.next();

        if (multiblocksToMerge.size() == 1) {
            Logger.sendMessage("Only one adjacent FluidPipeMultiblock " + mainMultiblock.id.toString().substring(0, 8) + " found", true);
            return mainMultiblock;
        }

        Logger.sendMessage("Merging " + multiblocksToMerge.size() + " FluidPipeMultiblocks into " + mainMultiblock.id.toString().substring(0, 8), true);
        int totalPipesBefore = mainMultiblock.pipeBlockPositions.size();
        int totalTankConnectionsBefore = mainMultiblock.connectedTankMultiblocksMap.size();
        int totalPortalConnectionsBefore = mainMultiblock.connectedPortalStructuresMap.size();

        Set<BlockPos> allPositionsToUpdate = new HashSet<>();
        while (iterator.hasNext()) {
            FluidPipeMultiblock otherMultiblock = iterator.next();
            allPositionsToUpdate.addAll(otherMultiblock.pipeBlockPositions);

            if (otherMultiblock.id.equals(mainMultiblock.id)) continue;

            int pipesToAdd = otherMultiblock.pipeBlockPositions.size();
            int tankConnectionsToAdd = otherMultiblock.connectedTankMultiblocksMap.size();
            int portalConnectionsToAdd = otherMultiblock.connectedPortalStructuresMap.size();
            mainMultiblock.mergeWith(otherMultiblock);
            PortalMultiblockManager.removeFluidPipeMultiblock(otherMultiblock);

            Logger.sendMessage("Merged FluidPipeMultiblock " + otherMultiblock.id.toString().substring(0, 8) + " into " + mainMultiblock.id.toString().substring(0, 8) + " (+" + pipesToAdd + " pipes, +" + tankConnectionsToAdd + " tank connections, +" + portalConnectionsToAdd + " portal connections)", true);
        }

        updateAllBlockEntityReferences(mainMultiblock, allPositionsToUpdate);
        int totalPipesAdded = mainMultiblock.pipeBlockPositions.size() - totalPipesBefore;
        int totalTankConnectionsAdded = mainMultiblock.connectedTankMultiblocksMap.size() - totalTankConnectionsBefore;
        int totalPortalConnectionsAdded = mainMultiblock.connectedPortalStructuresMap.size() - totalPortalConnectionsBefore;
        Logger.sendMessage("Fluid pipe merge complete: " + totalPipesAdded + " total pipes added, " + totalTankConnectionsAdded + " tank connections added, " + totalPortalConnectionsAdded + " portal connections added", true);

        return mainMultiblock;
    }

    private static void updateAllBlockEntityReferences(FluidPipeMultiblock multiblock, Set<BlockPos> positionsToUpdate) {
        if (multiblock.level == null) return;
        int updatedCount = 0;
        for (BlockPos pos : positionsToUpdate) {
            BlockEntity blockEntity = multiblock.level.getBlockEntity(pos);
            if (blockEntity instanceof PortalFluidPipeBlockEntity) {
                ((PortalFluidPipeBlockEntity) blockEntity).setMultiblock(multiblock);
                updatedCount++;
            }
        }
        Logger.sendMessage("Updated " + updatedCount + " fluid pipe block entity references to FluidPipeMultiblock " + multiblock.id.toString().substring(0, 8), true);
    }

    public void handlePipeBlockBreak(BlockPos removedPos) {
        Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) + " removing pipe at " + removedPos + " (currently " + pipeBlockPositions.size() + " pipes)", true);
        pipeBlockPositions.remove(removedPos);

        // CRITICAL: Remove portal connections for this pipe
        removePortalConnectionsForPipe(removedPos);
        removeTankConnectionsForPipe(removedPos);

        if (pipeBlockPositions.isEmpty()) {
            PortalMultiblockManager.removeFluidPipeMultiblock(this);
            Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) + " DESTROYED (no pipes remaining)", true);
        }
    }

    private void removePortalConnectionsForPipe(BlockPos pipePos) {
        Logger.sendMessage("Removing portal connections for pipe " + pipePos, true);
        Set<UUID> portalsToUpdate = new HashSet<>();

        for (Map.Entry<UUID, Set<BlockPos>> entry : connectedPortalStructuresMap.entrySet()) {
            UUID portalId = entry.getKey();
            Set<BlockPos> connectionPoints = entry.getValue();
            if (connectionPoints.contains(pipePos)) {
                portalsToUpdate.add(portalId);
            }
        }

        for (UUID portalId : portalsToUpdate) {
            Set<BlockPos> connectionPoints = connectedPortalStructuresMap.get(portalId);
            if (connectionPoints != null) {
                connectionPoints.remove(pipePos);
                if (connectionPoints.isEmpty()) {
                    connectedPortalStructuresMap.remove(portalId);
                    Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) + " DISCONNECTED from PortalStructure " + portalId.toString().substring(0, 8) + " (no more connection points)", true);

                    // CRITICAL: Notify the portal structure about the disconnection
                    PortalStructure portal = PortalMultiblockManager.getPortalStructure(portalId);
                    if (portal != null) {
                        portal.removeFluidPipeMultiblock(this);
                    }
                } else {
                    Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) + " removed connection point to PortalStructure " + portalId.toString().substring(0, 8) + " at " + pipePos + " (" + connectionPoints.size() + " connection points remain)", true);
                }
            }
        }
    }

    private void removeTankConnectionsForPipe(BlockPos pipePos) {
        Logger.sendMessage("Removing tank connections for pipe " + pipePos, true);
        Set<UUID> tanksToUpdate = new HashSet<>();
        for (Map.Entry<UUID, Set<BlockPos>> entry : connectedTankMultiblocksMap.entrySet()) {
            UUID tankId = entry.getKey();
            Set<BlockPos> connectionPoints = entry.getValue();
            if (connectionPoints.contains(pipePos)) {
                tanksToUpdate.add(tankId);
            }
        }

        for (UUID tankId : tanksToUpdate) {
            Set<BlockPos> connectionPoints = connectedTankMultiblocksMap.get(tankId);
            if (connectionPoints != null) {
                connectionPoints.remove(pipePos);
                if (connectionPoints.isEmpty()) {
                    connectedTankMultiblocksMap.remove(tankId);
                    connectedTankMultiblocks.removeIf(tank -> tank.getMultiblockId().equals(tankId));
                    Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) + " DISCONNECTED from TankMultiblock " + tankId.toString().substring(0, 8) + " (no more connection points)", true);
                } else {
                    Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) + " removed connection point to TankMultiblock " + tankId.toString().substring(0, 8) + " at " + pipePos + " (" + connectionPoints.size() + " connection points remain)", true);
                }
            }
        }
    }

    public void addPipePosition(BlockPos pos) {
        pipeBlockPositions.add(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof PortalFluidPipeBlockEntity) {
            ((PortalFluidPipeBlockEntity) blockEntity).setMultiblock(this);
        }
        Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) + " added pipe at " + pos + " (total: " + pipeBlockPositions.size() + " pipes)", true);
    }

    public void addTankConnection(TankMultiblock tank, BlockPos pipePos) {
        Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) + " addTankConnection called for tank " + tank.getMultiblockId().toString().substring(0, 8) + " at PIPE position " + pipePos, true);

        if (tank == null) return;

        UUID tankId = tank.getMultiblockId();

        // Check if this specific connection already exists
        if (connectedTankMultiblocksMap.containsKey(tankId) && connectedTankMultiblocksMap.get(tankId).contains(pipePos)) {
            Logger.sendMessage("Connection already exists at pipe position " + pipePos + " for tank " + tankId.toString().substring(0, 8), true);
            return;
        }

        boolean isNewConnection = !connectedTankMultiblocksMap.containsKey(tankId);

        if (!connectedTankMultiblocksMap.containsKey(tankId)) {
            connectedTankMultiblocksMap.put(tankId, new HashSet<>());
        }
        connectedTankMultiblocksMap.get(tankId).add(pipePos);

        // Add to simple set for backward compatibility
        connectedTankMultiblocks.add(tank);

        if (isNewConnection) {
            Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) + " CONNECTED to TankMultiblock " + tankId.toString().substring(0, 8) + " via pipe at " + pipePos, true);
        } else {
            Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) + " added connection to TankMultiblock " + tankId.toString().substring(0, 8) + " at " + pipePos + " (" + connectedTankMultiblocksMap.get(tankId).size() + " total connections)", true);
        }

        // CRITICAL: Notify the tank to store this connection from its side
        tank.addPipeConnectionFromPipe(this.id, pipePos);
    }

    public void removeTankConnection(TankMultiblock tank, BlockPos pipePos) {
        Logger.sendMessage("Removing tank connections for pipe " + pipePos + " (currently " + connectedTankMultiblocksMap.size() + " tank connections)", true);
        if (tank == null) return;

        UUID tankId = tank.getMultiblockId();

        Logger.sendMessage("Looking for tank ID " + tankId.toString().substring(0, 8) + " in connectedTankMultiblocksMap", true);

        if (connectedTankMultiblocksMap.containsKey(tankId)) {
            Set<BlockPos> connectionPoints = connectedTankMultiblocksMap.get(tankId);
            boolean hadConnection = connectionPoints.contains(pipePos);

            Logger.sendMessage("Found tank connection. Connection points before: " + connectionPoints, true);

            connectionPoints.remove(pipePos);

            if (connectionPoints.isEmpty()) {
                connectedTankMultiblocksMap.remove(tankId);
                connectedTankMultiblocks.remove(tank);
                Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) + " DISCONNECTED from TankMultiblock " + tankId.toString().substring(0, 8) + " (no more connection points)", true);
            } else if (hadConnection) {
                Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) + " removed connection point to TankMultiblock " + tankId.toString().substring(0, 8) + " at " + pipePos + " (" + connectionPoints.size() + " connection points remain)", true);
            }
        } else {
            Logger.sendMessage("WARNING: Tank ID " + tankId.toString().substring(0, 8) + " not found in connectedTankMultiblocksMap", true);
        }
    }

    // NEW METHOD: Add portal connection from portal side
    public void addPortalConnectionFromPortal(UUID portalId, BlockPos pipePos) {
        Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) + " connected to PortalStructure " + portalId.toString().substring(0, 8) + " at " + pipePos, true);
        if (!connectedPortalStructuresMap.containsKey(portalId)) {
            connectedPortalStructuresMap.put(portalId, new HashSet<>());
        }
        connectedPortalStructuresMap.get(portalId).add(pipePos);
    }

    // NEW METHOD: Remove portal connection from portal side
    public void removePortalConnectionFromPortal(UUID portalId, BlockPos pipePos) {
        Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) + " disconnected from PortalStructure " + portalId.toString().substring(0, 8) + " at " + pipePos, true);
        if (connectedPortalStructuresMap.containsKey(portalId)) {
            Set<BlockPos> connectionPoints = connectedPortalStructuresMap.get(portalId);
            connectionPoints.remove(pipePos);
            if (connectionPoints.isEmpty()) {
                connectedPortalStructuresMap.remove(portalId);
            }
        }
    }

    public void mergeWith(FluidPipeMultiblock other) {
        Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) + " merging with " + other.id.toString().substring(0, 8), true);

        // Add all pipe positions
        this.pipeBlockPositions.addAll(other.pipeBlockPositions);

        // CRITICAL: Merge tank connection maps
        for (Map.Entry<UUID, Set<BlockPos>> entry : other.connectedTankMultiblocksMap.entrySet()) {
            UUID tankId = entry.getKey();
            Set<BlockPos> pipePositions = entry.getValue();

            if (!this.connectedTankMultiblocksMap.containsKey(tankId)) {
                this.connectedTankMultiblocksMap.put(tankId, new HashSet<>());
            }
            this.connectedTankMultiblocksMap.get(tankId).addAll(pipePositions);

            Logger.sendMessage("Merged " + pipePositions.size() + " connection points to tank " + tankId.toString().substring(0, 8), true);

            // Notify the tank multiblock to update its connection
            TankMultiblock tank = PortalMultiblockManager.getTankMultiblock(tankId);
            if (tank != null) {
                for (BlockPos pipePos : pipePositions) {
                    tank.removePipeConnectionFromPipe(other.id, pipePos);
                    tank.addPipeConnectionFromPipe(this.id, pipePos);
                }
                Logger.sendMessage("Updated tank " + tankId.toString().substring(0, 8) + " connections from old pipe multiblock to merged pipe multiblock", true);
            }
        }

        // CRITICAL: Merge portal connection maps
        for (Map.Entry<UUID, Set<BlockPos>> entry : other.connectedPortalStructuresMap.entrySet()) {
            UUID portalId = entry.getKey();
            Set<BlockPos> pipePositions = entry.getValue();

            if (!this.connectedPortalStructuresMap.containsKey(portalId)) {
                this.connectedPortalStructuresMap.put(portalId, new HashSet<>());
            }
            this.connectedPortalStructuresMap.get(portalId).addAll(pipePositions);

            Logger.sendMessage("Merged " + pipePositions.size() + " connection points to portal " + portalId.toString().substring(0, 8), true);

            // CRITICAL: Notify the portal structure to update its connection
            PortalStructure portal = PortalMultiblockManager.getPortalStructure(portalId);
            if (portal != null) {
                for (BlockPos pipePos : pipePositions) {
                    portal.removeFluidPipeMultiblock(other);
                    portal.addFluidPipeMultiblock(this);
                }
                Logger.sendMessage("Updated portal " + portalId.toString().substring(0, 8) + " connections from old pipe multiblock to merged pipe multiblock", true);
            }
        }

        Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) + " merged with " + other.id.toString().substring(0, 8) + " - added " + other.pipeBlockPositions.size() + " pipes, " + other.connectedTankMultiblocksMap.size() + " tank connections, " + other.connectedPortalStructuresMap.size() + " portal connections", true);
    }

    public Set<UUID> getConnectedTankIds() {
        return connectedTankMultiblocksMap.keySet();
    }

    public boolean isConnectedToTank(UUID tankId) {
        return connectedTankMultiblocksMap.containsKey(tankId) && !connectedTankMultiblocksMap.get(tankId).isEmpty();
    }

    public Set<BlockPos> getPipeBlockPositions() {
        return Collections.unmodifiableSet(pipeBlockPositions);
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