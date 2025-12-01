package portal_controller;

import advanced_portals.PortalBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import portal_multiblock.PortalStructure;

public class PortalControllerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public PortalControllerBlock() {
        super(Properties.of()
                .mapColor(MapColor.COLOR_BLUE)
                .strength(4.0f, 12.0f)
                .lightLevel(state -> 8)
                .noOcclusion()
        );
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);

        if (!level.isClientSide()) {
            // Create or merge portal structure
            PortalStructure newPortalStructure = PortalStructure.addCreateOrMergePortalStructureForBlock(pos, level);

            // Update the block entity reference
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof PortalControllerBlockEntity) {
                ((PortalControllerBlockEntity) blockEntity).setPortalStructure(newPortalStructure);
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
            if (blockEntity instanceof PortalControllerBlockEntity) {
                PortalControllerBlockEntity portalControllerBlockEntity = (PortalControllerBlockEntity) blockEntity;
                PortalStructure portalStructure = ((PortalControllerBlockEntity) blockEntity).portalStructure;
                if (portalStructure != null) {
                    portalStructure.removePortalController(portalControllerBlockEntity );
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof PortalControllerBlockEntity controller) {
                controller.openGui(player);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortalControllerBlockEntity(pos, state);
    }
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, PortalBlockEntities.PORTAL_CONTROLLER_BLOCK_ENTITY.get(),
                level.isClientSide ? null : PortalControllerBlockEntity::tick);
    }

}