package portal_block;

import advanced_portals.PortalBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import portal_multiblock.PortalMultiblockManager;
import portal_multiblock.PortalStructure;

import java.util.UUID;

public class PortalBlockEntity extends BlockEntity {
    public PortalStructure portalStructure;
    private UUID portalStructureId;
    public boolean joinedPortalStructure = false;

    public PortalBlockEntity(BlockPos pos, BlockState state) {
        super(PortalBlockEntities.PORTAL_BLOCK_ENTITY.get(), pos, state);
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

    public static void tick(Level level, BlockPos pos, BlockState state, PortalBlockEntity be) {
        if (!level.isClientSide && !be.joinedPortalStructure) {
            if (be.portalStructureId == null) {
                be.joinedPortalStructure = true;
                return;
            }

            // USE THE NEW METHOD HERE
            be.portalStructure = PortalStructure.getOrCreatePortalStructure(be.portalStructureId, level);

            if (be.portalStructure != null) { // This will now work
                be.portalStructure.addPortalBlock(pos); // Re-add the block
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


    public PortalStructure getPortal() {

        return portalStructure;
    }


}