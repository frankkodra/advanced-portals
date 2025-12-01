package portal_battery;

import advanced_portals.PortalBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

public class PortalBatteryBlock extends BaseEntityBlock implements EntityBlock {
    // FIXED: REMOVE this field - blocks are singletons!
    // public BatteryMultiblock batteryMultiblock;

    public PortalBatteryBlock() {
        super(Properties.of()
                .mapColor(MapColor.COLOR_ORANGE)
                .strength(3.0f, 8.0f)
                .lightLevel(state -> 5));
    }
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {

        if (!level.isClientSide()) {
            // FIXED: Just create/merge the multiblock - don't store reference in block
            BatteryMultiblock newMultiblock = BatteryMultiblock.addCreateOrMergeMultiblockForBlockPlaced(pos, level);

            // FIXED: Update the block entity reference instead
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof PortalBatteryBlockEntity) {
                ((PortalBatteryBlockEntity) blockEntity).setBatteryMultiblock(newMultiblock);
            }

        }

        super.onPlace(state, level, pos, oldState, isMoving);

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
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, PortalBlockEntities.PORTAL_BATTERY_BLOCK_ENTITY.get(),
                level.isClientSide ? null : PortalBatteryBlockEntity::tick);
    }

    // FIXED: REMOVE this method - we don't store multiblock in block anymore
    // public void setBatteryMultiblock(BatteryMultiblock multiblock) {
    //     this.batteryMultiblock = multiblock;
    // }
}