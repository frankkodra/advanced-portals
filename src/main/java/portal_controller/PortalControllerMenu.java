package portal_controller;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class PortalControllerMenu extends AbstractContainerMenu {
    private final UUID portalId;
    private final BlockPos controllerPos;

    public PortalControllerMenu(int containerId, Inventory playerInventory, UUID portalId, BlockPos controllerPos) {
        super(null, containerId); // We'll register the menu type later
        this.portalId = portalId;
        this.controllerPos = controllerPos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int i) {
        return null;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(
                controllerPos.getX() + 0.5,
                controllerPos.getY() + 0.5,
                controllerPos.getZ() + 0.5
        ) <= 64.0;
    }
}
