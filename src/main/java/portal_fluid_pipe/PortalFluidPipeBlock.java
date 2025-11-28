package portal_fluid_pipe;

import advanced_portals.PortalBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import portal_battery.PortalBatteryBlock;
import portal_block.PortalBlock;
import portal_controller.PortalControllerBlock;
import portal_fluid_tank.PortalFluidTankBlock;

import java.util.HashMap;
import java.util.Map;

public class PortalFluidPipeBlock extends BaseEntityBlock {

    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    private static final VoxelShape CORE_SHAPE = Block.box(5.0, 5.0, 5.0, 11.0, 11.0, 11.0);
    private static final VoxelShape NORTH_SHAPE = Block.box(5.0, 5.0, 0.0, 11.0, 11.0, 5.0);
    private static final VoxelShape SOUTH_SHAPE = Block.box(5.0, 5.0, 11.0, 11.0, 11.0, 16.0);
    private static final VoxelShape EAST_SHAPE = Block.box(11.0, 5.0, 5.0, 16.0, 11.0, 11.0);
    private static final VoxelShape WEST_SHAPE = Block.box(0.0, 5.0, 5.0, 5.0, 11.0, 11.0);
    private static final VoxelShape UP_SHAPE = Block.box(5.0, 11.0, 5.0, 11.0, 16.0, 11.0);
    private static final VoxelShape DOWN_SHAPE = Block.box(5.0, 0.0, 5.0, 11.0, 5.0, 11.0);

    private final Map<BlockState, VoxelShape> SHAPE_CACHE = new HashMap<>();

    public PortalFluidPipeBlock() {
        super(Properties.of()
                .mapColor(MapColor.COLOR_BLUE)
                .strength(2.0f, 6.0f)
                .lightLevel(state -> 2)
                .noOcclusion());

        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        if (SHAPE_CACHE.containsKey(state)) {
            return SHAPE_CACHE.get(state);
        }

        VoxelShape shape = CORE_SHAPE;

        if (state.getValue(NORTH)) {
            shape = Shapes.or(shape, NORTH_SHAPE);
        }
        if (state.getValue(SOUTH)) {
            shape = Shapes.or(shape, SOUTH_SHAPE);
        }
        if (state.getValue(EAST)) {
            shape = Shapes.or(shape, EAST_SHAPE);
        }
        if (state.getValue(WEST)) {
            shape = Shapes.or(shape, WEST_SHAPE);
        }
        if (state.getValue(UP)) {
            shape = Shapes.or(shape, UP_SHAPE);
        }
        if (state.getValue(DOWN)) {
            shape = Shapes.or(shape, DOWN_SHAPE);
        }

        SHAPE_CACHE.put(state, shape);
        return shape;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();

        return this.defaultBlockState()
                .setValue(NORTH, canConnectTo(world, pos.north()))
                .setValue(EAST, canConnectTo(world, pos.east()))
                .setValue(SOUTH, canConnectTo(world, pos.south()))
                .setValue(WEST, canConnectTo(world, pos.west()))
                .setValue(UP, canConnectTo(world, pos.above()))
                .setValue(DOWN, canConnectTo(world, pos.below()));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor world, BlockPos pos, BlockPos facingPos) {
        BlockState newState = state.setValue(getPropertyForDirection(facing), canConnectTo(world, facingPos));
        SHAPE_CACHE.remove(newState);
        return newState;
    }

    private boolean canConnectTo(LevelAccessor world, BlockPos neighborPos) {
        BlockState neighborState = world.getBlockState(neighborPos);

        return neighborState.getBlock() instanceof PortalFluidPipeBlock ||
                neighborState.getBlock() instanceof PortalFluidTankBlock ||
                neighborState.getBlock() instanceof PortalControllerBlock ||
                neighborState.getBlock() instanceof PortalBlock;
    }

    private BooleanProperty getPropertyForDirection(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> DOWN;
            default -> throw new IllegalArgumentException("Invalid direction: " + direction);
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState blockState) {
        return RenderShape.MODEL;
    }

    @Override
    public void onPlace(BlockState blockState, Level level, BlockPos pos, BlockState blockState2, boolean isMoving) {
        FluidPipeMultiblock newMultiblock = FluidPipeMultiblock.addCreateOrMergeForBlock(pos, level);

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof PortalFluidPipeBlockEntity) {
            ((PortalFluidPipeBlockEntity) blockEntity).setMultiblock(newMultiblock);
        }

        super.onPlace(blockState, level, pos, blockState2, isMoving);

        if (!level.isClientSide) {
            level.updateNeighborsAt(pos, blockState.getBlock());
        }
    }

    @Override
    public void onRemove(BlockState blockState, Level level, BlockPos pos, BlockState blockState2, boolean isMoving) {
        if (!blockState.is(blockState2.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof PortalFluidPipeBlockEntity) {
                FluidPipeMultiblock multiblock = ((PortalFluidPipeBlockEntity) blockEntity).getMultiblock();
                if (multiblock != null) {
                    multiblock.handlePipeBlockBreak(pos);
                }
            }

            if (!level.isClientSide) {
                level.updateNeighborsAt(pos, blockState.getBlock());
            }

            super.onRemove(blockState, level, pos, blockState2, isMoving);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortalFluidPipeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }

        return createTickerHelper(
                type,
                PortalBlockEntities.PORTAL_FLUIDPIPE_BLOCK_ENTITY.get(),
                PortalFluidPipeBlockEntity::tick
        );
    }
}