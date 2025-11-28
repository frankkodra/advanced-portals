package advanced_portals;

import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import portal_battery.PortalBatteryBlock;
import portal_block.PortalBlock;
import portal_controller.PortalControllerBlock;
import portal_fluid_pipe.PortalFluidPipeBlock;
import portal_fluid_tank.PortalFluidTankBlock;
import portal_power_cable.PortalPowerCableBlock;
import portal_teleport_block.PortalTeleportBlock;

public class PortalBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, AdvancedPortals.MODID);

    public static final RegistryObject<Block> PORTAL_BLOCK =
            BLOCKS.register("portal_block", PortalBlock::new);

    public static final RegistryObject<Block> PORTAL_CONTROLLER_BLOCK =
            BLOCKS.register("portal_controller", PortalControllerBlock::new);

    public static final RegistryObject<Block> PORTAL_TELEPORT_BLOCK =
            BLOCKS.register("portal_teleport", PortalTeleportBlock::new);

    public static final RegistryObject<Block> PORTAL_BATTERY_BLOCK =
            BLOCKS.register("portal_battery", PortalBatteryBlock::new);

    public static final RegistryObject<Block> PORTAL_POWERCABLE_BLOCK =
            BLOCKS.register("portal_powercable", PortalPowerCableBlock::new);

    // Add the fluid blocks with consistent naming
    public static final RegistryObject<Block> PORTAL_FLUIDPIPE_BLOCK =
            BLOCKS.register("portal_fluidpipe", PortalFluidPipeBlock::new);

    public static final RegistryObject<Block> PORTAL_FLUIDTANK_BLOCK =
            BLOCKS.register("portal_fluidtank", PortalFluidTankBlock::new);
}