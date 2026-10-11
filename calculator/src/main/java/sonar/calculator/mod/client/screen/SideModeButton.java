package sonar.calculator.mod.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Per-face energy mode button for the advanced power cube (#3). Its colour tracks the world face
 * colour: yellow = INPUT, white = OUTPUT, dark grey = DISABLED, so the GUI and the rendered face
 * agree. The click still goes through the ordinary menu button protocol.
 */
public class SideModeButton extends Button {
    private int mode;

    public SideModeButton(int x, int y, Component label, OnPress onPress) {
        super(x, y, 16, 16, label, onPress, DEFAULT_NARRATION);
    }

    public void setMode(int mode) { this.mode = mode; }

    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int border = switch (mode) {
            case 0 -> 0xFFE0C000;   // INPUT: yellow lightning
            case 1 -> 0xFFFFFFFF;   // OUTPUT: white lightning
            default -> 0xFF3A3A3A;  // DISABLED: no lightning
        };
        int fill = switch (mode) {
            case 0 -> 0xFF5A4A00;
            case 1 -> 0xFF4A4A4A;
            default -> 0xFF1A1A1A;
        };
        g.fill(getX(), getY(), getX() + width, getY() + height, border);
        g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, fill);
        int colour = mode == 1 ? 0x000000 : 0xFFFFFF;
        Component label = getMessage();
        int x = getX() + (width - Minecraft.getInstance().font.width(label)) / 2;
        g.drawString(Minecraft.getInstance().font, label, x, getY() + 4, colour, false);
        if (isHoveredOrFocused()) g.fill(getX(), getY(), getX() + width, getY() + height, 0x40FFFFFF);
    }
}
