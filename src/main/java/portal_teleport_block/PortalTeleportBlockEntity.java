package portal_teleport_block;

import advanced_portals.PortalBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PortalTeleportBlockEntity extends BlockEntity {

    public PortalTeleportBlockEntity(BlockPos pos, BlockState state) {
        super(PortalBlockEntities.PORTAL_TELEPORT_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PortalTeleportBlockEntity blockEntity) {
        // Visual effects, particle spawning, etc.
    }
}