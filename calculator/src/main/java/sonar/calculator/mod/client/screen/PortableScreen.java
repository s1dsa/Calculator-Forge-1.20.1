package sonar.calculator.mod.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import sonar.calculator.mod.item.PortableMenu;

/** Portable menus preserve their individual calculator artwork and server-owned slot layout. */
public final class PortableScreen extends AbstractContainerScreen<PortableMenu> {
    private final ResourceLocation texture;
    public PortableScreen(PortableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = menu.getImageHeight();
        inventoryLabelY = imageHeight - 94;
        texture = new ResourceLocation("calculator", "textures/gui/" + switch (menu.getMode()) {
            case "scientific" -> "scientificcalculator.png";
            case "flawless" -> "flawlesscalculator.png";
            case "dynamic" -> "dynamiccalculator.png";
            case "crafting" -> "craftingcalculator.png";
            default -> "calculator.png";
        });
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (menu.getMode().equals("storage")) {
            ResourceLocation chest = new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");
            graphics.blit(chest, leftPos, topPos, 0, 0, imageWidth, 125);
            graphics.blit(chest, leftPos, topPos + 125, 0, 125, imageWidth, 97);
        } else graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int x, int y) {
        int titleY = menu.getMode().equals("dynamic") ? -12 : titleLabelY;
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, titleY, 0x404040, false);
        if (menu.getMode().equals("calculator") || menu.getMode().equals("scientific")) {
            String energy = menu.getEnergy() + " RF";
            graphics.drawString(font, energy, (imageWidth - font.width(energy)) / 2, 64, 0xFFFFFF, false);
        }
    }
    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, x, y, partialTick);
        renderTooltip(graphics, x, y);
    }
}
