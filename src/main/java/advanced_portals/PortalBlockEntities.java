package advanced_portals;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import portal_battery.PortalBatteryBlockEntity;
import portal_block.PortalBlockEntity;
import portal_controller.PortalControllerBlockEntity;
import portal_power_cable.PortalPowerCableBlockEntity;
import portal_teleport_block.PortalTeleportBlockEntity;

public class PortalBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AdvancedPortals.MODID);

    public static final RegistryObject<BlockEntityType<PortalBlockEntity>> PORTAL_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("portal_block",
                    () -> BlockEntityType.Builder.of(PortalBlockEntity::new,
                            PortalBlocks.PORTAL_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<PortalControllerBlockEntity>> PORTAL_CONTROLLER_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("portal_controller",
                    () -> BlockEntityType.Builder.of(PortalControllerBlockEntity::new,
                            PortalBlocks.PORTAL_CONTROLLER_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<PortalTeleportBlockEntity>> PORTAL_TELEPORT_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("portal_teleport",
                    () -> BlockEntityType.Builder.of(PortalTeleportBlockEntity::new,
                            PortalBlocks.PORTAL_TELEPORT_BLOCK.get()).build(null));


    public static final RegistryObject<BlockEntityType<PortalBatteryBlockEntity>> PORTAL_BATTERY_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("portal_battery",
                    () -> BlockEntityType.Builder.of(PortalBatteryBlockEntity::new, PortalBlocks.PORTAL_BATTERY_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<PortalPowerCableBlockEntity>> PORTAL_POWERCABLE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("portal_powercable",
            ()->BlockEntityType.Builder.of(PortalPowerCableBlockEntity::new, PortalBlocks.PORTAL_POWERCABLE_BLOCK.get()).build(null));
}