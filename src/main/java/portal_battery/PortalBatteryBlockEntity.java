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

import javax.annotation.Nonnull;
import java.util.UUID;

public class PortalBatteryBlockEntity extends BlockEntity {
    private BatteryMultiblock batteryMultiblock;
    private final EnergyStorage energyStorage = new EnergyStorage(100000, 1000, 1000, 0);
    private final LazyOptional<IEnergyStorage> energyHandler = LazyOptional.of(() -> energyStorage);

    // Track multiblock ID for lazy loading
    private UUID batteryMultiblockId;

    // NEW FIELD: Tracks if this BlockEntity has joined its multiblock after loading
    public boolean joinedMultiblock = false;

    public PortalBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(PortalBlockEntities.PORTAL_BATTERY_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        if(tag.contains("batteryMultiblockId")) {
            batteryMultiblockId = tag.getUUID("batteryMultiblockId");
            // Set to false to trigger the lazy loading logic in the 'tick' method
            this.joinedMultiblock = false;
        } else {
            // If no ID is saved, it's a new block, and onPlace will handle it.
            this.joinedMultiblock = true;
        }

        energyStorage.deserializeNBT(tag.get("Energy"));
    }

    /**
     * Static tick method for handling lazy loading and regular updates.
     * This mirrors the logic in your PortalPowerCableBlockEntity.
     */
    public static void tick(Level level, BlockPos pos, BlockState state, PortalBatteryBlockEntity be) {
        // Only run on the server and if we haven't joined the multiblock yet
        if(!level.isClientSide && !be.joinedMultiblock) {

            // If the ID is null, we assume the block was placed before saving/loading
            if (be.batteryMultiblockId == null) {
                be.joinedMultiblock = true;
                return;
            }

            // Use the saved ID to get or create the multiblock
            be.batteryMultiblock = BatteryMultiblock.getOrCreateBatteryMultiblock(be.batteryMultiblockId, level);

            // Add this block to the multiblock (this is where the logging occurs)
            if (be.batteryMultiblock != null) {
                be.batteryMultiblock.addBattery(pos);
                be.joinedMultiblock = true;

                // Ensure the BlockEntity is marked for saving if the multiblock reference was just set.
                be.setChanged();
            }
        }

        // Add your existing tick logic here (e.g., energy transfer, etc.)
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

    // Public method to set multiblock
    public void setBatteryMultiblock(BatteryMultiblock multiblock) {
        this.batteryMultiblock = multiblock;
        if (multiblock != null) {
            this.batteryMultiblockId = multiblock.getMultiblockId();
            // IMPORTANT: If setting on Place, mark as joined to skip the tick logic
            this.joinedMultiblock = true;
        }
    }

    // Add public getter for multiblock
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
