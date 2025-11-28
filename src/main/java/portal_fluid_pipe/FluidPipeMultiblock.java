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
    public Set<PortalFluidPipeBlock> connectedPipes;
    public Set<PortalStructure> connectedPortalStructures;
    public Set<TankMultiblock> connectedTankMultiblocks;
    public Map<UUID, Set<BlockPos>> connectedTankMultiblocksMap;

    public UUID id;
    private Level level;
    private Set<BlockPos> pipeBlockPositions;

    public FluidPipeMultiblock(UUID id, Level level) {
        this.id = id;
        this.level = level;
        this.connectedPipes = new HashSet<>();
        this.connectedPortalStructures = new HashSet<>();
        this.connectedTankMultiblocks = new HashSet<>();
        this.connectedTankMultiblocksMap = new HashMap<>();
        this.pipeBlockPositions = new HashSet<>();

        PortalMultiblockManager.addFluidPipeMultiblock(this);
        Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) + " created", true);
    }

    public static FluidPipeMultiblock getOrCreateFluidPipeMultiblock(UUID multiblockId, Level level) {
        FluidPipeMultiblock existing = PortalMultiblockManager.getFluidPipeMultiblock(multiblockId);
        if (existing != null) {
            Logger.sendMessage("FluidPipeMultiblock: Found existing multiblock with ID " +
                    multiblockId.toString().substring(0, 8), true);
            return existing;
        } else {
            Logger.sendMessage("FluidPipeMultiblock: Creating new multiblock with ID " +
                    multiblockId.toString().substring(0, 8), true);
            FluidPipeMultiblock newMultiblock = new FluidPipeMultiblock(multiblockId, level);
            PortalMultiblockManager.addFluidPipeMultiblock(newMultiblock);
            return newMultiblock;
        }
    }

    public static FluidPipeMultiblock addCreateOrMergeForBlock(BlockPos pos, Level level) {
        Set<FluidPipeMultiblock> adjacentMultiblocks = findAdjacentFluidPipeMultiblocks(pos, level);
        Set<PortalStructure> adjacentPortals = findAdjacentPortalStructures(pos, level);
        Set<TankMultiblock> adjacentTanks = findAdjacentTanks(pos, level);

        Logger.sendMessage("Placing fluid pipe at " + pos + " - Found " + adjacentMultiblocks.size() +
                " adjacent FluidPipeMultiblocks, " + adjacentPortals.size() + " portals, " +
                adjacentTanks.size() + " tanks", true);

        FluidPipeMultiblock resultMultiblock;

        if (adjacentMultiblocks.isEmpty()) {
            resultMultiblock = new FluidPipeMultiblock(UUID.randomUUID(), level);
            resultMultiblock.addPipePosition(pos);
            Logger.sendMessage("New FluidPipeMultiblock " + resultMultiblock.id.toString().substring(0, 8) +
                    " created with 1 pipe at " + pos, true);
        }
        else {
            resultMultiblock = mergeAllAdjacentMultiblocks(adjacentMultiblocks);
            resultMultiblock.addPipePosition(pos);
            Logger.sendMessage("FluidPipeMultiblock " + resultMultiblock.id.toString().substring(0, 8) +
                    " added pipe at " + pos + " (total: " + resultMultiblock.pipeBlockPositions.size() + " pipes)", true);
        }

        updateBlockEntityMultiblockReference(pos, level, resultMultiblock);

        for (PortalStructure portal : adjacentPortals) {
            resultMultiblock.connectedPortalStructures.add(portal);
            portal.fluidPipeMultiblocks.add(resultMultiblock);
            Logger.sendMessage("FluidPipeMultiblock " + resultMultiblock.id.toString().substring(0, 8) +
                    " connected to PortalStructure", true);
        }

        for (TankMultiblock tank : adjacentTanks) {
            resultMultiblock.addTankConnection(tank, pos);
        }

        return resultMultiblock;
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
                    Logger.sendMessage("Found adjacent fluid pipe at " + neighborPos + " belonging to FluidPipeMultiblock " +
                            neighborMultiblock.id.toString().substring(0, 8), true);
                } else {
                    Logger.sendMessage("WARNING: Fluid pipe at " + neighborPos + " has null multiblock reference in block entity!", true);
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
            Logger.sendMessage("Updated fluid pipe block entity reference at " + pos + " to FluidPipeMultiblock " +
                    multiblock.id.toString().substring(0, 8), true);
        } else {
            Logger.sendMessage("ERROR: No fluid pipe block entity found at " + pos + " to update multiblock reference!", true);
        }
    }

    private static Set<PortalStructure> findAdjacentPortalStructures(BlockPos pos, Level level) {
        Set<PortalStructure> portals = new HashSet<>();

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockEntity block = level.getBlockEntity(neighborPos);

            if (block instanceof portal_block.PortalBlockEntity) {
                PortalStructure portalStructure = ((portal_block.PortalBlockEntity) block).portalStructure;
                if (portalStructure != null) {
                    portals.add(portalStructure);
                    Logger.sendMessage("Found adjacent portal structure at " + neighborPos, true);
                }
            }
        }
        return portals;
    }

    private static Set<TankMultiblock> findAdjacentTanks(BlockPos pos, Level level) {
        Set<TankMultiblock> tanks = new HashSet<>();

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            Block block = level.getBlockState(neighborPos).getBlock();

            if (block instanceof portal_fluid_tank.PortalFluidTankBlock) {
                TankMultiblock tankMultiblock = portal_fluid_tank.TankMultiblock.getMultiblockFromBlockEntity(neighborPos, level);
                if (tankMultiblock != null) {
                    tanks.add(tankMultiblock);
                    Logger.sendMessage("Found adjacent fluid tank at " + neighborPos + " belonging to TankMultiblock " +
                            tankMultiblock.getMultiblockId().toString().substring(0, 8), true);
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
            Logger.sendMessage("Only one adjacent FluidPipeMultiblock " + mainMultiblock.id.toString().substring(0, 8) +
                    " found", true);
            return mainMultiblock;
        }

        Logger.sendMessage("Merging " + multiblocksToMerge.size() + " FluidPipeMultiblocks into " +
                mainMultiblock.id.toString().substring(0, 8), true);

        int totalPipesBefore = mainMultiblock.pipeBlockPositions.size();
        int totalTankConnectionsBefore = mainMultiblock.connectedTankMultiblocksMap.size();
        int totalPortalConnectionsBefore = mainMultiblock.connectedPortalStructures.size();

        Set<BlockPos> allPositionsToUpdate = new HashSet<>();

        while (iterator.hasNext()) {
            FluidPipeMultiblock otherMultiblock = iterator.next();
            allPositionsToUpdate.addAll(otherMultiblock.pipeBlockPositions);

            if (otherMultiblock.id.equals(mainMultiblock.id)) {
                continue;
            }

            int pipesToAdd = otherMultiblock.pipeBlockPositions.size();
            int tankConnectionsToAdd = otherMultiblock.connectedTankMultiblocksMap.size();
            int portalConnectionsToAdd = otherMultiblock.connectedPortalStructures.size();

            mainMultiblock.mergeWith(otherMultiblock);
            PortalMultiblockManager.removeFluidPipeMultiblock(otherMultiblock);

            Logger.sendMessage("Merged FluidPipeMultiblock " + otherMultiblock.id.toString().substring(0, 8) +
                    " into " + mainMultiblock.id.toString().substring(0, 8) +
                    " (+" + pipesToAdd + " pipes, +" + tankConnectionsToAdd + " tank connections, +" + portalConnectionsToAdd + " portal connections)", true);
        }

        updateAllBlockEntityReferences(mainMultiblock, allPositionsToUpdate);

        int totalPipesAdded = mainMultiblock.pipeBlockPositions.size() - totalPipesBefore;
        int totalTankConnectionsAdded = mainMultiblock.connectedTankMultiblocksMap.size() - totalTankConnectionsBefore;
        int totalPortalConnectionsAdded = mainMultiblock.connectedPortalStructures.size() - totalPortalConnectionsBefore;

        Logger.sendMessage("Fluid pipe merge complete: " + totalPipesAdded + " total pipes added, " +
                totalTankConnectionsAdded + " tank connections added, " + totalPortalConnectionsAdded + " portal connections added", true);

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
        Logger.sendMessage("Updated " + updatedCount + " fluid pipe block entity references to FluidPipeMultiblock " +
                multiblock.id.toString().substring(0, 8), true);
    }

    public void handlePipeBlockBreak(BlockPos removedPos) {
        Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) +
                " removing pipe at " + removedPos + " (currently " + pipeBlockPositions.size() + " pipes)", true);

        pipeBlockPositions.remove(removedPos);
        removeTankConnectionsForPipe(removedPos);

        connectedPipes.removeIf(pipe -> true);

        if (pipeBlockPositions.isEmpty()) {
            PortalMultiblockManager.removeFluidPipeMultiblock(this);
            Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) + " DESTROYED (no pipes remaining)", true);
            return;
        }

        List<Set<BlockPos>> disconnectedComponents = findDisconnectedComponents();
        if (disconnectedComponents.size() > 1) {
            Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) +
                    " splitting into " + disconnectedComponents.size() + " components", true);
            splitIntoComponents(disconnectedComponents);
        } else {
            Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) +
                    " now has " + pipeBlockPositions.size() + " pipes (no split needed)", true);
        }
    }

    private void removeTankConnectionsForPipe(BlockPos pipePos) {
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
                    Logger.sendMessage("Disconnected from TankMultiblock " + tankId.toString().substring(0, 8) +
                            " (no more connection points)", true);
                } else {
                    Logger.sendMessage("Removed connection point to TankMultiblock " + tankId.toString().substring(0, 8) +
                            " at " + pipePos + " (" + connectionPoints.size() + " connection points remain)", true);
                }
            }
        }
    }

    private List<Set<BlockPos>> findDisconnectedComponents() {
        Set<BlockPos> visited = new HashSet<>();
        List<Set<BlockPos>> components = new ArrayList<>();

        for (BlockPos startPos : pipeBlockPositions) {
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
            if (pipeBlockPositions.contains(neighbor)) {
                floodFill(neighbor, component, visited);
            }
        }
    }

    private void splitIntoComponents(List<Set<BlockPos>> components) {
        Set<BlockPos> mainComponent = components.get(0);
        this.pipeBlockPositions.clear();
        this.pipeBlockPositions.addAll(mainComponent);

        for (int i = 1; i < components.size(); i++) {
            Set<BlockPos> component = components.get(i);
            FluidPipeMultiblock newMultiblock = new FluidPipeMultiblock(UUID.randomUUID(), level);
            newMultiblock.pipeBlockPositions.addAll(component);
            distributeTankConnectionsForSplit(newMultiblock, component);
            distributePortalConnectionsForSplit(newMultiblock, component);

            Logger.sendMessage("Created new FluidPipeMultiblock " + newMultiblock.id.toString().substring(0, 8) +
                    " with " + component.size() + " pipes from split", true);
        }

        Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) +
                " now has " + pipeBlockPositions.size() + " pipes after split", true);
    }

    private void distributeTankConnectionsForSplit(FluidPipeMultiblock newMultiblock, Set<BlockPos> component) {
        Iterator<Map.Entry<UUID, Set<BlockPos>>> iterator = connectedTankMultiblocksMap.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Set<BlockPos>> entry = iterator.next();
            UUID tankId = entry.getKey();
            Set<BlockPos> connectionPoints = entry.getValue();

            Set<BlockPos> pointsForNewMultiblock = new HashSet<>();
            for (BlockPos point : connectionPoints) {
                if (component.contains(point)) {
                    pointsForNewMultiblock.add(point);
                }
            }

            if (!pointsForNewMultiblock.isEmpty()) {
                connectionPoints.removeAll(pointsForNewMultiblock);
                newMultiblock.connectedTankMultiblocksMap.put(tankId, pointsForNewMultiblock);

                TankMultiblock tank = getTankById(tankId);
                if (tank != null) {
                    newMultiblock.connectedTankMultiblocks.add(tank);
                }

                if (connectionPoints.isEmpty()) {
                    iterator.remove();
                    connectedTankMultiblocks.removeIf(tank1 -> tank.getMultiblockId().equals(tankId));
                }

                Logger.sendMessage("Transferred " + pointsForNewMultiblock.size() + " tank connection points to new multiblock for tank " +
                        tankId.toString().substring(0, 8), true);
            }
        }
    }

    private void distributePortalConnectionsForSplit(FluidPipeMultiblock newMultiblock, Set<BlockPos> component) {
        Set<PortalStructure> portalsForNewMultiblock = new HashSet<>();

        for (BlockPos pipePos : component) {
            Set<PortalStructure> adjacentPortals = findAdjacentPortalStructures(pipePos, level);
            portalsForNewMultiblock.addAll(adjacentPortals);
        }

        for (PortalStructure portal : portalsForNewMultiblock) {
            newMultiblock.connectedPortalStructures.add(portal);
            portal.getFluidPipeMultiblocks().add(newMultiblock);
            Logger.sendMessage("Transferred portal connection to new multiblock for portal " +
                    portal.getPortalId().toString().substring(0, 8), true);
        }
    }

    private TankMultiblock getTankById(UUID tankId) {
        for (TankMultiblock tank : connectedTankMultiblocks) {
            if (tank.getMultiblockId().equals(tankId)) {
                return tank;
            }
        }
        return null;
    }

    public void addPipePosition(BlockPos pos) {
        pipeBlockPositions.add(pos);

        if (level != null) {
            Block block = level.getBlockState(pos).getBlock();
            if (block instanceof PortalFluidPipeBlock) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity instanceof PortalFluidPipeBlockEntity) {
                    ((PortalFluidPipeBlockEntity) blockEntity).setMultiblock(this);
                }
                connectedPipes.add((PortalFluidPipeBlock) block);
            }
        }

        Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) +
                " added pipe at " + pos + " (total: " + pipeBlockPositions.size() + " pipes)", true);
    }

    public void addTankConnection(TankMultiblock tank, BlockPos pipePos) {
        if (tank == null) return;

        UUID tankId = tank.getMultiblockId();

        if (connectedTankMultiblocksMap.containsKey(tankId) &&
                connectedTankMultiblocksMap.get(tankId).contains(pipePos)) {
            Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) +
                    " already connected to TankMultiblock " + tankId.toString().substring(0, 8) +
                    " at " + pipePos, true);
            return;
        }

        boolean isNewConnection = !connectedTankMultiblocksMap.containsKey(tankId);

        if (!connectedTankMultiblocksMap.containsKey(tankId)) {
            connectedTankMultiblocksMap.put(tankId, new HashSet<>());
        }
        connectedTankMultiblocksMap.get(tankId).add(pipePos);
        connectedTankMultiblocks.add(tank);

        if (isNewConnection) {
            Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) +
                    " CONNECTED to TankMultiblock " + tankId.toString().substring(0, 8) +
                    " via pipe at " + pipePos, true);
            tank.addPipeConnection(this, pipePos);
        } else {
            Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) +
                    " added connection to TankMultiblock " + tankId.toString().substring(0, 8) +
                    " at " + pipePos + " (" + connectedTankMultiblocksMap.get(tankId).size() + " total connections)", true);
        }
    }

    public void removeTankConnection(TankMultiblock tank, BlockPos pipePos) {
        if (tank == null) return;

        UUID tankId = tank.getMultiblockId();

        if (connectedTankMultiblocksMap.containsKey(tankId)) {
            Set<BlockPos> connectionPoints = connectedTankMultiblocksMap.get(tankId);
            boolean hadConnection = connectionPoints.contains(pipePos);
            connectionPoints.remove(pipePos);

            if (connectionPoints.isEmpty()) {
                connectedTankMultiblocksMap.remove(tankId);
                connectedTankMultiblocks.remove(tank);
                Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) +
                        " DISCONNECTED from TankMultiblock " + tankId.toString().substring(0, 8) +
                        " (no more connection points)", true);
            } else if (hadConnection) {
                Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) +
                        " removed connection point to TankMultiblock " + tankId.toString().substring(0, 8) +
                        " at " + pipePos + " (" + connectionPoints.size() + " connection points remain)", true);
            }
        }
    }

    public void mergeWith(FluidPipeMultiblock other) {
        this.pipeBlockPositions.addAll(other.pipeBlockPositions);
        this.connectedPipes.addAll(other.connectedPipes);
        this.connectedPortalStructures.addAll(other.connectedPortalStructures);
        this.connectedTankMultiblocks.addAll(other.connectedTankMultiblocks);

        for (Map.Entry<UUID, Set<BlockPos>> entry : other.connectedTankMultiblocksMap.entrySet()) {
            UUID tankId = entry.getKey();
            Set<BlockPos> pipePositions = entry.getValue();

            if (!this.connectedTankMultiblocksMap.containsKey(tankId)) {
                this.connectedTankMultiblocksMap.put(tankId, new HashSet<>());
            }
            this.connectedTankMultiblocksMap.get(tankId).addAll(pipePositions);
        }

        for (PortalStructure portal : other.connectedPortalStructures) {
            portal.fluidPipeMultiblocks.remove(other);
            portal.fluidPipeMultiblocks.add(this);
        }

        Logger.sendMessage("FluidPipeMultiblock " + id.toString().substring(0, 8) +
                " merged with " + other.id.toString().substring(0, 8) + " - added " +
                other.pipeBlockPositions.size() + " pipes, " + other.connectedTankMultiblocksMap.size() +
                " tank connections, " + other.connectedPortalStructures.size() + " portal connections", true);
    }

    public Set<UUID> getConnectedTankIds() {
        return connectedTankMultiblocksMap.keySet();
    }

    public boolean isConnectedToTank(UUID tankId) {
        return connectedTankMultiblocksMap.containsKey(tankId) &&
                !connectedTankMultiblocksMap.get(tankId).isEmpty();
    }

    public Set<BlockPos> getPipeBlockPositions() {
        return Collections.unmodifiableSet(pipeBlockPositions);
    }

    @Override
    public String toString() {
        return String.format("FluidPipeMultiblock[%s: %d pipes, Connected Tanks: %d, Connected Portals: %d]",
                id.toString().substring(0, 8),
                pipeBlockPositions.size(),
                connectedTankMultiblocksMap.size(),
                connectedPortalStructures.size());
    }
}
