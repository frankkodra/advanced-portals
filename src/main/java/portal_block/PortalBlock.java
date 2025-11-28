package portal_block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;
import portal_multiblock.PortalStructure;

public class PortalBlock extends BaseEntityBlock implements EntityBlock {

    public PortalBlock() {
        super(Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(3.0f, 10.0f)
                .lightLevel(state -> 5));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);

        if (!level.isClientSide()) {
            // Create or merge portal structure
            PortalStructure newPortalStructure = PortalStructure.addCreateOrMergePortalStructureForBlock(pos, level);

            // Update the block entity reference
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof PortalBlockEntity) {
                ((PortalBlockEntity) blockEntity).setPortalStructure(newPortalStructure);
            }
        }

        if (!level.isClientSide) {
            level.updateNeighborsAt(pos, state.getBlock());
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof PortalBlockEntity) {
                PortalStructure portalStructure = ((PortalBlockEntity) blockEntity).getPortalStructure();
                if (portalStructure != null) {
                    portalStructure.removePortalBlock(pos);
                    portalStructure.revalidateStructure();
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortalBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }

        return createTickerHelper(
                type,
                advanced_portals.PortalBlockEntities.PORTAL_BLOCK_ENTITY.get(),
                PortalBlockEntity::tick
        );
    }
}
