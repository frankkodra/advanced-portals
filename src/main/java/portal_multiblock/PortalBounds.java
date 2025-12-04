package portal_multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;

public class PortalBounds {
    private final BlockPos center;
    private final boolean constantX; // true if X is constant (vertical plane on X), false if Z is constant
    private final int constantValue;
    private final int width;
    private final int height;
    private final AABB detectionArea;

    private PortalBounds(BlockPos center, boolean constantX, int constantValue, int width, int height) {
        this.center = center;
        this.constantX = constantX;
        this.constantValue = constantValue;
        this.width = width;
        this.height = height;
        this.detectionArea = calculateDetectionArea();
    }

    public static PortalBounds calculateFromFrame(Set<BlockPos> frameBlocks) {
        if (frameBlocks.isEmpty()) return null;

        // Determine plane orientation
        Set<Integer> xValues = new HashSet<>();
        Set<Integer> zValues = new HashSet<>();

        for (BlockPos pos : frameBlocks) {
            xValues.add(pos.getX());
            zValues.add(pos.getZ());
        }

        boolean constantX = xValues.size() == 1;
        boolean constantZ = zValues.size() == 1;

        if (!constantX && !constantZ) return null;

        int constantValue = constantX ? xValues.iterator().next() : zValues.iterator().next();

        // Find min/max coordinates
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minOther = Integer.MAX_VALUE, maxOther = Integer.MIN_VALUE;

        for (BlockPos pos : frameBlocks) {
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());

            if (constantX) {
                int z = pos.getZ();
                minOther = Math.min(minOther, z);
                maxOther = Math.max(maxOther, z);
            } else {
                int x = pos.getX();
                minOther = Math.min(minOther, x);
                maxOther = Math.max(maxOther, x);
            }
        }

        // Calculate interior dimensions
        int interiorWidth = maxOther - minOther - 1;
        int interiorHeight = maxY - minY - 1;

        if (interiorWidth < 1 || interiorHeight < 2) return null;

        // Calculate center point (coordinate center, not block center)
        double centerX, centerY, centerZ;
        if (constantX) {
            centerX = constantValue + 0.5;
            centerY = (minY + maxY) / 2.0 + 0.5;
            centerZ = (minOther + maxOther) / 2.0 + 0.5;
        } else {
            centerX = (minOther + maxOther) / 2.0 + 0.5;
            centerY = (minY + maxY) / 2.0 + 0.5;
            centerZ = constantValue + 0.5;
        }

        BlockPos center = new BlockPos((int)Math.floor(centerX), (int)Math.floor(centerY), (int)Math.floor(centerZ));

        return new PortalBounds(center, constantX, constantValue, interiorWidth, interiorHeight);
    }

    private AABB calculateDetectionArea() {
        // Paper-thin detection area (0.1 blocks thick) centered on portal surface
        double thickness = 0.05; // Half of 0.1

        if (constantX) {
            // Vertical plane on X
            double minX = constantValue + 0.5 - thickness;
            double maxX = constantValue + 0.5 + thickness;
            double minZ = center.getZ() - width / 2.0;
            double maxZ = center.getZ() + width / 2.0;
            double minY = center.getY() - height / 2.0;
            double maxY = center.getY() + height / 2.0;
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        } else {
            // Vertical plane on Z
            double minZ = constantValue + 0.5 - thickness;
            double maxZ = constantValue + 0.5 + thickness;
            double minX = center.getX() - width / 2.0;
            double maxX = center.getX() + width / 2.0;
            double minY = center.getY() - height / 2.0;
            double maxY = center.getY() + height / 2.0;
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    public BlockPos getCenter() { return center; }
    public boolean isConstantX() { return constantX; }
    public int getConstantValue() { return constantValue; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public AABB getDetectionArea() { return detectionArea; }

    public boolean contains(double x, double y, double z) {
        return detectionArea.contains(x, y, z);
    }
}