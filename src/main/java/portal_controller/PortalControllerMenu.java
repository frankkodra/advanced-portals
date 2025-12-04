package portal_controller;

import advanced_portals.MenuRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import portal_multiblock.PortalMultiblockManager;
import portal_multiblock.PortalStructure;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PortalControllerMenu extends AbstractContainerMenu {
    private final UUID portalId;
    private final BlockPos controllerPos;
    private final Player player;
    private final ContainerLevelAccess access;

    // Button IDs (must match constants in PortalControllerScreen)
    private static final int BUTTON_APPLY_NAME = 2;
    private static final int BUTTON_TOGGLE_ACTIVATION = 3;
    private static final int BUTTON_ADD_PORTAL = 4;
    private static final int BUTTON_SCROLL_LINKED_UP = 5;
    private static final int BUTTON_SCROLL_LINKED_DOWN = 6;

    // Portal Structure Data (from initial packet and server updates)
    private String currentPortalName = "Unnamed Portal";
    private String connectingToPortal= "";
    private boolean isPortalActive = false;
    private List<String> linkedPortalNames = new ArrayList<>();
    private int linkedPortalsScrollOffset = 0;
    private float powerFillPercentage = 0.0f;
    private float fluidFillPercentage = 0.0f;

    // GUI Input Data (set by client, processed on button press)
    private String portalNameInput = "";
    private String addPortalInput = "";

    // ERROR COMMUNICATION (Server -> Client)
    private String errorMessage = "";

    public PortalControllerMenu(int containerId, Inventory playerInventory, UUID portalId, BlockPos controllerPos) {
        super(MenuRegistry.PORTAL_CONTROLLER_MENU.get(), containerId);
        this.portalId = portalId;
        this.controllerPos = controllerPos;
        this.player = playerInventory.player;
        this.access = ContainerLevelAccess.create(player.level(), controllerPos);

        layoutPlayerInventorySlots(playerInventory);

        // Initial state sync (Server side constructor calls this)
        if (!player.level().isClientSide) {
            PortalStructure portal = PortalMultiblockManager.getPortalStructure(portalId);
            if (portal != null) {
                syncPortalState(portal);
            }
        }
    }

    // ================= CLIENT-SIDE GETTERS (Used by PortalControllerScreen) =================

    public String getErrorMessage() {
        return errorMessage;
    }

    // Called by the client screen after reading the error message
    public void clearErrorMessage() {
        this.errorMessage = "";
    }

    public String getCurrentPortalName() {
        return currentPortalName;
    }

    public boolean isPortalActive() {
        return isPortalActive;
    }

    public List<String> getLinkedPortalNames() {
        return linkedPortalNames;
    }

    public int getLinkedPortalsScrollOffset() {
        return linkedPortalsScrollOffset;
    }

    // ================= CLIENT-SIDE SETTERS (For Input Fields) =================

    public void setPortalNameInput(String portalNameInput) {
        this.portalNameInput = portalNameInput;
    }

    public void setAddPortalInput(String addPortalInput) {
        this.addPortalInput = addPortalInput;
    }

    // ================= SERVER-SIDE LOGIC =================

    /**
     * Called on the server side to handle button clicks from the client.
     */
    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (player.level().isClientSide) return false;

        PortalStructure portal = PortalMultiblockManager.getPortalByName(this.currentPortalName);
        PortalStructure portalConnectingTo=PortalMultiblockManager.getPortalByName(addPortalInput);
        if (portal == null || !portal.isValid) {
            setErrorMessage("Error: Portal structure not found or controller removed.");
            return false;
        }

        switch (buttonId) {
            case BUTTON_APPLY_NAME:
                return handleApplyName(portal);
            case BUTTON_TOGGLE_ACTIVATION:
                return handleToggleActivation(portal,portalConnectingTo );
            case BUTTON_SCROLL_LINKED_UP:
                scrollLinkedPortals(-1);
                return true;
            case BUTTON_SCROLL_LINKED_DOWN:
                scrollLinkedPortals(1);
                return true;
            default:
                return super.clickMenuButton(player, buttonId);
        }
    }

    private void scrollLinkedPortals(int direction) {
        int maxOffset = Math.max(0, linkedPortalNames.size() - 4); // Show 4 lines
        linkedPortalsScrollOffset += direction;
        linkedPortalsScrollOffset = Math.max(0, Math.min(maxOffset, linkedPortalsScrollOffset));
    }

    /**
     * Handles the logic for applying a new portal name.
     * Throws an error if the name already exists.
     */
    private boolean handleApplyName(PortalStructure portal) {
        String newName = portalNameInput.trim();

        if (newName.isEmpty()) {
            setErrorMessage("Portal name cannot be empty.");
            return false;
        }

        // Check if the name is already in use by another portal
        if (PortalMultiblockManager.isNameTaken(newName, portal.getPortalId())) {
            setErrorMessage("Error: Portal name '" + newName + "' already exists.");
            return false;
        }

        // Check if the name is the same as the current one
        if (newName.equals(portal.getPortalName())) {
            setErrorMessage("Name is already set to '" + newName + "'.");
            return false;
        }

        // Update name in PortalStructure and PortalMultiblockManager
        PortalMultiblockManager.renamePortal(portal, portal.getPortalName(), newName);

        // Success: Clear error and update client state
        setErrorMessage("");
        syncPortalState(portal);
        return true;
    }

    /**
     * Handles the logic for toggling portal activation.
     * Throws an error if the linked portal name is not found (implicitly handled by PortalStructure.tryActivate).
     */
    private boolean handleToggleActivation(PortalStructure portal,PortalStructure teleportToPortal) {
        if (portal.isActive()) {
            // DEACTIVATE

            setErrorMessage(""); // Clear any previous error
            syncPortalState(portal);
            return true;
        } else {
            // ACTIVATE

            // Perform checks and attempt activation
            String error = portal.activatePortal(teleportToPortal); // Assume tryActivate handles all checks (frame, power, fluid, linked portals)

            if (error != null) {
                // Activation failed, set the error message
                setErrorMessage(error);
                // Ensure client state is synchronized (active=false, but name/linked list might change)
                syncPortalState(portal);
                return false;
            } else {
                // Activation succeeded
                setErrorMessage("Portal Activated.");
                syncPortalState(portal);
                return true;
            }
        }
    }

    private void setErrorMessage(String s) {
        errorMessage = s;
    }

    // ================= SYNCING LOGIC =================

    /**
     * Synchronizes the necessary PortalStructure data to the menu fields for client consumption.
     * This is called on the server side.
     */
    private void syncPortalState(PortalStructure portal) {
        this.currentPortalName = portal.getPortalName();
        this.isPortalActive = portal.isActive();
        this.powerFillPercentage = portal.getPowerFillPercentage();
        this.fluidFillPercentage = portal.getFluidFillPercentage();

        // Synchronize linked portal names (convert UUIDs to names)
        this.linkedPortalNames.clear();
        for(UUID linkedId : portal.getLinkedPortals()) {
            PortalStructure linkedPortal = PortalMultiblockManager.getPortalStructure(linkedId);
            if (linkedPortal != null) {
                this.linkedPortalNames.add(linkedPortal.getPortalName());
            } else {
                this.linkedPortalNames.add("Unknown Portal (" + linkedId.toString().substring(0, 8) + ")");
            }
        }

        // Important: Tell Minecraft to update the tracked data to the client
        // This relies on the standard AbstractContainerMenu tracking which is limited for strings,
        // but is necessary for the primitive fields. The string fields will be updated when the menu is synced.
        // A dedicated packet or DataComponent should be used for reliable string sync, but we rely on the
        // client polling or the Menu's default sync mechanism here.

        // This call ensures primitive fields (like power, fluid, and activation state) are sent.
        broadcastChanges();
    }

    // --- Boilerplate (keep inventory methods and stillValid) ---

    private void layoutPlayerInventorySlots(Inventory playerInventory) {
        IItemHandler playerInventoryForge = new InvWrapper(playerInventory);
        int startX = 8;
        int startY = 104; // Adjust Y position for the smaller GUI height

        // Hotbar (y: 162)
        for (int col = 0; col < 9; col++) {
            addSlot(new SlotItemHandler(playerInventoryForge, col, startX + col * 18, startY + 58));
        }

        // Main inventory (y: 104)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new SlotItemHandler(playerInventoryForge, col + row * 9 + 9,
                        startX + col * 18, startY + row * 18));
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof PortalControllerBlockEntity controller) {
                // Ensure the player is still near the block and the portal structure still exists
                return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 64d &&
                        PortalMultiblockManager.getPortalStructure(portalId) != null;
            }
            return false;
        }, true);
    }

    // Removed unused syncPortalState method that uses data fields for brevity,
    // and replaced with the custom `syncPortalState` above.
}