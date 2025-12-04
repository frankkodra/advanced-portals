package portal_multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import advanced_portals.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "advanced_portals", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PortalManager {
    private static final Map<UUID, PortalStructure> activePortals = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> entityCooldowns = new ConcurrentHashMap<>();
    private static final int COOLDOWN_TICKS = 60; // 3 seconds (20 ticks/second)

    // Resource costs
    public static final int ACTIVATION_FLUID_COST = 1000; // 1000 mB
    public static final int MAINTENANCE_POWER_COST = 5; // 5 FE/tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, PortalStructure>> iterator = activePortals.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, PortalStructure> entry = iterator.next();
            PortalStructure portal = entry.getValue();

            try {
                processPortal(portal, currentTime);

                // Remove if portal is no longer active
                if (!portal.isActive()) {
                    iterator.remove();
                    Logger.sendMessage("Portal " + portal.getPortalId().toString().substring(0, 8) + " removed from active processing", true);
                }
            } catch (Exception e) {
                Logger.sendMessage("Error processing portal " + portal.getPortalId().toString().substring(0, 8) + ": " + e.getMessage(), true);
                iterator.remove();
            }
        }

        // Clean up old cooldowns
        entityCooldowns.entrySet().removeIf(entry -> currentTime - entry.getValue() > COOLDOWN_TICKS * 50);
    }

    private static void processPortal(PortalStructure portal, long currentTime) {
        if (!portal.isValid()) {
            portal.setActive(false);
            return;
        }

        // Check if portal is activating side
        if (portal.isActivatingSide()) {
            // Check power for maintenance
            if (portal.getCurrentPower() < MAINTENANCE_POWER_COST) {
                Logger.sendMessage("Portal " + portal.getPortalId().toString().substring(0, 8) + " closed due to insufficient power", true);
                portal.setActive(false);
                return;
            }

            // Consume maintenance power
            if (!portal.consumePower(MAINTENANCE_POWER_COST)) {
                portal.setActive(false);
                return;
            }

            // Check duration
            if (portal.isDurationExpired(currentTime)) {
                Logger.sendMessage("Portal " + portal.getPortalId().toString().substring(0, 8) + " closed due to duration expiry", true);
                portal.setActive(false);
                return;
            }
        }

        // Entity detection and teleportation
        detectAndTeleportEntities(portal);
    }

    private static void detectAndTeleportEntities(PortalStructure portal) {
        PortalBounds bounds = portal.getBounds();
        if (bounds == null) return;

        Level level = portal.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;

        // Get entities in detection area
        List<Entity> entities = serverLevel.getEntitiesOfClass(Entity.class, bounds.getDetectionArea());

        for (Entity entity : entities) {
            if (isValidForTeleport(entity, portal)) {
                teleportEntity(entity, portal);
                break; // Only teleport one entity per tick to prevent lag
            }
        }
    }

    private static boolean isValidForTeleport(Entity entity, PortalStructure portal) {
        // Check cooldown
        if (entityCooldowns.containsKey(entity.getUUID())) {
            return false;
        }

        // Skip creative players and spectators
        if (entity instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) return false;
        }

        // Skip projectiles and other non-teleportable entities
        if (!entity.canChangeDimensions()) return false;

        // Entity must be alive and have physics
        if (!entity.isAlive() || entity.isNoGravity()) return false;

        return true;
    }

    private static void teleportEntity(Entity entity, PortalStructure fromPortal) {
        // Get linked portals
        List<UUID> linkedPortals = fromPortal.getLinkedPortals();
        if (linkedPortals.isEmpty()) return;

        // For now, teleport to first linked portal
        UUID targetPortalId = linkedPortals.get(0);
        PortalStructure targetPortal = PortalMultiblockManager.getPortalStructure(targetPortalId);

        if (targetPortal == null || !targetPortal.isValid() || !targetPortal.isActive()) {
            Logger.sendMessage("Target portal " + targetPortalId.toString().substring(0, 8) + " is not available for teleport", true);
            return;
        }

        PortalBounds targetBounds = targetPortal.getBounds();
        if (targetBounds == null) return;

        // Calculate teleport position (center of target portal)
        BlockPos targetCenter = targetBounds.getCenter();
        double targetX = targetCenter.getX() + 0.5;
        double targetY = targetCenter.getY();
        double targetZ = targetCenter.getZ() + 0.5;

        // Get target level
        Level targetLevel = targetPortal.getLevel();
        if (!(targetLevel instanceof ServerLevel targetServerLevel)) return;

        // Teleport entity
        if (entity.changeDimension(targetServerLevel) != null) {
            entity.teleportTo(targetX, targetY, targetZ);
            entity.setYRot(entity.getYRot() + 180.0F); // Turn around

            // Apply cooldown
            entityCooldowns.put(entity.getUUID(), System.currentTimeMillis());

            Logger.sendMessage("Teleported entity " + entity.getName().getString() + " from portal " +
                    fromPortal.getPortalId().toString().substring(0, 8) + " to portal " +
                    targetPortalId.toString().substring(0, 8), true);

            // Check if portal should close after teleport
            if (fromPortal.shouldCloseAfterTeleport() && fromPortal.isActivatingSide()) {
                fromPortal.setActive(false);
            }
        }
    }

    public static void registerActivePortal(PortalStructure portal) {
        if (portal.isActive() && portal.isValid()) {
            activePortals.put(portal.getPortalId(), portal);
            Logger.sendMessage("Portal " + portal.getPortalId().toString().substring(0, 8) + " registered for active processing", true);
        }
    }

    public static void unregisterPortal(PortalStructure portal) {
        activePortals.remove(portal.getPortalId());
    }

    public static void clearAll() {
        activePortals.clear();
        entityCooldowns.clear();
    }
}