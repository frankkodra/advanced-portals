package portal_controller;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class PortalControllerMenuProvider implements MenuProvider {
    private final UUID portalId;
    private final BlockPos controllerPos;

    public PortalControllerMenuProvider(UUID portalId, BlockPos controllerPos) {
        this.portalId = portalId;
        this.controllerPos = controllerPos;
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Portal Controller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        // We'll implement the actual menu later
        return new PortalControllerMenu(containerId, playerInventory, portalId, controllerPos);
    }
}