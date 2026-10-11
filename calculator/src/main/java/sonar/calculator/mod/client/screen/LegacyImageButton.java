package sonar.calculator.mod.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Upstream legacy icon button (#7/#10 pause + eject "移除提取物"), drawn from the original
 * {@code calculator:textures/gui/buttons/buttons.png} atlas so the port keeps the authentic 16x16 art
 * instead of a text-only modern button. Behaviour and tooltips stay translatable.
 */
public class LegacyImageButton extends Button {
    private static final ResourceLocation TEXTURE = new ResourceLocation("calculator", "textures/gui/buttons/buttons.png");
    private int u, v;

    public LegacyImageButton(int x, int y, OnPress onPress, Component tooltip) {
        super(x, y, 16, 16, Component.empty(), onPress, DEFAULT_NARRATION);
        setTooltip(net.minecraft.client.gui.components.Tooltip.create(tooltip));
    }

    /** Selects the atlas cell (u,v) of the 16x16 icon to draw, e.g. (0,0) eject, (34,0)/(51,0) pause/resume. */
    public LegacyImageButton icon(int u, int v) { this.u = u; this.v = v; return this; }

    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.blit(TEXTURE, getX(), getY(), u, v, 16, 16);
        if (isHoveredOrFocused()) g.fill(getX(), getY(), getX() + 16, getY() + 16, 0x40FFFFFF);
    }
}
