package portal_controller;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.widget.ExtendedButton;
import org.lwjgl.glfw.GLFW;
import portal_multiblock.PortalStructure;
import portal_multiblock.PortalSettings;
import portal_multiblock.PortalLocation;
import portal_multiblock.PortalMultiblockManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PortalControllerScreen extends AbstractContainerScreen<PortalControllerMenu> {
    private static final ResourceLocation GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath("advanced_portals", "textures/gui/portal_controller.png");

    // Adjusted dimensions (based on most recent snippet)
    private static final int GUI_WIDTH = 300;
    private static final int GUI_HEIGHT = 180;

    // Button IDs (must match constants in PortalControllerMenu)
    private static final int BUTTON_APPLY_NAME = 2;
    private static final int BUTTON_TOGGLE_ACTIVATION = 3;
    private static final int BUTTON_ADD_PORTAL = 4;
    private static final int BUTTON_SCROLL_LINKED_UP = 5;
    private static final int BUTTON_SCROLL_LINKED_DOWN = 6;

    // --- Error Message State ---
    private String currentDisplayError = "";
    private int errorDisplayTimer = 0;
    private final int MAX_ERROR_DISPLAY_TICKS = 100; // 5 seconds (20 ticks per second)

    // Input fields
    private EditBox portalNameField;
    private EditBox addPortalField;

    // Buttons
    private Button applyNameButton;
    private Button addPortalButton;
    private Button toggleActivationButton;
    private Button scrollLinkedUpButton;
    private Button scrollLinkedDownButton;

    public PortalControllerScreen(PortalControllerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = this.imageHeight - 94; // Standard position
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.leftPos + this.imageWidth / 2;
        int startX = this.leftPos + 8;
        int startY = this.topPos + 20;

        // --- Portal Name Field and Button ---
        this.portalNameField = new EditBox(this.font, startX + 50, startY, 100, 18, Component.literal("Portal Name"));
        this.portalNameField.setMaxLength(32);
        this.portalNameField.setValue(this.menu.getCurrentPortalName());
        this.addRenderableWidget(this.portalNameField);

        this.applyNameButton = ExtendedButton.builder(Component.literal("Apply"), button -> {
            // Trigger server logic to apply name
            this.menu.setPortalNameInput(this.portalNameField.getValue());
            sendMenuButtonClick(BUTTON_APPLY_NAME);
        }).bounds(startX, startY, 50, 20).build();
        this.addRenderableWidget(this.applyNameButton);

        // --- Add Linked Portal Field and Button ---
        int addPortalY = startY + 25;
        this.addPortalField = new EditBox(this.font, startX + 50, addPortalY, 100, 18, Component.literal("Portal Name/UUID"));
        this.addPortalField.setMaxLength(64);
        this.addRenderableWidget(this.addPortalField);

        this.addPortalButton = ExtendedButton.builder(Component.literal("Link"), button -> {
            // Trigger server logic to add linked portal
            this.menu.setAddPortalInput(this.addPortalField.getValue());
            sendMenuButtonClick(BUTTON_ADD_PORTAL);
        }).bounds(startX, addPortalY, 50, 20).build();
        this.addRenderableWidget(this.addPortalButton);

        // --- Toggle Activation Button ---
        this.toggleActivationButton = ExtendedButton.builder(Component.literal("Activate"), button -> {
            // Trigger server logic to toggle activation
            sendMenuButtonClick(BUTTON_TOGGLE_ACTIVATION);
        }).bounds(this.leftPos + 180, this.topPos + 20, 110, 20).build();
        this.addRenderableWidget(this.toggleActivationButton);

        // --- Linked Portals Scroll Buttons ---
        this.scrollLinkedUpButton = ExtendedButton.builder(Component.literal("▲"), button -> {
            sendMenuButtonClick(BUTTON_SCROLL_LINKED_UP);
        }).bounds(this.leftPos + 280, this.topPos + 50, 20, 10).build();
        this.addRenderableWidget(this.scrollLinkedUpButton);

        this.scrollLinkedDownButton = ExtendedButton.builder(Component.literal("▼"), button -> {
            sendMenuButtonClick(BUTTON_SCROLL_LINKED_DOWN);
        }).bounds(this.leftPos + 280, this.topPos + 75, 20, 10).build();
        this.addRenderableWidget(this.scrollLinkedDownButton);
    }

    // Helper method to send button click from client to server menu
    private void sendMenuButtonClick(int buttonId) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();

        // --- Error Message Timer Logic ---
        if (this.errorDisplayTimer > 0) {
            this.errorDisplayTimer--;
        }

        // Poll the menu for error message
        String serverError = menu.getErrorMessage(); // Assumes getErrorMessage() exists in PortalControllerMenu
        if (!serverError.isEmpty()) {
            if (!serverError.equals(this.currentDisplayError) || this.errorDisplayTimer == 0) {
                this.currentDisplayError = serverError;
                this.errorDisplayTimer = MAX_ERROR_DISPLAY_TICKS;
            }
            // Acknowledge the message to the server so it doesn't resend it on the next tick
            menu.clearErrorMessage(); // Assumes clearErrorMessage() exists in PortalControllerMenu
        }

        // --- Update Name Field from Menu State ---
        if (!this.portalNameField.isFocused() && !this.portalNameField.getValue().equals(this.menu.getCurrentPortalName())) {
            this.portalNameField.setValue(this.menu.getCurrentPortalName());
        }

        // --- Update Activation Button Text ---
        Component newButtonText = this.menu.isPortalActive() ? Component.literal("Deactivate") : Component.literal("Activate");
        if (!this.toggleActivationButton.getMessage().equals(newButtonText)) {
            this.toggleActivationButton.setMessage(newButtonText);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.blit(GUI_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);

        // Labels for fields
        guiGraphics.drawString(this.font, Component.literal("Portal Name:"), 8, 23, 0x404040);
        guiGraphics.drawString(this.font, Component.literal("Link Portal:"), 8, 48, 0x404040);
        guiGraphics.drawString(this.font, Component.literal("Linked Portals:"), 178, 48, 0x404040);

        // --- Render Error Message ---
        if (errorDisplayTimer > 0 && !currentDisplayError.isEmpty()) {
            int textColor = 0xFF0000; // Red color for error
            int x = (this.imageWidth / 2);
            int y = this.imageHeight - 35; // Positioned right above the player inventory area

            guiGraphics.drawCenteredString(this.font, Component.literal(currentDisplayError), x, y, textColor);
        }

        // --- Render Linked Portal List ---
        // Display a scrolling list of linked portals
        List<String> linkedNames = this.menu.getLinkedPortalNames(); // Assumes this method exists
        int listX = 180;
        int listY = 65;
        int lineHeight = 10;
        int maxLines = 4;

        int startIndex = this.menu.getLinkedPortalsScrollOffset(); // Assumes this method exists
        for (int i = 0; i < maxLines; i++) {
            int index = startIndex + i;
            if (index < linkedNames.size()) {
                String name = linkedNames.get(index);
                // Truncate if too long
                if (name.length() > 20) {
                    name = name.substring(0, 17) + "...";
                }
                guiGraphics.drawString(this.font, Component.literal(name), listX, listY + i * lineHeight, 0x00AA00, false);
            }
        }
    }

    // Inner class for confirmation screen (copied from original snippet, ensure it's here)
    protected static class ConfirmationScreen extends AbstractContainerScreen<PortalControllerMenu> {
        private final Component title;
        private final Component message;
        private final java.util.function.Consumer<Boolean> callback;

        protected ConfirmationScreen(Component title, Component message, java.util.function.Consumer<Boolean> callback) {
            super(null, null, title); // Use null for menu/inventory as this is a transient screen
            this.title = title;
            this.message = message;
            this.callback = callback;
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int centerY = this.height / 2;

            this.addRenderableWidget(Button.builder(Component.literal("Yes"), button -> {
                callback.accept(true);
            }).bounds(centerX - 105, centerY + 20, 100, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("No"), button -> {
                callback.accept(false);
            }).bounds(centerX + 5, centerY + 20, 100, 20).build());
        }

        @Override
        protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
            // Render dark background
            this.renderBackground(guiGraphics);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
            this.renderBackground(guiGraphics);
            guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 30, 0xFFFFFF);
            guiGraphics.drawCenteredString(this.font, this.message, this.width / 2, this.height / 2 - 10, 0xCCCCCC);
            super.render(guiGraphics, mouseX, mouseY, partialTicks);
        }
    }
}