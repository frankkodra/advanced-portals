package portal_power_cable;

import advanced_portals.PortalBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor; // <-- NEW IMPORT: Required for updateShape to properly override
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
// CORRECTED: Import 'Shapes' instead of 'VoxelShapes' for static methods like Shapes.or()
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;
import portal_battery.PortalBatteryBlock;
import portal_block.PortalBlock;
import portal_controller.PortalControllerBlock;

import java.util.HashMap;
import java.util.Map;

// NOTE: Ensure you have all necessary imports for Direction, VoxelShape, BlockPlaceContext, etc.

public class PortalPowerCableBlock extends BaseEntityBlock {

    // --- 1. BLOCKSTATE PROPERTIES FOR CONNECTIONS ---
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    // --- 2. VOXEL SHAPE CONSTANTS (Dimensions based on JSON [5,5,5] to [11,11,11]) ---
    private static final VoxelShape CORE_SHAPE = Block.box(5.0, 5.0, 5.0, 11.0, 11.0, 11.0);

    // Shapes for the 6 potential arms (connecting the core to the block edge)
    private static final VoxelShape NORTH_SHAPE = Block.box(5.0, 5.0, 0.0, 11.0, 11.0, 5.0); // Connects core (Z=5) to Z=0
    private static final VoxelShape SOUTH_SHAPE = Block.box(5.0, 5.0, 11.0, 11.0, 11.0, 16.0); // Connects core (Z=11) to Z=16
    private static final VoxelShape EAST_SHAPE = Block.box(11.0, 5.0, 5.0, 16.0, 11.0, 11.0); // Connects core (X=11) to X=16
    private static final VoxelShape WEST_SHAPE = Block.box(0.0, 5.0, 5.0, 5.0, 11.0, 11.0); // Connects core (X=0) to X=5
    private static final VoxelShape UP_SHAPE = Block.box(5.0, 11.0, 5.0, 11.0, 16.0, 11.0); // Connects core (Y=11) to Y=16
    private static final VoxelShape DOWN_SHAPE = Block.box(5.0, 0.0, 5.0, 11.0, 5.0, 11.0); // Connects core (Y=0) to Y=5

    // Map to cache generated shapes (saves performance)
    private final Map<BlockState, VoxelShape> SHAPE_CACHE = new HashMap<>();

    public PortalPowerCableBlock() {
        super(Properties.of()
                .mapColor(MapColor.COLOR_ORANGE)
                .strength(3.0f, 8.0f)
                .lightLevel(state -> 5)
                .noOcclusion()); // FIX: .noOcclusion() prevents face culling, solving the invisible block issue

        // Set the default state to include all properties, initially set to false
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false));
    }

    // --- 3. DYNAMIC SHAPE GENERATION ---

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        // Use the cache if the shape for this state has already been calculated
        if (SHAPE_CACHE.containsKey(state)) {
            return SHAPE_CACHE.get(state);
        }

        // Start with the central core shape
        VoxelShape shape = CORE_SHAPE;

        // Combine the core with the appropriate arm shapes based on the BlockState
        // CORRECTED: Using Shapes.or() instead of VoxelShapes.or()
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

        // Cache the calculated shape and return it
        SHAPE_CACHE.put(state, shape);
        return shape;
    }

    // --- 4. BLOCKSTATE DEFINITIONS ---

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // Register all six directional properties
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    // --- 5. CONNECTION LOGIC (for placement/updates) ---

    // When the block is placed, check surrounding blocks to set the initial connections
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

    // After a neighbor block changes, update this block's connections
    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor world, BlockPos pos, BlockPos facingPos) {
        // FIX: Changed 'Level' to 'LevelAccessor' to match the required override signature.
        // 1. Update this block's connection state based on the neighboring block
        BlockState newState = state.setValue(getPropertyForDirection(facing), canConnectTo(world, facingPos));

        // 2. Clear the shape cache for this new state so it gets recalculated next time
        SHAPE_CACHE.remove(newState);

        return newState;
    }

    // Helper method to check if a connection is possible (e.g., if the other block is also a cable)
    private boolean canConnectTo(LevelAccessor world, BlockPos neighborPos) {
        // FIX: Changed 'Level' to 'LevelAccessor' to match the caller (updateShape).
        BlockState neighborState = world.getBlockState(neighborPos);

        // This is a simple check that will connect to ANY other PortalPowerCableBlock
        return neighborState.getBlock() instanceof PortalPowerCableBlock||
                neighborState.getBlock() instanceof PortalBatteryBlock||
        neighborState.getBlock() instanceof PortalControllerBlock||
                neighborState.getBlock() instanceof PortalBlock;
    }

    // Helper method to get the correct BooleanProperty for a Direction
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

    // --- 6. STANDARD BLOCK ENTITY METHODS (from existing code) ---

    @Override
    public RenderShape getRenderShape(BlockState blockState) {
        return RenderShape.MODEL;
    }

    @Override
    public void onPlace(BlockState blockState, Level level, BlockPos pos, BlockState blockState2, boolean isMoving) {
        // Create/merge the multiblock structure
        // NOTE: These multiblock methods assume PowerCableMultiblock class exists and is correct.
        PowerCableMultiblock newMultiblock = PowerCableMultiblock.addCreateOrMergeForBlock(pos, level);

        // Update the block entity reference
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof PortalPowerCableBlockEntity) {
            ((PortalPowerCableBlockEntity) blockEntity).setMultiblock(newMultiblock);
        }

        super.onPlace(blockState, level, pos, blockState2, isMoving);

        // Notify neighboring blocks when this block is placed

    }

    @Override
    public void onRemove(BlockState blockState, Level level, BlockPos pos, BlockState blockState2, boolean isMoving) {
        // Only run the removal logic if the block being removed is *this* block (not just a state change)
        if (!blockState.is(blockState2.getBlock())) {
            // Multiblock handling
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof PortalPowerCableBlockEntity) {
                PowerCableMultiblock multiblock = ((PortalPowerCableBlockEntity) blockEntity).getMultiblock();
                if (multiblock != null) {
                    multiblock.handleCableBlockBreak(pos);
                }
            }

            // Notify neighboring blocks when this block is removed
            if (!level.isClientSide) {
                level.updateNeighborsAt(pos, blockState.getBlock());
            }

            super.onRemove(blockState, level, pos, blockState2, isMoving);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // FIXED: Don't pass multiblock to constructor - it will be set later
        return new PortalPowerCableBlockEntity(pos, state);
    }
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, PortalBlockEntities.PORTAL_POWERCABLE_BLOCK_ENTITY.get(),
                level.isClientSide ? null : PortalPowerCableBlockEntity::tick);
    }
}