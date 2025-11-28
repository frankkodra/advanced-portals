package portal_battery;

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
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import portal_multiblock.PortalMultiblockManager;

import javax.annotation.Nonnull;
import java.util.UUID;

public class PortalBatteryBlockEntity extends BlockEntity {
    private BatteryMultiblock batteryMultiblock;
    private final EnergyStorage energyStorage = new EnergyStorage(100000, 1000, 1000, 0);
    private final LazyOptional<IEnergyStorage> energyHandler = LazyOptional.of(() -> energyStorage);

    // Track multiblock ID for lazy loading
    private UUID batteryMultiblockId;

    // FIXED: Remove constructor with multiblock parameter
    public PortalBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(PortalBlockEntities.PORTAL_BATTERY_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        if(tag.contains("batteryMultiblockId")) {
            batteryMultiblockId = tag.getUUID("batteryMultiblockId");

            Level level = this.getLevel();
            if(level != null && !level.isClientSide()) {
                // Use the static method in BatteryMultiblock class
                batteryMultiblock = BatteryMultiblock.getOrCreateBatteryMultiblock(batteryMultiblockId, level);

                // Add this battery to the multiblock
                batteryMultiblock.addBattery(this.getBlockPos());
            }
        }

        energyStorage.deserializeNBT(tag.get("Energy"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        if(batteryMultiblock != null) {
            tag.putUUID("batteryMultiblockId", batteryMultiblock.getMultiblockId());
        } else if(batteryMultiblockId != null) {
            tag.putUUID("batteryMultiblockId", batteryMultiblockId);
        }

        tag.put("Energy", energyStorage.serializeNBT());
    }

    // FIXED: Public method to set multiblock
    public void setBatteryMultiblock(BatteryMultiblock multiblock) {
        this.batteryMultiblock = multiblock;
        if (multiblock != null) {
            this.batteryMultiblockId = multiblock.getMultiblockId();
        }
    }

    // FIXED: Add public getter for multiblock
    public BatteryMultiblock getBatteryMultiblock() {
        return batteryMultiblock;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, Direction side) {
        if(cap == ForgeCapabilities.ENERGY) {
            return energyHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    public int getEnergyStored() {
        return energyStorage.getEnergyStored();
    }

    public int getMaxEnergyStored() {
        return energyStorage.getMaxEnergyStored();
    }
}
