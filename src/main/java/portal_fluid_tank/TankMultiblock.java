package portal_fluid_tank;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import java.util.*;

public class TankMultiblock {
    private final UUID multiblockId;
    private final Set<BlockPos> tankBlocks;
    private UUID connectedPortalId;
    private final Level level;
    private int storedFluid;
    private final int capacityPerTank = 16000; // 16 buckets per tank

    public TankMultiblock(UUID multiblockId, Level level) {
        this.multiblockId = multiblockId;
        this.tankBlocks = new HashSet<>();
        this.level = level;
        this.storedFluid = 0;
    }
    public boolean containsTank(BlockPos pos) {
        return tankBlocks.contains(pos);
    }

    public void addTank(BlockPos pos) {
        tankBlocks.add(pos);
    }

    public void removeTank(BlockPos pos) {
        tankBlocks.remove(pos);
    }

    public boolean connectToPortal(UUID portalId) {
        if (this.connectedPortalId != null && !this.connectedPortalId.equals(portalId)) {
            return false;
        }
        this.connectedPortalId = portalId;
        return true;
    }

    public void disconnectFromPortal() {
        this.connectedPortalId = null;
    }

    public int consumeFluid(int amount) {
        int fluidToConsume = Math.min(amount, storedFluid);
        storedFluid -= fluidToConsume;
        return fluidToConsume;
    }

    public int addFluid(int amount) {
        int capacity = getMaxCapacity();
        int spaceAvailable = capacity - storedFluid;
        int fluidToAdd = Math.min(amount, spaceAvailable);
        storedFluid += fluidToAdd;
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

    public UUID getConnectedPortalId() {
        return connectedPortalId;
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

    public boolean isConnected() {
        return connectedPortalId != null;
    }

    public boolean isAdjacentTo(BlockPos pos) {
        for (BlockPos tankPos : tankBlocks) {
            if (tankPos.distSqr(pos) <= 2.0) {
                return true;
            }
        }
        return false;
    }

    public void mergeWith(TankMultiblock other) {
        this.tankBlocks.addAll(other.tankBlocks);
        this.storedFluid += other.storedFluid;
    }

    // Split this multiblock into connected components
    public List<TankMultiblock> split() {
        List<Set<BlockPos>> components = findDisconnectedComponents();
        List<TankMultiblock> newMultiblocks = new ArrayList<>();

        if (components.size() > 1) {
            Set<BlockPos> mainComponent = components.get(0);
            Set<BlockPos> tanksToRemove = new HashSet<>(tankBlocks);
            tanksToRemove.removeAll(mainComponent);

            int totalCapacity = getMaxCapacity();
            int fluidPerTank = totalCapacity > 0 ? storedFluid / tankBlocks.size() : 0;

            for (BlockPos pos : tanksToRemove) {
                tankBlocks.remove(pos);
            }

            this.storedFluid = mainComponent.size() * fluidPerTank;

            for (int i = 1; i < components.size(); i++) {
                Set<BlockPos> component = components.get(i);
                TankMultiblock newMultiblock = new TankMultiblock(UUID.randomUUID(), level);

                for (BlockPos pos : component) {
                    newMultiblock.addTank(pos);
                }

                int componentFluid = component.size() * fluidPerTank;
                newMultiblock.addFluid(componentFluid);

                if (this.connectedPortalId != null) {
                    newMultiblock.connectToPortal(this.connectedPortalId);
                }

                newMultiblocks.add(newMultiblock);
            }
        }

        return newMultiblocks;
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

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dz == 0 && dy == 0) continue;

                    BlockPos neighbor = start.offset(dx, dy, dz);
                    if (tankBlocks.contains(neighbor)) {
                        floodFill(neighbor, component, visited);
                    }
                }
            }
        }
    }

    @Override
    public String toString() {
        return String.format("TankMultiblock[%s: %d tanks, %d/%d mB, Connected: %s]",
                multiblockId.toString().substring(0, 8),
                tankBlocks.size(), storedFluid, getMaxCapacity(),
                connectedPortalId != null ? "Yes" : "No");
    }
}