package portal_teleport_block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;
import portal_multiblock.PortalMultiblockManager;
import portal_multiblock.PortalStructure;

import java.util.UUID;

public class PortalTeleportBlock extends Block implements EntityBlock {

    public PortalTeleportBlock() {
        super(Properties.of()
                .mapColor(MapColor.COLOR_MAGENTA)
                .strength(-1.0f, 3600000.0f) // Unbreakable like end portal
                .lightLevel(state -> 15)
                .noCollission());
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {

    }

    private void teleportEntity(Entity entity, PortalStructure fromPortal, PortalStructure toPortal) {
        // Basic teleport to the center of the target portal
        BlockPos targetPos = null;
        if (targetPos != null) {
            // Calculate center of target portal interior
            PortalStructure.PortalDimensions dims = toPortal.getDimensions();
            BlockPos center = targetPos.offset(dims.width / 2, 0, dims.height / 2);

            entity.teleportTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);

            // Consume power for teleport
            fromPortal.consumePower(1000); // Base teleport cost
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortalTeleportBlockEntity(pos, state);
    }
}