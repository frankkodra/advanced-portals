package portal_fluid_tank;

import advanced_portals.PortalBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

public class PortalFluidTankBlock extends Block implements EntityBlock {

    public PortalFluidTankBlock() {
        super(Properties.of()
                .mapColor(MapColor.COLOR_BLUE)
                .strength(3.0f, 8.0f)
                .lightLevel(state -> 3));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);

        if (!level.isClientSide()) {
            TankMultiblock newMultiblock = TankMultiblock.addCreateOrMergeMultiblockForBlockPlaced(pos, level);

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof PortalFluidTankBlockEntity) {
                ((PortalFluidTankBlockEntity) blockEntity).setTankMultiblock(newMultiblock);
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
            if (blockEntity instanceof PortalFluidTankBlockEntity) {
                TankMultiblock multiblock = ((PortalFluidTankBlockEntity) blockEntity).getTankMultiblock();
                if (multiblock != null) {
                    multiblock.handleTankBlockBreak(pos);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortalFluidTankBlockEntity(pos, state);
    }
}