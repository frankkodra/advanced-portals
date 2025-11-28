package portal_block;


import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;
import portal_multiblock.PortalMultiblockManager;
import portal_multiblock.PortalStructure;

public class PortalBlock extends Block implements EntityBlock {
    public PortalStructure portalStructure;
    public PortalBlockEntity blockEntity;
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
         portalStructure =  PortalStructure.addCreateOrMergePortalStructureForBlock(pos,level);
        }
        if (!level.isClientSide) {
            level.updateNeighborsAt(pos, state.getBlock());
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide()) {
           portalStructure.removePortalBlock(pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        blockEntity = new PortalBlockEntity(pos, state);
        return blockEntity;
    }
    public void setPortalStructure(PortalStructure portalStructure) {
        this.portalStructure = portalStructure;
        blockEntity.portalId = portalStructure.getPortalId();
    }
}
