package portal_multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;

public class PortalLocation {
    private String name;
    private ResourceKey<Level> dimension;
    private BlockPos position;
    private float yaw;
    private float pitch;

    public PortalLocation(String name, ResourceKey<Level> dimension, BlockPos position, float yaw, float pitch) {
        this.name = name;
        this.dimension = dimension;
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ResourceKey<Level> getDimension() { return dimension; }
    public BlockPos getPosition() { return position; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("dimension", dimension.location().toString());
        tag.putInt("x", position.getX());
        tag.putInt("y", position.getY());
        tag.putInt("z", position.getZ());
        tag.putFloat("yaw", yaw);
        tag.putFloat("pitch", pitch);
        return tag;
    }

    public static PortalLocation load(CompoundTag tag) {
        String name = tag.getString("name");
        ResourceKey<Level> dimension = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.parse(tag.getString("dimension"))
        );

        BlockPos pos = new BlockPos(
                tag.getInt("x"),
                tag.getInt("y"),
                tag.getInt("z")
        );
        float yaw = tag.getFloat("yaw");
        float pitch = tag.getFloat("pitch");

        return new PortalLocation(name, dimension, pos, yaw, pitch);
    }

    // --- NEW: Packet Serialization Methods ---

    /** Writes this PortalLocation object to a FriendlyByteBuf. */
    public void writeToBuffer(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.name);
        buffer.writeResourceLocation(this.dimension.location());
        buffer.writeBlockPos(this.position);
        buffer.writeFloat(this.yaw);
        buffer.writeFloat(this.pitch);
    }

    /** Static method to read a PortalLocation object from a FriendlyByteBuf. */
    public static PortalLocation readFromBuffer(FriendlyByteBuf buffer) {
        String name = buffer.readUtf();

        ResourceLocation dimLoc = buffer.readResourceLocation();
        ResourceKey<Level> dimension = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, dimLoc);

        BlockPos pos = buffer.readBlockPos();
        float yaw = buffer.readFloat();
        float pitch = buffer.readFloat();

        return new PortalLocation(name, dimension, pos, yaw, pitch);
    }
}