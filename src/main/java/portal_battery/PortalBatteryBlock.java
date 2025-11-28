package portal_battery;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

public class PortalBatteryBlock extends Block implements EntityBlock {
    // FIXED: REMOVE this field - blocks are singletons!
    // public BatteryMultiblock batteryMultiblock;

    public PortalBatteryBlock() {
        super(Properties.of()
                .mapColor(MapColor.COLOR_ORANGE)
                .strength(3.0f, 8.0f)
                .lightLevel(state -> 5));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);

        if (!level.isClientSide()) {
            // FIXED: Just create/merge the multiblock - don't store reference in block
            BatteryMultiblock newMultiblock = BatteryMultiblock.addCreateOrMergeMultiblockForBlockPlaced(pos, level);

            // FIXED: Update the block entity reference instead
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof PortalBatteryBlockEntity) {
                ((PortalBatteryBlockEntity) blockEntity).setBatteryMultiblock(newMultiblock);
            }

        }
        if (!level.isClientSide) {
            level.updateNeighborsAt(pos, state.getBlock());
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide()) {
            // FIXED: Get multiblock from block entity instead of block field
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof PortalBatteryBlockEntity) {
                BatteryMultiblock multiblock = ((PortalBatteryBlockEntity) blockEntity).getBatteryMultiblock();
                if (multiblock != null) {
                    multiblock.handleBatteryBlockBreak(pos);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // FIXED: Don't pass multiblock to constructor - it will be set later
        return new PortalBatteryBlockEntity(pos, state);
    }

    // FIXED: REMOVE this method - we don't store multiblock in block anymore
    // public void setBatteryMultiblock(BatteryMultiblock multiblock) {
    //     this.batteryMultiblock = multiblock;
    // }
}