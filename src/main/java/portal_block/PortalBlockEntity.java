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
    public UUID portalId;

    public PortalBlockEntity(BlockPos pos, BlockState state) {
        super(PortalBlockEntities.PORTAL_BLOCK_ENTITY.get(), pos, state);
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

        return portalId;
    }

    public PortalStructure getPortal() {
        if (level != null && !level.isClientSide()) {

            return PortalMultiblockManager.portals.get(getPortalId());
        }
        return null;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PortalBlockEntity blockEntity) {
        // Periodic updates if needed
    }
}