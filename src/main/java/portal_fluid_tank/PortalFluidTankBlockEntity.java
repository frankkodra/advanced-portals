package portal_fluid_tank;

import advanced_portals.Logger;
import advanced_portals.PortalBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import javax.annotation.Nonnull;
import java.util.UUID;

public class PortalFluidTankBlockEntity extends BlockEntity {
    private TankMultiblock tankMultiblock;
    private final FluidTank fluidTank = new FluidTank(16000) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final LazyOptional<IFluidHandler> fluidHandler = LazyOptional.of(() -> fluidTank);

    // Track multiblock ID for lazy loading
    public UUID tankMultiblockId;

    // Tracks if this BlockEntity has joined its multiblock after loading
    public boolean joinedMultiblock = true;

    public PortalFluidTankBlockEntity(BlockPos pos, BlockState state) {
        super(PortalBlockEntities.PORTAL_FLUIDTANK_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        if(tag.contains("tankMultiblockId")) {
            tankMultiblockId = tag.getUUID("tankMultiblockId");
            this.joinedMultiblock = false;
        } else {
            this.joinedMultiblock = true;
        }

        fluidTank.readFromNBT(tag.getCompound("Fluid"));
    }

    /**
     * Static tick method for handling lazy loading and regular updates.
     */
    public static void tick(Level level, BlockPos pos, BlockState state, PortalFluidTankBlockEntity be) {
        if(!level.isClientSide && !be.joinedMultiblock) {
            be.tankMultiblock = TankMultiblock.getOrCreateTankMultiblock(be.tankMultiblockId, level);

            if (be.tankMultiblock != null) {
                be.tankMultiblock.addTank(pos);
                be.joinedMultiblock = true;
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        if(tankMultiblock != null) {
            tag.putUUID("tankMultiblockId", tankMultiblock.getMultiblockId());
        } else if(tankMultiblockId != null) {
            tag.putUUID("tankMultiblockId", tankMultiblockId);
        }

        tag.put("Fluid", fluidTank.writeToNBT(new CompoundTag()));
    }

    // Public method to set multiblock
    public void setTankMultiblock(TankMultiblock multiblock) {
        this.tankMultiblock = multiblock;
        if (multiblock != null) {
            this.tankMultiblockId = multiblock.getMultiblockId();
            this.joinedMultiblock = true;
        }
    }

    // Add public getter for multiblock
    public TankMultiblock getTankMultiblock() {
        return tankMultiblock;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, Direction side) {
        if(cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    public int getFluidStored() {
        return fluidTank.getFluidAmount();
    }

    public int getMaxFluidStored() {
        return fluidTank.getCapacity();
    }

    public FluidStack getFluid() {
        return fluidTank.getFluid();
    }
}
