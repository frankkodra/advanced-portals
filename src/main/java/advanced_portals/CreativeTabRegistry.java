package advanced_portals;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class CreativeTabRegistry {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AdvancedPortals.MODID);

    // Main Creative Tab for Advanced Portals
    public static final RegistryObject<CreativeModeTab> ADVANCED_PORTALS_TAB = CREATIVE_MODE_TABS.register("advanced_portals_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.advanced_portals"))
                    .icon(() -> new ItemStack(ItemRegistry.PORTAL_BLOCK_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        // Portal System Items
                        output.accept(ItemRegistry.PORTAL_BLOCK_ITEM.get());
                        output.accept(ItemRegistry.PORTAL_CONTROLLER_ITEM.get());
                        //output.accept(ItemRegistry.PORTAL_TELEPORT_ITEM.get());
                        output.accept(ItemRegistry.PORTAL_BATTERY_ITEM.get());
                        output.accept(ItemRegistry.PORTAL_POWERCABLE_ITEM.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}