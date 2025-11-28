package advanced_portals;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ItemRegistry {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, AdvancedPortals.MODID);

    // Block Items
    public static final RegistryObject<Item> PORTAL_BLOCK_ITEM =
            ITEMS.register("portal_block", () -> new BlockItem(PortalBlocks.PORTAL_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> PORTAL_CONTROLLER_ITEM =
            ITEMS.register("portal_controller", () -> new BlockItem(PortalBlocks.PORTAL_CONTROLLER_BLOCK.get(), new Item.Properties()));

  //  public static final RegistryObject<Item> PORTAL_TELEPORT_ITEM =
      //      ITEMS.register("portal_teleport", () -> new BlockItem(PortalBlocks.PORTAL_TELEPORT_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> PORTAL_BATTERY_ITEM =
            ITEMS.register("portal_battery", () -> new BlockItem(PortalBlocks.PORTAL_BATTERY_BLOCK.get(), new Item.Properties()));
   // public static final RegistryObject<Item> PORTAL_TANK_ITEM = ITEMS.register("portal_tank",()->new BlockItem(PortalBlocks.))
   public static final RegistryObject<Item> PORTAL_POWERCABLE_ITEM =
           ITEMS.register("portal_powercable", () -> new BlockItem(PortalBlocks.PORTAL_POWERCABLE_BLOCK.get(), new Item.Properties()));
    //

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}