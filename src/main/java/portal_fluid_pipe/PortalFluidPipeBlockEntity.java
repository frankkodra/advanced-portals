package portal_fluid_pipe;

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

public class PortalFluidPipeBlockEntity extends BlockEntity {
    public FluidPipeMultiblock multiblock;
    public boolean joinedMultiblock = false;

    private final FluidTank fluidTank = new FluidTank(1000) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final LazyOptional<IFluidHandler> fluidHandler = LazyOptional.of(() -> fluidTank);

    private UUID fluidPipeMultiblockId;

    public PortalFluidPipeBlockEntity(BlockPos pos, BlockState state) {
        super(PortalBlockEntities.PORTAL_FLUIDPIPE_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        if(tag.contains("fluidPipeMultiblockId")) {
            fluidPipeMultiblockId = tag.getUUID("fluidPipeMultiblockId");
            this.joinedMultiblock = false;
            Logger.sendMessage("FluidPipe BlockEntity at " + worldPosition + " loaded with multiblock ID: " +
                    fluidPipeMultiblockId.toString().substring(0, 8), true);
        } else {
            this.joinedMultiblock = true;
            Logger.sendMessage("FluidPipe BlockEntity at " + worldPosition + " loaded with no saved multiblock ID", true);
        }

        fluidTank.readFromNBT(tag.getCompound("Fluid"));
        Logger.sendMessage("FluidPipe BlockEntity at " + worldPosition + " loaded fluid: " +
                fluidTank.getFluidAmount() + "/" + fluidTank.getCapacity() + " mB", true);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PortalFluidPipeBlockEntity be) {
        if(!level.isClientSide && !be.joinedMultiblock) {
            if (be.fluidPipeMultiblockId == null) {
                be.joinedMultiblock = true;
                Logger.sendMessage("FluidPipe BlockEntity at " + pos + " has null multiblock ID, skipping lazy load", true);
                return;
            }

            Logger.sendMessage("FluidPipe BlockEntity at " + pos + " lazy loading multiblock " +
                    be.fluidPipeMultiblockId.toString().substring(0, 8), true);

            be.multiblock = FluidPipeMultiblock.getOrCreateFluidPipeMultiblock(be.fluidPipeMultiblockId, be.level);

            if (be.multiblock != null) {
                be.multiblock.addPipePosition(be.getBlockPos());
                be.joinedMultiblock = true;
                be.setChanged();
                Logger.sendMessage("FluidPipe BlockEntity at " + pos + " successfully joined multiblock " +
                        be.multiblock.id.toString().substring(0, 8), true);
            } else {
                Logger.sendMessage("ERROR: FluidPipe BlockEntity at " + pos + " failed to load multiblock " +
                        be.fluidPipeMultiblockId.toString().substring(0, 8), true);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        if(multiblock != null) {
            tag.putUUID("fluidPipeMultiblockId", multiblock.id);
          //  Logger.sendMessage("FluidPipe BlockEntity at " + worldPosition + " saving multiblock ID: " +
              //      multiblock.id.toString().substring(0, 8), true);
        } else if(fluidPipeMultiblockId != null) {
            tag.putUUID("fluidPipeMultiblockId", fluidPipeMultiblockId);
         //   Logger.sendMessage("FluidPipe BlockEntity at " + worldPosition + " saving stored multiblock ID: " +
                //    fluidPipeMultiblockId.toString().substring(0, 8), true);
        }

        tag.put("Fluid", fluidTank.writeToNBT(new CompoundTag()));
        //Logger.sendMessage("FluidPipe BlockEntity at " + worldPosition + " saving fluid: " +
          //      fluidTank.getFluidAmount() + "/" + fluidTank.getCapacity() + " mB", true);
    }

    public void setMultiblock(FluidPipeMultiblock multiblock) {
        this.multiblock = multiblock;
        if (multiblock != null) {
            this.fluidPipeMultiblockId = multiblock.id;
            this.joinedMultiblock = true;
            Logger.sendMessage("FluidPipe BlockEntity at " + worldPosition + " set multiblock to " +
                    multiblock.id.toString().substring(0, 8), true);
        }
    }

    public FluidPipeMultiblock getMultiblock() {
        return multiblock;
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