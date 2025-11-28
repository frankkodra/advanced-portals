package portal_fluid_tank;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import portal_fluid_pipe.PortalFluidPipeBlockEntity;
import portal_multiblock.PortalMultiblockManager;
import advanced_portals.Logger;
import portal_fluid_pipe.FluidPipeMultiblock;

import java.util.*;

public class TankMultiblock {
    private final UUID multiblockId;
    private final Set<BlockPos> tankBlocks;
    public Set<UUID> connectedPortalsId;
    private Level level = null;

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
        Set<FluidPipeMultiblock> adjacentPipes = findAdjacentFluidPipes(pos, level);

        Logger.sendMessage("Placing tank at " + pos + " - Found " + adjacentMultiblocks.size() +
                " adjacent TankMultiblocks, " + adjacentPipes.size() + " pipes", true);

        TankMultiblock resultMultiblock;

        if (adjacentMultiblocks.isEmpty()) {
            resultMultiblock = new TankMultiblock(UUID.randomUUID(), level);
            resultMultiblock.addTank(pos);
            Logger.sendMessage("New TankMultiblock " + resultMultiblock.multiblockId.toString().substring(0, 8) +
                    " created with 1 tank at " + pos, true);
        }
        else {
            resultMultiblock = mergeAllAdjacentMultiblocks(adjacentMultiblocks);
            resultMultiblock.addTank(pos);
            Logger.sendMessage("TankMultiblock " + resultMultiblock.multiblockId.toString().substring(0, 8) +
                    " added tank at " + pos + " (total: " + resultMultiblock.tankBlocks.size() + " tanks)", true);
        }

        updateBlockEntityMultiblockReference(pos, level, resultMultiblock);

        for (FluidPipeMultiblock pipe : adjacentPipes) {
            resultMultiblock.addPipeConnection(pipe, pos);
        }

        return resultMultiblock;
    }

    public void addTank(BlockPos pos) {
        tankBlocks.add(pos);
        Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) +
                " added tank at " + pos + " (total: " + tankBlocks.size() + " tanks)", true);
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
                    Logger.sendMessage("Found adjacent tank at " + neighborPos + " belonging to TankMultiblock " +
                            neighborMultiblock.multiblockId.toString().substring(0, 8), true);
                } else {
                    Logger.sendMessage("WARNING: Tank at " + neighborPos + " has null multiblock reference in block entity!", true);
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
            Logger.sendMessage("Updated tank block entity reference at " + pos + " to TankMultiblock " +
                    multiblock.multiblockId.toString().substring(0, 8), true);
        } else {
            Logger.sendMessage("ERROR: No tank block entity found at " + pos + " to update multiblock reference!", true);
        }
    }

    private static Set<FluidPipeMultiblock> findAdjacentFluidPipes(BlockPos pos, Level level) {
        Set<FluidPipeMultiblock> pipes = new HashSet<>();

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockEntity block = level.getBlockEntity(neighborPos);

            if (block instanceof PortalFluidPipeBlockEntity) {
                FluidPipeMultiblock pipeMultiblock = ((PortalFluidPipeBlockEntity) block).getMultiblock();
                if (pipeMultiblock != null) {
                    pipes.add(pipeMultiblock);
                    Logger.sendMessage("Found adjacent fluid pipe at " + neighborPos + " belonging to FluidPipeMultiblock " +
                            pipeMultiblock.id.toString().substring(0, 8), true);
                }
            }
        }
        return pipes;
    }

    private static TankMultiblock mergeAllAdjacentMultiblocks(Set<TankMultiblock> multiblocksToMerge) {
        if (multiblocksToMerge.isEmpty()) return null;

        Iterator<TankMultiblock> iterator = multiblocksToMerge.iterator();
        TankMultiblock mainMultiblock = iterator.next();

        if (multiblocksToMerge.size() == 1) {
            Logger.sendMessage("Only one adjacent TankMultiblock " + mainMultiblock.multiblockId.toString().substring(0, 8) +
                    " found", true);
            return mainMultiblock;
        }

        Logger.sendMessage("Merging " + multiblocksToMerge.size() + " TankMultiblocks into " +
                mainMultiblock.multiblockId.toString().substring(0, 8), true);

        int totalTanksBefore = mainMultiblock.tankBlocks.size();
        int totalPipeConnectionsBefore = mainMultiblock.connectedPipeMultiblocksMap.size();
        int totalFluidBefore = mainMultiblock.storedFluid;

        Set<BlockPos> allPositionsToUpdate = new HashSet<>();

        while (iterator.hasNext()) {
            TankMultiblock otherMultiblock = iterator.next();
            allPositionsToUpdate.addAll(otherMultiblock.tankBlocks);

            if (otherMultiblock.multiblockId.equals(mainMultiblock.multiblockId)) {
                continue;
            }

            int tanksToAdd = otherMultiblock.tankBlocks.size();
            int pipeConnectionsToAdd = otherMultiblock.connectedPipeMultiblocksMap.size();
            int fluidToAdd = otherMultiblock.storedFluid;

            mainMultiblock.mergeWith(otherMultiblock);
            PortalMultiblockManager.removeTankMultiblock(otherMultiblock);

            Logger.sendMessage("Merged TankMultiblock " + otherMultiblock.multiblockId.toString().substring(0, 8) +
                    " into " + mainMultiblock.multiblockId.toString().substring(0, 8) +
                    " (+" + tanksToAdd + " tanks, +" + pipeConnectionsToAdd + " pipe connections, +" + fluidToAdd + " mB)", true);
        }

        updateAllBlockEntityReferences(mainMultiblock, allPositionsToUpdate);

        int totalTanksAdded = mainMultiblock.tankBlocks.size() - totalTanksBefore;
        int totalPipeConnectionsAdded = mainMultiblock.connectedPipeMultiblocksMap.size() - totalPipeConnectionsBefore;
        int totalFluidAdded = mainMultiblock.storedFluid - totalFluidBefore;

        Logger.sendMessage("Tank merge complete: " + totalTanksAdded + " total tanks added, " +
                totalPipeConnectionsAdded + " pipe connections added, " + totalFluidAdded + " mB transferred", true);

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
        Logger.sendMessage("Updated " + updatedCount + " tank block entity references to TankMultiblock " +
                multiblock.multiblockId.toString().substring(0, 8), true);
    }

    public void handleTankBlockBreak(BlockPos removedPos) {
        Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) +
                " removing tank at " + removedPos + " (currently " + tankBlocks.size() + " tanks)", true);

        if (this.tankBlocks.remove(removedPos)) {
            removePipeConnectionsForTank(removedPos);

            if (tankBlocks.isEmpty()) {
                PortalMultiblockManager.removeTankMultiblock(this);
                Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) + " DESTROYED (no tanks remaining)", true);
                return;
            }

            List<Set<BlockPos>> disconnectedComponents = findDisconnectedComponents();
            if (disconnectedComponents.size() > 1) {
                Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) +
                        " splitting into " + disconnectedComponents.size() + " components", true);
                splitIntoComponents(disconnectedComponents);
            } else {
                Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) +
                        " now has " + tankBlocks.size() + " tanks (no split needed)", true);
            }
        }
    }

    private void removePipeConnectionsForTank(BlockPos tankPos) {
        Set<UUID> pipesToUpdate = new HashSet<>();

        for (Map.Entry<UUID, Set<BlockPos>> entry : connectedPipeMultiblocksMap.entrySet()) {
            UUID pipeId = entry.getKey();
            Set<BlockPos> connectionPoints = entry.getValue();

            if (connectionPoints.contains(tankPos)) {
                pipesToUpdate.add(pipeId);
            }
        }

        for (UUID pipeId : pipesToUpdate) {
            Set<BlockPos> connectionPoints = connectedPipeMultiblocksMap.get(pipeId);
            if (connectionPoints != null) {
                connectionPoints.remove(tankPos);
                if (connectionPoints.isEmpty()) {
                    connectedPipeMultiblocksMap.remove(pipeId);
                    Logger.sendMessage("Disconnected from FluidPipeMultiblock " + pipeId.toString().substring(0, 8) +
                            " (no more connection points)", true);
                } else {
                    Logger.sendMessage("Removed connection point to FluidPipeMultiblock " + pipeId.toString().substring(0, 8) +
                            " at " + tankPos + " (" + connectionPoints.size() + " connection points remain)", true);
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
        Set<BlockPos> mainComponent = components.get(0);
        this.tankBlocks.clear();
        this.tankBlocks.addAll(mainComponent);
        redistributeFluidForSplit(components);

        for (int i = 1; i < components.size(); i++) {
            Set<BlockPos> component = components.get(i);
            TankMultiblock newMultiblock = new TankMultiblock(UUID.randomUUID(), level);
            newMultiblock.tankBlocks.addAll(component);
            distributePipeConnectionsForSplit(newMultiblock, component);

            Logger.sendMessage("Created new TankMultiblock " + newMultiblock.multiblockId.toString().substring(0, 8) +
                    " with " + component.size() + " tanks from split", true);
        }

        updateAllBlockEntityReferences(this, mainComponent);

        Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) +
                " now has " + tankBlocks.size() + " tanks after split", true);
    }

    private void redistributeFluidForSplit(List<Set<BlockPos>> components) {
        int totalTanks = components.stream().mapToInt(Set::size).sum();
        if (totalTanks == 0) return;

        int originalFluid = this.storedFluid;
        this.storedFluid = (originalFluid * this.tankBlocks.size()) / totalTanks;

        Logger.sendMessage("Redistributed fluid: " + originalFluid + " mB -> " + this.storedFluid + " mB for main component", true);
    }

    private void distributePipeConnectionsForSplit(TankMultiblock newMultiblock, Set<BlockPos> component) {
        Iterator<Map.Entry<UUID, Set<BlockPos>>> iterator = connectedPipeMultiblocksMap.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Set<BlockPos>> entry = iterator.next();
            UUID pipeId = entry.getKey();
            Set<BlockPos> connectionPoints = entry.getValue();

            Set<BlockPos> pointsForNewMultiblock = new HashSet<>();
            for (BlockPos point : connectionPoints) {
                if (component.contains(point)) {
                    pointsForNewMultiblock.add(point);
                }
            }

            if (!pointsForNewMultiblock.isEmpty()) {
                connectionPoints.removeAll(pointsForNewMultiblock);
                newMultiblock.connectedPipeMultiblocksMap.put(pipeId, pointsForNewMultiblock);

                if (connectionPoints.isEmpty()) {
                    iterator.remove();
                }
                updateAllBlockEntityReferences(newMultiblock, component);

                Logger.sendMessage("Transferred " + pointsForNewMultiblock.size() + " pipe connection points to new multiblock for pipe " +
                        pipeId.toString().substring(0, 8), true);
            }
        }
    }

    public void addPipeConnection(FluidPipeMultiblock pipe, BlockPos tankPos) {
        if (pipe == null) return;

        UUID pipeId = pipe.id;

        if (connectedPipeMultiblocksMap.containsKey(pipeId) &&
                connectedPipeMultiblocksMap.get(pipeId).contains(tankPos)) {
            Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) +
                    " already connected to FluidPipeMultiblock " + pipeId.toString().substring(0, 8) +
                    " at " + tankPos, true);
            return;
        }

        boolean isNewConnection = !connectedPipeMultiblocksMap.containsKey(pipeId);

        if (!connectedPipeMultiblocksMap.containsKey(pipeId)) {
            connectedPipeMultiblocksMap.put(pipeId, new HashSet<>());
        }
        connectedPipeMultiblocksMap.get(pipeId).add(tankPos);

        if (isNewConnection) {
            Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) +
                    " CONNECTED to FluidPipeMultiblock " + pipeId.toString().substring(0, 8) +
                    " via tank at " + tankPos, true);
            pipe.addTankConnection(this, tankPos);
        } else {
            Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) +
                    " added connection to FluidPipeMultiblock " + pipeId.toString().substring(0, 8) +
                    " at " + tankPos + " (" + connectedPipeMultiblocksMap.get(pipeId).size() + " total connections)", true);
        }
    }

    public void removePipeConnection(FluidPipeMultiblock pipe, BlockPos tankPos) {
        if (pipe == null) return;

        UUID pipeId = pipe.id;

        if (connectedPipeMultiblocksMap.containsKey(pipeId)) {
            Set<BlockPos> connectionPoints = connectedPipeMultiblocksMap.get(pipeId);
            boolean hadConnection = connectionPoints.contains(tankPos);
            connectionPoints.remove(tankPos);

            if (connectionPoints.isEmpty()) {
                connectedPipeMultiblocksMap.remove(pipeId);
                Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) +
                        " DISCONNECTED from FluidPipeMultiblock " + pipeId.toString().substring(0, 8) +
                        " (no more connection points)", true);
            } else if (hadConnection) {
                Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) +
                        " removed connection point to FluidPipeMultiblock " + pipeId.toString().substring(0, 8) +
                        " at " + tankPos + " (" + connectionPoints.size() + " connection points remain)", true);
            }
        }
    }

    public void mergeWith(TankMultiblock other) {
        this.tankBlocks.addAll(other.tankBlocks);
        this.connectedPortalsId.addAll(other.connectedPortalsId);
        this.storedFluid += other.storedFluid;

        for (Map.Entry<UUID, Set<BlockPos>> entry : other.connectedPipeMultiblocksMap.entrySet()) {
            UUID pipeId = entry.getKey();
            Set<BlockPos> tankPositions = entry.getValue();

            if (!this.connectedPipeMultiblocksMap.containsKey(pipeId)) {
                this.connectedPipeMultiblocksMap.put(pipeId, new HashSet<>());
            }
            this.connectedPipeMultiblocksMap.get(pipeId).addAll(tankPositions);
        }

        Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) +
                " merged with " + other.multiblockId.toString().substring(0, 8) + " - added " +
                other.tankBlocks.size() + " tanks, " + other.connectedPipeMultiblocksMap.size() +
                " pipe connections, " + other.storedFluid + " mB", true);
    }

    public boolean connectToPortal(UUID portalId) {
        this.connectedPortalsId.add(portalId);
        Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) +
                " connected to portal " + portalId.toString().substring(0, 8), true);
        return true;
    }

    public void disconnectFromPortal(UUID portalId) {
        this.connectedPortalsId.remove(portalId);
        Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) +
                " disconnected from portal " + portalId.toString().substring(0, 8), true);
    }

    public int consumeFluid(int amount) {
        int fluidToConsume = Math.min(amount, storedFluid);
        storedFluid -= fluidToConsume;
        Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) +
                " consumed " + fluidToConsume + " mB (remaining: " + storedFluid + "/" + getMaxCapacity() + " mB)", true);
        return fluidToConsume;
    }

    public int addFluid(int amount) {
        int capacity = getMaxCapacity();
        int spaceAvailable = capacity - storedFluid;
        int fluidToAdd = Math.min(amount, spaceAvailable);
        storedFluid += fluidToAdd;
        Logger.sendMessage("TankMultiblock " + multiblockId.toString().substring(0, 8) +
                " added " + fluidToAdd + " mB (now: " + storedFluid + "/" + getMaxCapacity() + " mB)", true);
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
        return connectedPipeMultiblocksMap.containsKey(pipeId) &&
                !connectedPipeMultiblocksMap.get(pipeId).isEmpty();
    }

    @Override
    public String toString() {
        return String.format("TankMultiblock[%s: %d tanks, %d/%d mB, Connected Pipes: %d, Connected Portals: %d]",
                multiblockId.toString().substring(0, 8),
                tankBlocks.size(), storedFluid, getMaxCapacity(),
                connectedPipeMultiblocksMap.size(), connectedPortalsId.size());
    }
}