package portal_controller;


import advanced_portals.PortalBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkHooks;
import portal_multiblock.PortalMultiblockManager;
import portal_multiblock.PortalStructure;

import java.util.UUID;

public class PortalControllerBlockEntity extends BlockEntity {
    private UUID portalId;

    public PortalControllerBlockEntity(BlockPos pos, BlockState state) {
        super(PortalBlockEntities.PORTAL_CONTROLLER_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID("PortalId")) {
            this.portalId = tag.getUUID("PortalId");
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (portalId != null) {
            tag.putUUID("PortalId", portalId);
        }
    }

    public UUID getPortalId() {
        if (portalId == null && level != null && !level.isClientSide()) {
           // PortalMultiblockManager manager = PortalMultiblockManager.get(level);
           // PortalStructure portal = manager.getPortalByController(worldPosition);
           // if (portal != null) {
           //     portalId = portal.getPortalId();
//}
        }
        return portalId;
    }

    public PortalStructure getPortal() {
        if (level != null && !level.isClientSide()) {

        }
        return null;
    }

    public void openGui(Player player) {
        if (level != null && !level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            PortalStructure portal = getPortal();
            if (portal != null) {
                // Open GUI - we'll implement the container/menu later
                NetworkHooks.openScreen(serverPlayer,
                        new PortalControllerMenuProvider(portal.getPortalId(), worldPosition),
                        buf -> {
                            buf.writeUUID(portal.getPortalId());
                            buf.writeBlockPos(worldPosition);
                        });
            }
        }
    }



    public boolean linkToPortal(String targetPortalName) {
     return true;
    }

    public boolean toggleActivation() {
    return true;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PortalControllerBlockEntity blockEntity) {
        // Periodic updates for power consumption, status checks, etc.
    }
}