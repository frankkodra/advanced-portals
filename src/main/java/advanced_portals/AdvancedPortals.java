package advanced_portals;

import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import portal_controller.PortalControllerScreen;

@Mod(AdvancedPortals.MODID)
public class AdvancedPortals {
    public static final String MODID = "advanced_portals";
    private static final Logger LOGGER = LogUtils.getLogger();

    public AdvancedPortals() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register setup methods
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onClientSetup);

        // Register portal system registries IN ORDER
        PortalBlocks.BLOCKS.register(modEventBus);
        ItemRegistry.ITEMS.register(modEventBus);
        PortalBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        CreativeTabRegistry.CREATIVE_MODE_TABS.register(modEventBus);
        MenuRegistry.MENUS.register(modEventBus); // Add this line

        // Register event handlers
        MinecraftForge.EVENT_BUS.register(this);
      //  BlockRenderTypeMap.INSTANCE.putBlock(PortalBlocks.PORTAL_POWERCABLE_BLOCK.get(), RenderType.cutout());
        // Register creative tab contents
        modEventBus.addListener(this::addCreative);
    }

    public void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Advanced Portals mod initialized");
    }

    public void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            // Optional: Add items to functional blocks tab
            event.accept(ItemRegistry.PORTAL_BLOCK_ITEM.get());
            event.accept(ItemRegistry.PORTAL_CONTROLLER_ITEM.get());
           // event.accept(ItemRegistry.PORTAL_TELEPORT_ITEM.get());
            event.accept(ItemRegistry.PORTAL_BATTERY_ITEM.get());
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Advanced Portals server starting");
    }



    public void onClientSetup(final FMLClientSetupEvent event) {
        // This must be run on the main game thread
        event.enqueueWork(() -> {
            // Register your screen. Replace 'MenuRegistry' and 'PortalControllerScreen'
            // with the exact names of your classes.
            net.minecraft.client.gui.screens.MenuScreens.register(
                    MenuRegistry.PORTAL_CONTROLLER_MENU.get(),
                    PortalControllerScreen::new
            );
        });
    }

}