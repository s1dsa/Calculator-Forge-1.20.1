package sonar.calculator.mod.client.screen;

import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import sonar.calculator.mod.machine.MachineBlockEntity;
import sonar.calculator.mod.machine.MachineMenu;

/**
 * Machine artwork, server-synchronized gauges and server-validated controls.
 *
 * <p>Layout follows the original GUI art (byte-identical upstream PNGs): titles are centred, the
 * energy read-out is white with no shadow and sits inside the energy bar, and slot graphics come
 * from the baked artwork instead of being drawn on top. All user-visible strings resolve through
 * translation keys.</p>
 */
public final class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final ResourceLocation VANILLA_INVENTORY = new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");
    private final String id;
    private final ResourceLocation texture;
    private int fabricationScroll;
    private final SideModeButton[] sideButtons = new SideModeButton[6];
    private LegacyImageButton pauseButton;
    private LegacyImageButton ejectButton;
    private Button whitelistButton;
    private Button exactButton;

    /** Process machines whose original GUI exposes the pause / eject ("移除提取物") icon pair (#7/#10). */
    private static final Set<String> DUAL_OUTPUT = Set.of("stoneseparator", "algorithmseparator", "extractionchamber", "precisionchamber");
    private static final Set<String> SMELTING = Set.of("reinforcedfurnace", "restorationchamber", "reassemblychamber", "processingchamber");
    private static final String[] SIDE_KEYS = {"down", "up", "north", "south", "west", "east"};

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        id = menu.getMachineId();
        imageHeight = menu.getImageHeight();
        inventoryLabelY = menu.playerInventoryY() - 10;
        String artwork = switch (id) {
            case "powercube", "creativepowercube" -> "guipowercube.png";
            case "advancedpowercube" -> "guiadvancedcube.png";
            case "handcrankedgenerator" -> "guicrank.png";
            case "stoneseparator", "algorithmseparator" -> "stoneseperator.png";
            case "precisionchamber", "extractionchamber" -> "extractionchamber.png";
            case "restorationchamber", "processingchamber", "reassemblychamber" -> "restorationchamber.png";
            case "analysingchamber" -> "analysischamber.png";
            case "storagechamber" -> "storage_chamber.png";
            case "fabricationchamber" -> "fabrication_chamber.png";
            case "moduleworkstation" -> "module_workstation.png";
            case "magneticflux" -> "magnetic_flux.png";
            case "hungerprocessor", "healthprocessor" -> "hungerprocessor.png";
            case "stoneassimilator", "algorithmassimilator" -> "assimilator.png";
            case "starchextractor" -> "starchgenerator.png";
            case "redstoneextractor" -> "redstonegenerator.png";
            case "glowstoneextractor" -> "glowstonegenerator.png";
            case "conductormast" -> "conductormast.png";
            case "calculatorlocator" -> "guicalculatorlocator.png";
            case "calculatorplug" -> "guicalculatorplug.png";
            case "co2generator" -> "guico2generator.png";
            case "weathercontroller" -> "guiweathercontroller.png";
            case "gaslanternon", "gaslanternoff" -> "guicalculatorplug.png";
            case "reinforcedchest" -> "";
            case "atomiccalculator", "dynamiccalculator", "atomicmultiplier", "reinforcedfurnace", "dockingstation", "basicgreenhouse", "advancedgreenhouse", "flawlessgreenhouse" -> id + ".png";
            default -> "";
        };
        texture = artwork.isEmpty() ? null : new ResourceLocation("calculator", "textures/gui/" + artwork);
    }

    private void action(int button) { if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button); }

    private void control(Component name, int action, int x, int y, int width) {
        addRenderableWidget(Button.builder(name, b -> action(action)).bounds(leftPos + x, topPos + y, width, 18).build());
    }

    @Override protected void init() {
        super.init();
        if (id.equals("weathercontroller")) {
            // Centre the complete rail + panel footprint; keep the rail on-screen at compact GUI scales.
            topPos = Math.max(20, (height - imageHeight - 20) / 2 + 20);
            leftPos = Math.max(0, Math.min(leftPos, Math.max(0, width - imageWidth)));
        }
        if (id.endsWith("greenhouse")) {
            control(Component.translatable("greenhouse.build"), 1, -70, 4, 64);
            control(Component.translatable("greenhouse.demolish"), 2, -70, 24, 64);
        } else if (id.equals("weathercontroller")) {
            // Weather controls live in a dedicated top rail above the title and energy/slot area.
            // The panel is 176px wide; the four bounded controls fit inside x=4..172 and y=-20..-2.
            control(Component.translatable("weather.time"), 100, 4, -20, 38);
            control(Component.translatable("weather.rain"), 101, 44, -20, 38);
            control(Component.translatable("weather.thunder"), 102, 84, -20, 38);
            control(Component.translatable("gui.calculator.toggle"), 11, 124, -20, 48);
        } else if (id.equals("magneticflux")) {
            whitelistButton = addRenderableWidget(Button.builder(Component.translatable("gui.calculator.whitelist"), b -> action(10)).bounds(leftPos + 8, topPos + 28, 76, 18).build());
            exactButton = addRenderableWidget(Button.builder(Component.translatable("gui.calculator.exact"), b -> action(11)).bounds(leftPos + 86, topPos + 28, 82, 18).build());
        } else if (id.equals("fabricationchamber")) {
            control(Component.translatable("gui.calculator.fabricate"), 10, 26, 87, 60);
        }
        if (DUAL_OUTPUT.contains(id) || SMELTING.contains(id)) {
            boolean dual = DUAL_OUTPUT.contains(id);
            pauseButton = addRenderableWidget(new LegacyImageButton(leftPos + (dual ? 8 : 22), topPos + 23,
                    b -> action(0), Component.translatable("buttons.pause")).icon(34, 0));
            ejectButton = addRenderableWidget(new LegacyImageButton(leftPos + (dual ? 149 : 136), topPos + 23,
                    b -> action(12), Component.translatable("buttons.circuits")).icon(0, 0));
        }
        if (id.equals("advancedpowercube")) {
            // #3: one button per world face, kept on the left of the panel and coloured like the face.
            for (int side = 0; side < 6; side++) {
                final int index = side;
                sideButtons[side] = addRenderableWidget(new SideModeButton(
                        leftPos + 4 + (side % 2) * 17, topPos + 6 + (side / 2) * 17,
                        Component.translatable("gui.calculator.side." + SIDE_KEYS[side]), b -> action(300 + index)));
            }
        }
    }

    private static int scaled(int value, int max, int pixels) {
        return max <= 0 ? 0 : (int) Math.max(0, Math.min(pixels, (long) value * pixels / max));
    }

    /** The original {@code FontHelper.formatStorage} compact read-out (e.g. {@code 50.0 KRF}). */
    private static String formatStorage(long power) {
        if (power < 1000) return power + " RF";
        if (power < 1000000) return round(1, power / 1000F) + " KRF";
        if (power < 1000000000L) return round(1, power / 1000000F) + " MRF";
        return round(2, power / 1000000000F) + " BRF";
    }

    /** The original {@code FontHelper.formatOutput} compact read-out (e.g. {@code 5.0 KRF/t}). */
    private static String formatOutput(long power) {
        if (power < 1000) return power + " RF/t";
        if (power < 1000000) return round(1, power / 1000F) + " KRF/t";
        if (power < 1000000000L) return round(1, power / 1000000F) + " MRF/t";
        return round(2, power / 1000000000F) + " BRF/t";
    }

    private static String round(int places, float value) {
        return new java.math.BigDecimal(Float.toString(value)).setScale(places, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private ResourceLocation backgroundTexture() {
        if (!id.equals("dockingstation")) return texture;
        // #9: the mounted calculator lives in the docking station's dedicated render slot, not slot 0.
        ItemStack mounted = menu.getMachine().getInventory().getStackInSlot(MachineBlockEntity.RENDER_CALCULATOR_SLOT);
        String artwork = switch (MachineBlockEntity.itemId(mounted)) {
            case "calculator" -> "calculator";
            case "scientificcalculator" -> "scientific";
            case "atomiccalculator" -> "atomic";
            case "flawlesscalculator" -> "flawless";
            default -> "";
        };
        return artwork.isEmpty() ? texture : new ResourceLocation("calculator", "textures/gui/dockingstation/" + artwork + ".png");
    }

    private void blit(GuiGraphics g, int x, int y, int u, int v, int width, int height) {
        if (width > 0 && height > 0) g.blit(backgroundTexture(), leftPos + x, topPos + y, u, v, width, height);
    }

    @Override protected void renderBg(GuiGraphics g, float tick, int mouseX, int mouseY) {
        // A tiled chest panel covers extended inventories without sampling past legacy artwork.
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xffc6c6c6);
        if (texture != null) {
            int panelHeight = imageHeight;
            g.blit(backgroundTexture(), leftPos, topPos, 0, 0, imageWidth, panelHeight);
        }
        int playerY = menu.playerInventoryY();
        for (int i = 0; i < menu.getPlayerSlotStart(); i++) {
            if (texture != null && i < menu.getMachineSlotCount()) continue;
            var slot = menu.slots.get(i);
            g.blit(VANILLA_INVENTORY, leftPos + slot.x - 1, topPos + slot.y - 1, 7, 17, 18, 18);
        }
        if (id.equals("fabricationchamber")) {
            var slot = menu.slots.get(menu.slots.size() - 1);
            g.blit(VANILLA_INVENTORY, leftPos + slot.x - 1, topPos + slot.y - 1, 7, 17, 18, 18);
        }
        if (texture == null) {
            for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
                g.blit(VANILLA_INVENTORY, leftPos + 7 + col * 18, topPos + playerY - 1 + row * 18,
                        7, 17, 18, 18);
            for (int col = 0; col < 9; col++)
                g.blit(VANILLA_INVENTORY, leftPos + 7 + col * 18, topPos + playerY + 57,
                        7, 17, 18, 18);
        }
        if (id.equals("fabricationchamber")) {
            var recipes = menu.getFabricationRecipes();
            for (int row = 0; row < 4 && fabricationScroll + row < recipes.size(); row++) {
                var output = recipes.get(fabricationScroll + row).getResultItem(menu.machine.getLevel().registryAccess());
                boolean selected = ItemStack.isSameItemSameTags(output, menu.getFabricationTarget());
                int y = topPos + 7 + row * 18;
                g.fill(leftPos + 5, y - 1, leftPos + 92, y + 17, selected ? 0xff78a878 : 0xff888888);
                g.renderItem(output, leftPos + 7, y);
                g.drawString(font, font.plainSubstrByWidth(output.getHoverName().getString(), 64), leftPos + 25, y + 4, 0xffffff, false);
            }
            g.drawString(font, Component.translatable("gui.calculator.required"), leftPos + 105, topPos + 7, 0x404040, false);
            var target = menu.getFabricationTarget();
            var recipe = recipes.stream().filter(r -> ItemStack.isSameItemSameTags(target,
                    r.getResultItem(menu.machine.getLevel().registryAccess()))).findFirst();
            if (recipe.isPresent()) {
                int at = 0;
                for (var input : recipe.get().getInputs()) {
                    var choices = input.ingredient().getItems();
                    if (choices.length == 0) continue;
                    var stack = choices[0].copy(); stack.setCount(input.count()); stack.setTag(input.exactNbt());
                    int x = leftPos + 100 + at % 4 * 18, y = topPos + 22 + at / 4 * 18;
                    g.renderItem(stack, x, y); g.renderItemDecorations(font, stack, x, y); at++;
                }
            } else g.drawString(font, Component.translatable(target.isEmpty() ? "gui.calculator.noRecipe" : "gui.calculator.invalid"), leftPos + 101, topPos + 24, 0x404040, false);
            blit(g, 5, 89, 176, 16, scaled(Math.max(0, Math.min(200, menu.getProgress() - 50)), 200, 20), 16);
        }
        int energy = scaled(menu.getEnergyStored(), menu.getEnergyCapacity(), 78);
        int progress = scaled(menu.getProgress(), menu.getProcessTime(), 24);
        switch (id) {
            case "reinforcedfurnace", "restorationchamber", "processingchamber", "reassemblychamber" -> { blit(g,49,63,176,0,energy,10); blit(g,76,24,176,10,progress,16); }
            case "stoneseparator", "algorithmseparator", "precisionchamber", "extractionchamber" -> { blit(g,49,63,176,0,energy,10); blit(g,62,24,176,10,progress,16); }
            case "powercube", "advancedpowercube", "creativepowercube", "handcrankedgenerator", "analysingchamber", "calculatorlocator", "co2generator", "weathercontroller", "dockingstation" -> blit(g,49,63,176,0,energy,10);
            case "starchextractor", "redstoneextractor", "glowstoneextractor" -> {
                blit(g,8,63,0,186,scaled(menu.getEnergyStored(),menu.getEnergyCapacity(),160),10);
                blit(g,30,17,0,166,scaled(menu.getStateValue("burnTime"),menu.getStateValue("maxBurnTime"),116),10);
                blit(g,30,41,0,176,scaled(menu.getStateValue("itemLevel"),5000,116),10);
            }
            case "atomicmultiplier" -> { blit(g,41,65,0,166,scaled(menu.getEnergyStored(),menu.getEnergyCapacity(),94),10); blit(g,79,20,176,0,scaled(menu.getProgress(),menu.getProcessTime(),16),9); }
            case "conductormast" -> {
                blit(g,22,59,0,166,scaled(menu.getEnergyStored(),menu.getEnergyCapacity(),132),20);
                blit(g,79,26,176,0,scaled(menu.getStateValue("lightningTimer"),menu.getStateValue("lightningDelay"),16),9);
            }
            case "basicgreenhouse", "advancedgreenhouse", "flawlessgreenhouse" -> {
                int offset = id.equals("flawlessgreenhouse") ? 27 : 81;
                int e = scaled(menu.getEnergyStored(),menu.getEnergyCapacity(),46);
                blit(g,offset,57-e,176,46-e,14,e);
                int carbon = scaled(menu.getStateValue("carbon"),100000,66);
                blit(g,offset+20,77-carbon,190,66-carbon,28,carbon);
                int oxygen = scaled(100000 - menu.getStateValue("carbon"),100000,66);
                blit(g,offset+56,77-oxygen,218,66-oxygen,28,oxygen);
            }
        }
    }

    /** Vertical position of the energy read-out, matching each original GUI (inside the bar). */
    private int energyLabelY() {
        int[] bar = energyBar();
        return bar == null ? 64 : bar[1] + (bar[3] - 8) / 2;
    }

    /**
     * Energy bar rectangle {x, y, width, height} in GUI space, matching the blit in {@link #renderBg}.
     * Every machine centres its bar horizontally on the 176 wide panel, so a single centred label works,
     * but the vertical position and the hover region differ per machine (#2/#12).
     */
    private int[] energyBar() {
        return switch (id) {
            case "starchextractor", "redstoneextractor", "glowstoneextractor" -> new int[]{8, 63, 160, 10};
            case "atomicmultiplier" -> new int[]{41, 65, 94, 10};
            case "conductormast" -> new int[]{22, 59, 132, 20};
            case "reinforcedfurnace", "restorationchamber", "processingchamber", "reassemblychamber",
                 "stoneseparator", "algorithmseparator", "precisionchamber", "extractionchamber",
                 "powercube", "advancedpowercube", "creativepowercube", "handcrankedgenerator",
                 "analysingchamber", "calculatorlocator", "co2generator", "weathercontroller",
                 "dockingstation" -> new int[]{49, 63, 78, 10};
            default -> null;
        };
    }

    private void centredLabel(GuiGraphics g, Component text, int y, int colour) {
        g.drawString(font, text, (imageWidth - font.width(text)) / 2, y, colour, false);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (!id.equals("fabricationchamber")) {
            // Keep titles above panels whose content starts on the first row.
            Component title = Component.literal(font.plainSubstrByWidth(this.title.getString(), 162));
            centredLabel(g, title, id.equals("calculatorlocator") || id.equals("dynamiccalculator") ? -12 : 6, 0x404040);
        }
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0x404040, false);
        // #2: white, shadow-less, centred inside the energy bar (was grey at y=74, colliding with the label).
        int[] energyRect = energyBar();
        if (menu.getEnergyCapacity() > 0 && energyRect != null) {
            String value = formatStorage(menu.getEnergyStored());
            g.drawString(font, value, energyRect[0] + (energyRect[2] - font.width(value)) / 2,
                    energyLabelY(), 0xFFFFFF, false);
        }
        if (id.equals("calculatorlocator")) locatorStatus(g);
        if (id.equals("hungerprocessor") || id.equals("healthprocessor"))
            centredLabel(g, Component.translatable("gui.calculator.points").append(": " + menu.getStateValue("points")), 60, 0x404040);
    }

    /** The four original locator status lines (upstream {@code GuiCalculatorLocator}). */
    private void locatorStatus(GuiGraphics g) {
        int size = menu.getStateValue("size");
        int stability = menu.getStateValue("stability");
        boolean active = menu.isMachineActive();
        String owner = menu.getMachineOwner();
        line(g, 0, Component.translatable("locator.active").append(": ").append(active
                ? Component.literal(formatOutput(menu.getStateValue("currentGen"))) : Component.translatable("locator.false")), active);
        line(g, 1, Component.translatable("locator.multiblock").append(": ").append(size != 0
                ? Component.translatable("locator.true") : Component.translatable("locator.false")), size != 0);
        line(g, 2, Component.translatable("locator.owner").append(": ").append(!owner.isEmpty()
                ? Component.literal(owner) : Component.translatable("locator.none")), !owner.isEmpty());
        line(g, 3, Component.translatable("circuit.stability").append(": ").append(size != 0
                ? Component.literal(stability == 0 ? "0%" : menu.getStabilityPercent() + "%")
                : Component.translatable("locator.unknown")), stability != 0 && size != 0);
    }

    private void line(GuiGraphics g, int index, Component text, boolean ok) {
        g.drawString(font, text, 25, 10 + index * 11, ok ? 0x404040 : 0xFFFFFF, false);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (id.equals("fabricationchamber") && button == 0 && mouseX >= leftPos + 5 && mouseX < leftPos + 92
                && mouseY >= topPos + 6 && mouseY < topPos + 78) {
            int index = fabricationScroll + (int) (mouseY - topPos - 6) / 18;
            if (index < menu.getFabricationRecipes().size()) action(MachineMenu.FABRICATION_BUTTON_BASE + index);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (id.equals("fabricationchamber") && mouseX >= leftPos && mouseX < leftPos + imageWidth
                && mouseY >= topPos && mouseY < topPos + 80) {
            fabricationScroll = Math.max(0, Math.min(Math.max(0, menu.getFabricationRecipes().size() - 4), fabricationScroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override public void render(GuiGraphics g, int x, int y, float tick) {
        if (id.equals("advancedpowercube")) for (int side = 0; side < 6; side++) if (sideButtons[side] != null) {
            int mode = menu.getEnergySideMode(side);
            sideButtons[side].setMode(mode);
            sideButtons[side].setTooltip(Tooltip.create(Component.translatable("gui.calculator.side." + SIDE_KEYS[side])
                    .append(": ").append(Component.translatable(switch (mode) {
                        case 0 -> "gui.calculator.side.input";
                        case 1 -> "gui.calculator.side.output";
                        default -> "gui.calculator.side.disabled";
                    }))));
        }
        if (pauseButton != null) {
            boolean paused = menu.getStateValue("paused") != 0;
            pauseButton.icon(paused ? 51 : 34, 0);
            pauseButton.setTooltip(Tooltip.create(Component.translatable(paused ? "buttons.resume" : "buttons.pause")));
        }
        if (whitelistButton != null) {
            boolean whitelist = menu.getStateValue("whitelist") != 0;
            whitelistButton.setMessage(Component.translatable(whitelist ? "gui.calculator.whitelist" : "gui.calculator.blacklist"));
            boolean exact = menu.getStateValue("exact") != 0;
            exactButton.setMessage(Component.translatable(exact ? "gui.calculator.exact" : "gui.calculator.fuzzy"));
        }
        renderBackground(g); super.render(g, x, y, tick); renderTooltip(g, x, y);
        // #2/#12: the hover region follows the machine's own energy bar instead of a fixed rectangle.
        int[] bar = energyBar() != null ? energyBar() : new int[]{49, 63, 78, 10};
        if (menu.getEnergyCapacity() > 0 && x >= leftPos + bar[0] && x < leftPos + bar[0] + bar[2]
                && y >= topPos + bar[1] && y < topPos + bar[1] + bar[3])
            g.renderTooltip(font, Component.literal(menu.getEnergyStored() + " / " + menu.getEnergyCapacity() + " FE"), x, y);
    }
}
