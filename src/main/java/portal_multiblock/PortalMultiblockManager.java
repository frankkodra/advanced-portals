
package portal_multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import portal_battery.BatteryMultiblock;
import portal_fluid_tank.TankMultiblock;
import portal_power_cable.PowerCableMultiblock;

import java.util.*;

public class PortalMultiblockManager {
    // Portal management
    public final static Map<UUID, PortalStructure> portals = new HashMap<>();
    public final static Map<String, UUID> portalNameToId = new HashMap<>();

    // Sub-multiblock management - JUST REGISTRY
    public final static Map<UUID, BatteryMultiblock> batteryMultiblocks = new HashMap<>();
    public final static Map<UUID, PowerCableMultiblock> powerCableMultiblocks = new HashMap<>();
    public final static Map<UUID, TankMultiblock> tankMultiblocks = new HashMap<>();

    public PortalMultiblockManager() {
    }

    // Core portal management
    public static PortalStructure createNewPortal(Level level, BlockPos initialBlock) {
        UUID portalId = UUID.randomUUID();
        PortalStructure portal = new PortalStructure(portalId, level);
        portals.put(portalId, portal);
        return portal;
    }

    // SIMPLE REGISTRY METHODS - No creation logic
    public static void addBatteryMultiblock(BatteryMultiblock batteryMultiblock) {
        batteryMultiblocks.put(batteryMultiblock.getMultiblockId(), batteryMultiblock);
    }

    public static void removeBatteryMultiblock(BatteryMultiblock batteryMultiblock) {
        batteryMultiblocks.remove(batteryMultiblock.getMultiblockId());
    }

    public static void addPowerCableMultiblock(PowerCableMultiblock powerCableMultiblock) {
        powerCableMultiblocks.put(powerCableMultiblock.id, powerCableMultiblock);
    }

    public static void removePowerCableMultiblock(PowerCableMultiblock powerCableMultiblock) {
        powerCableMultiblocks.remove(powerCableMultiblock.id);
    }

    public static void removePortalStructure(PortalStructure portalStructure) {
        portals.remove(portalStructure.getPortalId());
    }

    // SIMPLE GETTERS - No creation logic
    public static BatteryMultiblock getBatteryMultiblock(UUID id) {
        return batteryMultiblocks.get(id);
    }

    public static PowerCableMultiblock getPowerCableMultiblock(UUID id) {
        return powerCableMultiblocks.get(id);
    }


}