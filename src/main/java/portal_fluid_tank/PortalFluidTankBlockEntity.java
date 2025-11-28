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

    private UUID tankMultiblockId;
    public boolean joinedMultiblock = false;

    public PortalFluidTankBlockEntity(BlockPos pos, BlockState state) {
        super(PortalBlockEntities.PORTAL_FLUIDTANK_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        if(tag.contains("tankMultiblockId")) {
            tankMultiblockId = tag.getUUID("tankMultiblockId");
            this.joinedMultiblock = false;
            Logger.sendMessage("FluidTank BlockEntity at " + worldPosition + " loaded with multiblock ID: " +
                    tankMultiblockId.toString().substring(0, 8), true);
        } else {
            this.joinedMultiblock = true;
            Logger.sendMessage("FluidTank BlockEntity at " + worldPosition + " loaded with no saved multiblock ID", true);
        }

        fluidTank.readFromNBT(tag.getCompound("Fluid"));
        Logger.sendMessage("FluidTank BlockEntity at " + worldPosition + " loaded fluid: " +
                fluidTank.getFluidAmount() + "/" + fluidTank.getCapacity() + " mB", true);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PortalFluidTankBlockEntity be) {
        if(!level.isClientSide && !be.joinedMultiblock) {
            if (be.tankMultiblockId == null) {
                be.joinedMultiblock = true;
                Logger.sendMessage("FluidTank BlockEntity at " + pos + " has null multiblock ID, skipping lazy load", true);
                return;
            }

            Logger.sendMessage("FluidTank BlockEntity at " + pos + " lazy loading multiblock " +
                    be.tankMultiblockId.toString().substring(0, 8), true);

            be.tankMultiblock = TankMultiblock.getOrCreateTankMultiblock(be.tankMultiblockId, level);
            if (be.tankMultiblock != null) {
                be.tankMultiblock.addTank(pos);
                be.joinedMultiblock = true;
                be.setChanged();
                Logger.sendMessage("FluidTank BlockEntity at " + pos + " successfully joined multiblock " +
                        be.tankMultiblock.getMultiblockId().toString().substring(0, 8), true);
            } else {
                Logger.sendMessage("ERROR: FluidTank BlockEntity at " + pos + " failed to load multiblock " +
                        be.tankMultiblockId.toString().substring(0, 8), true);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        if(tankMultiblock != null) {
            tag.putUUID("tankMultiblockId", tankMultiblock.getMultiblockId());
           // Logger.sendMessage("FluidTank BlockEntity at " + worldPosition + " saving multiblock ID: " +
              //      tankMultiblock.getMultiblockId().toString().substring(0, 8), true);
        } else if(tankMultiblockId != null) {
            tag.putUUID("tankMultiblockId", tankMultiblockId);
          //  Logger.sendMessage("FluidTank BlockEntity at " + worldPosition + " saving stored multiblock ID: " +
             //       tankMultiblockId.toString().substring(0, 8), true);
        }

        tag.put("Fluid", fluidTank.writeToNBT(new CompoundTag()));
       // Logger.sendMessage("FluidTank BlockEntity at " + worldPosition + " saving fluid: " +
              //  fluidTank.getFluidAmount() + "/" + fluidTank.getCapacity() + " mB", true);
    }

    public void setTankMultiblock(TankMultiblock multiblock) {
        this.tankMultiblock = multiblock;
        if (multiblock != null) {
            this.tankMultiblockId = multiblock.getMultiblockId();
            this.joinedMultiblock = true;
            Logger.sendMessage("FluidTank BlockEntity at " + worldPosition + " set multiblock to " +
                    multiblock.getMultiblockId().toString().substring(0, 8), true);
        }
    }

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
