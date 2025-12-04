package advanced_portals; // Or your mod's base package

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import portal_controller.PortalControllerMenu;

public class MenuRegistry {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, "advanced_portals"); // Use your MODID

    // This is the critical piece that was missing
    public static final RegistryObject<MenuType<PortalControllerMenu>> PORTAL_CONTROLLER_MENU =
            MENUS.register("portal_controller",
                    () -> IForgeMenuType.create(PortalControllerMenu::new));
}
