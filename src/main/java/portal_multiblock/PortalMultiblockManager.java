
package portal_multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import portal_battery.BatteryMultiblock;
import portal_fluid_pipe.FluidPipeMultiblock;
import portal_fluid_tank.TankMultiblock;
import portal_power_cable.PowerCableMultiblock;

import java.util.*;
@Mod.EventBusSubscriber(modid = "advanced_portals", bus = Mod.EventBusSubscriber.Bus.FORGE)

public class PortalMultiblockManager {
    // Portal management
    public  static Map<UUID, PortalStructure> portals = new HashMap<>();
    public  static Map<String, UUID> portalNameToId = new HashMap<>();

    // Sub-multiblock management - JUST REGISTRY
    public  static Map<UUID, BatteryMultiblock> batteryMultiblocks = new HashMap<>();
    public  static Map<UUID, PowerCableMultiblock> powerCableMultiblocks = new HashMap<>();
    public  static Map<UUID, TankMultiblock> tankMultiblocks = new HashMap<>();
    public  static Map<UUID, FluidPipeMultiblock> fluidPipeMultiblocks = new HashMap<>();


    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        batteryMultiblocks.clear();
        powerCableMultiblocks.clear();
        tankMultiblocks.clear();
        fluidPipeMultiblocks.clear();
        portals.clear();
        portalNameToId.clear();

    }

    // Core portal management
    public static PortalStructure createNewPortal(Level level, BlockPos initialBlock) {
        UUID portalId = UUID.randomUUID();
        PortalStructure portal = new PortalStructure(portalId, level);
        portals.put(portalId, portal);
        return portal;
    }

    // SIMPLE REGISTRY METHODS - No creation logic

    // Battery multiblock registry
    public static void addBatteryMultiblock(BatteryMultiblock batteryMultiblock) {
        batteryMultiblocks.put(batteryMultiblock.getMultiblockId(), batteryMultiblock);
    }

    public static void removeBatteryMultiblock(BatteryMultiblock batteryMultiblock) {
        batteryMultiblocks.remove(batteryMultiblock.getMultiblockId());
    }

    // Power cable multiblock registry
    public static void addPowerCableMultiblock(PowerCableMultiblock powerCableMultiblock) {
        powerCableMultiblocks.put(powerCableMultiblock.id, powerCableMultiblock);
    }

    public static void removePowerCableMultiblock(PowerCableMultiblock powerCableMultiblock) {
        powerCableMultiblocks.remove(powerCableMultiblock.id);
    }

    // Tank multiblock registry
    public static void addTankMultiblock(TankMultiblock tankMultiblock) {
        tankMultiblocks.put(tankMultiblock.getMultiblockId(), tankMultiblock);
    }

    public static void removeTankMultiblock(TankMultiblock tankMultiblock) {
        tankMultiblocks.remove(tankMultiblock.getMultiblockId());
    }

    // Fluid pipe multiblock registry
    public static void addFluidPipeMultiblock(FluidPipeMultiblock fluidPipeMultiblock) {
        fluidPipeMultiblocks.put(fluidPipeMultiblock.id, fluidPipeMultiblock);
    }

    public static void removeFluidPipeMultiblock(FluidPipeMultiblock fluidPipeMultiblock) {
        fluidPipeMultiblocks.remove(fluidPipeMultiblock.id);
    }

    // Portal structure registry
    public static void removePortalStructure(PortalStructure portalStructure) {
        portals.remove(portalStructure.getPortalId());
    }

    // SIMPLE GETTERS - No creation logic

    // Battery multiblock getters
    public static BatteryMultiblock getBatteryMultiblock(UUID id) {
        return batteryMultiblocks.get(id);
    }

    public static Collection<BatteryMultiblock> getAllBatteryMultiblocks() {
        return Collections.unmodifiableCollection(batteryMultiblocks.values());
    }

    // Power cable multiblock getters
    public static PowerCableMultiblock getPowerCableMultiblock(UUID id) {
        return powerCableMultiblocks.get(id);
    }

    public static Collection<PowerCableMultiblock> getAllPowerCableMultiblocks() {
        return Collections.unmodifiableCollection(powerCableMultiblocks.values());
    }

    // Tank multiblock getters
    public static TankMultiblock getTankMultiblock(UUID id) {
        return tankMultiblocks.get(id);
    }

    public static Collection<TankMultiblock> getAllTankMultiblocks() {
        return Collections.unmodifiableCollection(tankMultiblocks.values());
    }

    // Fluid pipe multiblock getters
    public static FluidPipeMultiblock getFluidPipeMultiblock(UUID id) {
        return fluidPipeMultiblocks.get(id);
    }

    public static Collection<FluidPipeMultiblock> getAllFluidPipeMultiblocks() {
        return Collections.unmodifiableCollection(fluidPipeMultiblocks.values());
    }

    // Portal structure getters
    public static PortalStructure getPortalStructure(UUID id) {
        return portals.get(id);
    }

    public static Collection<PortalStructure> getAllPortalStructures() {
        return Collections.unmodifiableCollection(portals.values());
    }

    // Name-based portal lookup
    public static PortalStructure getPortalByName(String name) {
        UUID portalId = portalNameToId.get(name);
        return portalId != null ? portals.get(portalId) : null;
    }

    // Portal name management
    public static boolean registerPortalName(String name, UUID portalId) {
        if (portalNameToId.containsKey(name)) {
            return false; // Name already taken
        }
        portalNameToId.put(name, portalId);
        return true;
    }

    public static void unregisterPortalName(String name) {
        portalNameToId.remove(name);
    }

    public static boolean updatePortalName(String oldName, String newName, UUID portalId) {
        if (oldName != null) {
            portalNameToId.remove(oldName);
        }
        if (newName != null && !newName.isEmpty()&&!portalNameToId.containsKey(newName)) {
            portalNameToId.put(newName, portalId);
            return true;
        }
        return false;
    }

    // Multiblock cleanup and maintenance methods
    public static void cleanupEmptyMultiblocks() {
        // Remove empty battery multiblocks
        batteryMultiblocks.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        // Remove empty power cable multiblocks
        powerCableMultiblocks.entrySet().removeIf(entry -> entry.getValue().getCableBlockPositions().isEmpty());

        // Remove empty tank multiblocks
        tankMultiblocks.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        // Remove empty fluid pipe multiblocks
        fluidPipeMultiblocks.entrySet().removeIf(entry -> entry.getValue().getPipeBlockPositions().isEmpty());
    }

    // Find multiblocks by position
    public static BatteryMultiblock findBatteryMultiblockAt(BlockPos pos, Level level) {
        for (BatteryMultiblock multiblock : batteryMultiblocks.values()) {
            if (multiblock.getLevel() == level && multiblock.getBatteryBlocks().contains(pos)) {
                return multiblock;
            }
        }
        return null;
    }

    public static PowerCableMultiblock findPowerCableMultiblockAt(BlockPos pos, Level level) {
        for (PowerCableMultiblock multiblock : powerCableMultiblocks.values()) {
            if (multiblock.getCableBlockPositions().contains(pos)) {
                return multiblock;
            }
        }
        return null;
    }

    public static TankMultiblock findTankMultiblockAt(BlockPos pos, Level level) {
        for (TankMultiblock multiblock : tankMultiblocks.values()) {
            if (multiblock.getLevel() == level && multiblock.getTankBlocks().contains(pos)) {
                return multiblock;
            }
        }
        return null;
    }

    public static FluidPipeMultiblock findFluidPipeMultiblockAt(BlockPos pos, Level level) {
        for (FluidPipeMultiblock multiblock : fluidPipeMultiblocks.values()) {
            if (multiblock.getPipeBlockPositions().contains(pos)) {
                return multiblock;
            }
        }
        return null;
    }

    // Statistics and debugging methods
    public static String getMultiblockStatistics() {
        return String.format(
                "Multiblocks - Batteries: %d, Power Cables: %d, Tanks: %d, Fluid Pipes: %d, Portals: %d",
                batteryMultiblocks.size(),
                powerCableMultiblocks.size(),
                tankMultiblocks.size(),
                fluidPipeMultiblocks.size(),
                portals.size()
        );
    }

    public static Map<String, Integer> getDetailedStatistics() {
        Map<String, Integer> stats = new HashMap<>();

        // Count blocks in each multiblock type
        int totalBatteryBlocks = batteryMultiblocks.values().stream()
                .mapToInt(m -> m.getBatteryBlocks().size())
                .sum();
        int totalPowerCableBlocks = powerCableMultiblocks.values().stream()
                .mapToInt(m -> m.getCableBlockPositions().size())
                .sum();
        int totalTankBlocks = tankMultiblocks.values().stream()
                .mapToInt(m -> m.getTankBlocks().size())
                .sum();
        int totalFluidPipeBlocks = fluidPipeMultiblocks.values().stream()
                .mapToInt(m -> m.getPipeBlockPositions().size())
                .sum();

        stats.put("battery_multiblocks", batteryMultiblocks.size());
        stats.put("power_cable_multiblocks", powerCableMultiblocks.size());
        stats.put("tank_multiblocks", tankMultiblocks.size());
        stats.put("fluid_pipe_multiblocks", fluidPipeMultiblocks.size());
        stats.put("portals", portals.size());
        stats.put("total_battery_blocks", totalBatteryBlocks);
        stats.put("total_power_cable_blocks", totalPowerCableBlocks);
        stats.put("total_tank_blocks", totalTankBlocks);
        stats.put("total_fluid_pipe_blocks", totalFluidPipeBlocks);

        return stats;
    }

    // Level cleanup - remove all multiblocks from a specific level
    public static void cleanupLevel(Level level) {
        // Clean up battery multiblocks
        batteryMultiblocks.entrySet().removeIf(entry -> entry.getValue().getLevel() == level);

        // Clean up tank multiblocks
        tankMultiblocks.entrySet().removeIf(entry -> entry.getValue().getLevel() == level);

        // For cable and pipe multiblocks, we need to check if they have any blocks in the level
        powerCableMultiblocks.entrySet().removeIf(entry -> {
            PowerCableMultiblock multiblock = entry.getValue();
            return multiblock.getCableBlockPositions().stream()
                    .allMatch(pos -> level.getBlockState(pos).getBlock() instanceof portal_power_cable.PortalPowerCableBlock);
        });

        fluidPipeMultiblocks.entrySet().removeIf(entry -> {
            FluidPipeMultiblock multiblock = entry.getValue();
            return multiblock.getPipeBlockPositions().stream()
                    .allMatch(pos -> level.getBlockState(pos).getBlock() instanceof portal_fluid_pipe.PortalFluidPipeBlock);
        });

        // Clean up portals
        portals.entrySet().removeIf(entry -> entry.getValue().getLevel() == level);
    }

    // Validation methods for debugging
    public static List<String> validateMultiblockIntegrity() {
        List<String> issues = new ArrayList<>();

        // Validate battery multiblocks
        for (BatteryMultiblock battery : batteryMultiblocks.values()) {
            if (battery.isEmpty()) {
                issues.add("Empty battery multiblock: " + battery.getMultiblockId());
            }
            for (BlockPos pos : battery.getBatteryBlocks()) {
                if (battery.getLevel().getBlockEntity(pos) == null) {
                    issues.add("Battery multiblock " + battery.getMultiblockId() + " has invalid block at " + pos);
                }
            }
        }

        // Validate tank multiblocks
        for (TankMultiblock tank : tankMultiblocks.values()) {
            if (tank.isEmpty()) {
                issues.add("Empty tank multiblock: " + tank.getMultiblockId());
            }
            for (BlockPos pos : tank.getTankBlocks()) {
                if (tank.getLevel().getBlockEntity(pos) == null) {
                    issues.add("Tank multiblock " + tank.getMultiblockId() + " has invalid block at " + pos);
                }
            }
        }

        return issues;
    }

    public static void addPortalStructure(PortalStructure portalStructure) {
        portals.put(portalStructure.getPortalId(), portalStructure);
    }
    public static boolean isNameTaken(String name, UUID excludeId) {
        UUID existingId = portalNameToId.get(name);
        return existingId != null && !existingId.equals(excludeId);
    }

    /**
     * Renames a portal and updates the static maps.
     */
    public static void renamePortal(PortalStructure portal, String oldName, String newName) {
        if (!oldName.isEmpty() && portalNameToId.containsKey(oldName)) {
            portalNameToId.remove(oldName);
        }
        portal.getSettings().setPortalName(newName);
        portalNameToId.put(newName, portal.getPortalId());
        portal.markForSave();
    }



}