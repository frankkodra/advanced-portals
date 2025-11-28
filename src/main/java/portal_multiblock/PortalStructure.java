package portal_multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Block;
import portal_battery.BatteryMultiblock;
import portal_battery.PortalBatteryBlock;
import portal_block.PortalBlock;
import portal_controller.PortalControllerBlock;
import portal_fluid_tank.TankMultiblock;
import portal_multiblock.PortalMultiblockManager;
import portal_power_cable.PowerCableMultiblock;

import javax.sound.sampled.Port;
import java.util.*;

public class PortalStructure {
    private final UUID portalId;
    private String portalName;
    public Set<PortalControllerBlock> portalControllers;
    private final Set<BlockPos> frameBlocks;
    private final Set<BlockPos> interiorBlocks;
    public final Set<PowerCableMultiblock> powerCablesMultiblocks;
    private final Set<BlockPos> fluidPipes;
    private boolean isValid;
    private boolean isActive;
    private final Level level;
    private final List<UUID> linkedPortals;
    private ResourceKey<Level> levelKey;
    // Sub-multiblock management
    private final Set<UUID> connectedBatteryMultiblocks;
    private final Set<UUID> connectedTankMultiblocks;

    public PortalStructure(UUID portalId, Level level) {
        this.portalId = portalId;
        this.portalName = "Portal_" + portalId.toString().substring(0, 8);
        this.frameBlocks = new HashSet<>();
        this.interiorBlocks = new HashSet<>();
        this.powerCablesMultiblocks = new HashSet<>();
        this.fluidPipes = new HashSet<>();
        this.linkedPortals = new ArrayList<>();
        this.connectedBatteryMultiblocks = new HashSet<>();
        this.connectedTankMultiblocks = new HashSet<>();
        this.level = level;
        this.isValid = false;
        this.isActive = false;
        this.levelKey = level.dimension();
        this.portalControllers = new HashSet<>();
    }

    public static PortalStructure addCreateOrMergePortalStructureForBlock(BlockPos pos, Level level) {
        Set<PortalStructure> multiblocksToMerge=new HashSet<PortalStructure>();
        Block addedBlock = level.getBlockState(pos).getBlock();
        for(Direction direction : Direction.values()) {
            Block block = level.getBlockState(pos.relative(direction)).getBlock();
            if(block instanceof PortalBlock ) {
                multiblocksToMerge.add (((PortalBlock) level.getBlockState(pos.relative(direction)).getBlock()).portalStructure);
            }
            if(block instanceof PortalControllerBlock) {
                multiblocksToMerge.add( ((PortalControllerBlock) level.getBlockState(pos.relative(direction)).getBlock()).portalStructure);
            }
        }
        if(addedBlock instanceof PortalBlock) {
            if(multiblocksToMerge.size()==0) {
            PortalStructure portalStructure = new PortalStructure(UUID.randomUUID(),level);
            portalStructure.addPortalBlock(pos);
            return portalStructure;
             }
             if(multiblocksToMerge.size()==1) {

            PortalStructure multiblock = multiblocksToMerge.iterator().next();
            multiblock.addPortalBlock(pos);
            return multiblock;
            }
            if(multiblocksToMerge.size()>1) {
            PortalStructure multiblock= mergeMultiblocks(multiblocksToMerge,level);
            multiblock.addPortalBlock(pos);
            return multiblock;
             }}
        if(addedBlock instanceof PortalControllerBlock) {
            if(multiblocksToMerge.size()==0) {
                PortalStructure portalStructure = new PortalStructure(UUID.randomUUID(),level);
                portalStructure.addPortalControllerBlock(pos);
                return portalStructure;
            }
            if(multiblocksToMerge.size()==1) {

                PortalStructure multiblock = multiblocksToMerge.iterator().next();
                multiblock.addPortalControllerBlock(pos);
                return multiblock;
            }
            if(multiblocksToMerge.size()>1) {
                PortalStructure multiblock =  mergeMultiblocks(multiblocksToMerge,level);
                multiblock.addPortalControllerBlock(pos);
                return multiblock;
            }
        }

        return null;
    }

    private void addPortalControllerBlock(BlockPos pos) {
        PortalControllerBlock block = (PortalControllerBlock)level.getBlockState(pos).getBlock();
        portalControllers.add(block);
    }

    public static PortalStructure mergeMultiblocks(Set<PortalStructure> multiblocksToMerge,Level level) {

            PortalStructure mainStructureMultiblock = multiblocksToMerge.iterator().next();
            UUID mainId= mainStructureMultiblock.portalId;
            for (PortalStructure portalStructure : multiblocksToMerge) {
                if(portalStructure.portalId.equals(mainId)) {

                }
                else {
                    mainStructureMultiblock.addPortalBlocks(portalStructure.frameBlocks);
                    mainStructureMultiblock.addPortalControllerBlocks(portalStructure.portalControllers);
                    PortalMultiblockManager.removePortalStructure(portalStructure);
                    for(BlockPos pb:portalStructure.frameBlocks){
                        ((PortalBlock) level.getBlockState(pb).getBlock()).setPortalStructure(mainStructureMultiblock);
                    }


                }
            }
            return mainStructureMultiblock;


    }

    private void addPortalControllerBlocks(Set<PortalControllerBlock> portalControllers) {
        portalControllers.addAll(portalControllers);
    }

    private void addPortalBlocks(Set<BlockPos> frameBlocks) {
        frameBlocks.addAll(frameBlocks);
    }

    public void addPortalBlock(BlockPos pos) {
        frameBlocks.add(pos);
    }




    // Sub-multiblock connection management
    public boolean connectBatteryMultiblock(UUID batteryMultiblockId) {
        return connectedBatteryMultiblocks.add(batteryMultiblockId);
    }

    public boolean disconnectBatteryMultiblock(UUID batteryMultiblockId) {
        return connectedBatteryMultiblocks.remove(batteryMultiblockId);
    }

    public boolean connectTankMultiblock(UUID tankMultiblockId) {
        return connectedTankMultiblocks.add(tankMultiblockId);
    }

    public boolean disconnectTankMultiblock(UUID tankMultiblockId) {
        return connectedTankMultiblocks.remove(tankMultiblockId);
    }

    // Power management with round-robin consumption
    public boolean consumePower(int amount) {
        if (getCurrentPower() < amount) {
            return false;
        }

        int remaining = amount;
        List<UUID> batteryList = new ArrayList<>(connectedBatteryMultiblocks);

        // Round-robin consumption
        while (remaining > 0 && !batteryList.isEmpty()) {
            for (UUID batteryId : batteryList) {
                BatteryMultiblock battery = PortalMultiblockManager.batteryMultiblocks.get(batteryId);
                if (battery != null) {
                    int consumed = battery.consumeEnergy(remaining);
                    remaining -= consumed;
                    if (remaining <= 0) break;
                }
            }
        }

        return remaining == 0;
    }



    // Fluid management with round-robin consumption
    public boolean consumeFluid(int amount) {
        if (getCurrentFluid() < amount) {
            return false;
        }

        int remaining = amount;
        List<UUID> tankList = new ArrayList<>(connectedTankMultiblocks);

        while (remaining > 0 && !tankList.isEmpty()) {
            for (UUID tankId : tankList) {
                TankMultiblock tank = PortalMultiblockManager.tankMultiblocks.get(tankId);
                if (tank != null) {
                    int consumed = tank.consumeFluid(remaining);
                    remaining -= consumed;
                    if (remaining <= 0) break;
                }
            }
        }

        return remaining == 0;
    }



    // Aggregate power/fluid stats
    public int getCurrentPower() {
        int total = 0;
        for (UUID batteryId : connectedBatteryMultiblocks) {
            BatteryMultiblock battery = PortalMultiblockManager.batteryMultiblocks.get(batteryId);
            if (battery != null) {
                total += battery.getStoredEnergy();
            }
        }
        return total;
    }

    public int getMaxPowerCapacity() {
        int total = 0;
        for (UUID batteryId : connectedBatteryMultiblocks) {
            BatteryMultiblock battery = PortalMultiblockManager.batteryMultiblocks.get(batteryId);
            if (battery != null) {
                total += battery.getMaxCapacity();
            }
        }
        return total;
    }

    public int getCurrentFluid() {
        int total = 0;
        for (UUID tankId : connectedTankMultiblocks) {
            TankMultiblock tank = PortalMultiblockManager.tankMultiblocks.get(tankId);
            if (tank != null) {
                total += tank.getStoredFluid();
            }
        }
        return total;
    }

    public int getMaxFluidCapacity() {
        int total = 0;
        for (UUID tankId : connectedTankMultiblocks) {
            TankMultiblock tank = PortalMultiblockManager.tankMultiblocks.get(tankId);
            if (tank != null) {
                total += tank.getMaxCapacity();
            }
        }
        return total;
    }



   public void revalidateStructure() {
        // Clear previous state but keep connected blocks
        frameBlocks.clear();
        interiorBlocks.clear();
        isValid = false;

        // If we lost our controller, portal is invalid
        if (portalControllers.size() >=1 ) {
            setActive(false);
            return;
        }

        // Separate frame blocks from other blocks


        // Run validation
        isValid = validatePortalStructure();

        if (isValid) {
            calculateInteriorBlocks();
            // Portal can be activated if valid and has power (if power system required)
            if (isActive) {
                activatePortal();
            }
        } else {
            setActive(false);
        }
    }



    private boolean validatePortalStructure() {
        // Check if we have a controller
        if (portalControllers.size() >=1 ) {
            return false;
        }

        // Check if controller is adjacent to frame


        // Validate frame structure
        if (!validateRectangleFrame()) {
            return false;
        }

        // Check that power cables and fluid pipes are connected to frame
        if (!validateAttachmentBlocks()) {
            return false;
        }

        return true;
    }

    private boolean validateRectangleFrame() {
        if (frameBlocks.size() < 10) { // Minimum for 3x4 frame perimeter
            return false;
        }

        // Group frames by Y level (portals are flat structures)
        Map<Integer, Set<BlockPos>> framesByLevel = new HashMap<>();
        for (BlockPos pos : frameBlocks) {
            framesByLevel.computeIfAbsent(pos.getY(), k -> new HashSet<>()).add(pos);
        }

        // We only support single-level portals for now
        if (framesByLevel.size() != 1) {
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

        // Must be at least 3x4 or 4x3
        if (!((width >= 3 && height >= 4) || (width >= 4 && height >= 3))) {
            return false;
        }

        // Verify we have a complete rectangular frame
        return verifyCompleteRectangleFrame(minX, maxX, minZ, maxZ, yLevel, levelFrames);
    }

    private boolean verifyCompleteRectangleFrame(int minX, int maxX, int minZ, int maxZ, int yLevel, Set<BlockPos> levelFrames) {
        // Check all four edges are completely filled with frame blocks
        for (int x = minX; x <= maxX; x++) {
            // Bottom edge
            if (!levelFrames.contains(new BlockPos(x, yLevel, minZ))) {
                return false;
            }
            // Top edge
            if (!levelFrames.contains(new BlockPos(x, yLevel, maxZ))) {
                return false;
            }
        }

        for (int z = minZ; z <= maxZ; z++) {
            // Left edge
            if (!levelFrames.contains(new BlockPos(minX, yLevel, z))) {
                return false;
            }
            // Right edge
            if (!levelFrames.contains(new BlockPos(maxX, yLevel, z))) {
                return false;
            }
        }

        // Verify no frame blocks outside the rectangle
        for (BlockPos pos : levelFrames) {
            if (pos.getX() < minX || pos.getX() > maxX || pos.getZ() < minZ || pos.getZ() > maxZ) {
                return false;
            }
        }

        // Verify interior is empty (no frame blocks inside)
        for (int x = minX + 1; x < maxX; x++) {
            for (int z = minZ + 1; z < maxZ; z++) {
                if (levelFrames.contains(new BlockPos(x, yLevel, z))) {
                    return false; // Frame block in interior
                }
            }
        }

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
    }

    private boolean validateAttachmentBlocks() {
        // Power cables and fluid pipes must be adjacent to frame blocks


        return true;
    }

    private boolean isControllerAdjacentToFrame(BlockPos pos) {
        // Check if this block is adjacent to any frame block (including diagonals)
        for (BlockPos framePos : frameBlocks) {
            double distance = pos.distToCenterSqr((Position) framePos);
            if (distance <= 1.0) { // Adjacent: 1 block away (1.0) or diagonal (2.0)
                return true;
            }
        }
        return false;
    }

    public void activatePortal() {
        if (isValid && !isActive) {
            // Here you would place PortalTeleportBlock in all interior positions
            // For now, we just set the active state
            setActive(true);

            // In actual implementation:
            // for (BlockPos interiorPos : interiorBlocks) {
            //     level.setBlock(interiorPos, PortalTeleportBlock.defaultBlockState(), 3);
            // }
        }
    }

    public void deactivatePortal() {
        if (isActive) {
            setActive(false);

            // In actual implementation:
            // for (BlockPos interiorPos : interiorBlocks) {
            //     level.setBlock(interiorPos, Blocks.AIR.defaultBlockState(), 3);
            // }
        }
    }

    public void setActive(boolean active) {
        boolean wasActive = this.isActive;
        this.isActive = active && this.isValid;

        // Handle state changes
        if (wasActive && !this.isActive) {
            // Portal was deactivated
            deactivatePortal();
        } else if (!wasActive && this.isActive) {
            // Portal was activated
            activatePortal();
        }
    }

    // Name management methods
    public void setPortalName(String name) {
        this.portalName = name;
    }

    public String getPortalName() {
        return portalName;
    }

    // Link management methods
    public void addLinkedPortal(UUID portalId) {
        if (!linkedPortals.contains(portalId) && !portalId.equals(this.portalId)) {
            linkedPortals.add(portalId);
        }
    }

    public void removeLinkedPortal(UUID portalId) {
        linkedPortals.remove(portalId);
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
    public Set<BlockPos> getFluidPipes() { return Collections.unmodifiableSet(fluidPipes); }
    public Level getLevel() { return level; }

    // Sub-multiblock getters
    public Set<UUID> getConnectedBatteryMultiblocks() { return Collections.unmodifiableSet(connectedBatteryMultiblocks); }
    public Set<UUID> getConnectedTankMultiblocks() { return Collections.unmodifiableSet(connectedTankMultiblocks); }

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

    public void removePortalBlock(BlockPos pos) {
        frameBlocks.remove(pos);
    }

    public static class PortalDimensions {
        public final int width;
        public final int height;

        public PortalDimensions(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public boolean canEntityFit() {
            return width >= 1 && height >= 2; // Minimum 1x2 for player
        }

        @Override
        public String toString() {
            return width + "x" + height;
        }
    }

    @Override
    public String toString() {
        return String.format("Portal[%s: %s, Valid: %s, Active: %s, Power: %d/%d, Fluid: %d/%d, Links: %d, Batteries: %d, Tanks: %d]",
                portalId.toString().substring(0, 8), portalName, isValid, isActive,
                getCurrentPower(), getMaxPowerCapacity(), getCurrentFluid(), getMaxFluidCapacity(),
                linkedPortals.size(), connectedBatteryMultiblocks.size(), connectedTankMultiblocks.size());
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
}
