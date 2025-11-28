package portal_power_cable;

import advanced_portals.Logger;
import advanced_portals.PortalBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class PortalPowerCableBlockEntity extends BlockEntity {
    public PowerCableMultiblock multiblock;
    public boolean joinedMultiblock=false;
    // Track multiblock ID for lazy loading
    private UUID powerCableMultiblockId;

    public PortalPowerCableBlockEntity(BlockPos pos, BlockState state) {
        super(PortalBlockEntities.PORTAL_POWERCABLE_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        // FIXED: Enhanced loading
        if(tag.contains("powerCableMultiblockId")) {
            powerCableMultiblockId = tag.getUUID("powerCableMultiblockId");


                // Use the static method in PowerCableMultiblock class


        }
    }
public static void tick(Level level, BlockPos pos, BlockState state, PortalPowerCableBlockEntity be) {
    //Logger.sendMessage("ontick is running be joinedmultiblock: "+be.joinedMultiblock,false);
    if(!be.joinedMultiblock) {
        be.multiblock = PowerCableMultiblock.getOrCreatePowerCableMultiblock(be.powerCableMultiblockId,be.level);

        be.multiblock.addCablePosition(be.getBlockPos());
        be.joinedMultiblock=true;

    }

}
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        // FIXED: Only save the multiblock UUID
        if(multiblock != null) {
            tag.putUUID("powerCableMultiblockId", multiblock.id);
        } else if(powerCableMultiblockId != null) {
            tag.putUUID("powerCableMultiblockId", powerCableMultiblockId);
        }
    }

    // FIXED: Public method to set multiblock
    public void setMultiblock(PowerCableMultiblock multiblock) {
        this.multiblock = multiblock;
        if (multiblock != null) {
            this.powerCableMultiblockId = multiblock.id;
        }
    }

    // FIXED: Add public getter for multiblock
    public PowerCableMultiblock getMultiblock() {
        return multiblock;
    }
}
