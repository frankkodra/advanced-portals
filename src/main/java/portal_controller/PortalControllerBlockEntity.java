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
    public PortalStructure portalStructure;
    private UUID portalStructureId;
    public boolean joinedPortalStructure = false;

    public PortalControllerBlockEntity(BlockPos pos, BlockState state) {
        super(PortalBlockEntities.PORTAL_CONTROLLER_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        if (tag.contains("portalStructureId")) {
            portalStructureId = tag.getUUID("portalStructureId");
            this.joinedPortalStructure = false;
        } else {
            this.joinedPortalStructure = true;
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PortalControllerBlockEntity be) {
        if (!level.isClientSide && !be.joinedPortalStructure) {
            if (be.portalStructureId == null) {
                be.joinedPortalStructure = true;
                return;
            }

            // USE THE NEW METHOD HERE
            be.portalStructure = PortalStructure.getOrCreatePortalStructure(be.portalStructureId, level);

            if (be.portalStructure != null) { // This will now work
                  be.portalStructure.addPortalControllerBlock(be); // Re-add the controller
                be.joinedPortalStructure = true;
                be.setChanged();
            }
        }
        // ... rest of tick ...
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        if (portalStructure != null) {
            tag.putUUID("portalStructureId", portalStructure.getPortalId());
        } else if (portalStructureId != null) {
            tag.putUUID("portalStructureId", portalStructureId);
        }
    }

    public PortalStructure getPortalStructure() {
        return portalStructure;
    }

    public void setPortalStructure(PortalStructure portalStructure) {
        this.portalStructure = portalStructure;
        if (portalStructure != null) {
            this.portalStructureId = portalStructure.getPortalId();
            this.joinedPortalStructure = true;
        }
    }

    public void openGui(Player player) {
        if (level != null && !level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            PortalStructure portal = getPortalStructure();
            if (portal != null) {
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
        PortalStructure portal = getPortalStructure();
        if (portal == null) return false;

        PortalStructure targetPortal = PortalMultiblockManager.getPortalByName(targetPortalName);
        if (targetPortal != null && !targetPortal.getPortalId().equals(portal.getPortalId())) {
            portal.addLinkedPortal(targetPortal.getPortalId());
            targetPortal.addLinkedPortal(portal.getPortalId());
            setChanged();
            return true;
        }
        return false;
    }

    public boolean unlinkPortal(String targetPortalName) {
        PortalStructure portal = getPortalStructure();
        if (portal == null) return false;

        PortalStructure targetPortal = PortalMultiblockManager.getPortalByName(targetPortalName);
        if (targetPortal != null) {
            portal.removeLinkedPortal(targetPortal.getPortalId());
            targetPortal.removeLinkedPortal(portal.getPortalId());
            setChanged();
            return true;
        }
        return false;
    }

    public boolean toggleActivation() {
        PortalStructure portal = getPortalStructure();
        if (portal != null) {
            portal.setActive(!portal.isActive());
            setChanged();
            return portal.isActive();
        }
        return false;
    }

    public boolean setPortalName(String name) {
        PortalStructure portal = getPortalStructure();
        if (portal != null && name != null && !name.trim().isEmpty()) {
            portal.setPortalName(name.trim());
            setChanged();
            return true;
        }
        return false;
    }

    public String getPortalStatus() {
        PortalStructure portal = getPortalStructure();
        if (portal != null) {
            return portal.getStatus();
        }
        return "No Portal Structure";
    }
}