package sonar.calculator.mod.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import sonar.calculator.mod.CalculatorContent;
import sonar.calculator.mod.item.modules.FlawlessCalculatorItem;
import sonar.calculator.mod.item.modules.ModuleMenu;

/** Original module selector and Info Calculator list/detail navigation. */
public final class ModuleScreen extends AbstractContainerScreen<ModuleMenu> {
    private static final ResourceLocation MODULE = texture("module_selection"), LIST = texture("info_calculator"), DETAIL = texture("info_calculator_item");
    private static final String[] CATEGORY_ICONS = {"", "calculator", "energymodule", "circuitboard", "hungerprocessor", "handcrankedgenerator", "powercube", "wrench", "soil", "purifiedobsidian"};
    private final List<InfoCatalog.Entry> catalog;
    private List<InfoCatalog.Entry> filtered;
    private InfoCatalog.Category category = InfoCatalog.Category.All;
    private InfoCatalog.Entry detail;
    private final List<InfoCatalog.Entry> history = new ArrayList<>();
    private final List<List<FormattedCharSequence>> pages = new ArrayList<>();
    private EditBox search;
    private String query = "";
    private int first, page;
    private boolean dragging;
    public ModuleScreen(ModuleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 136;
        imageHeight = 166;
        catalog = menu.isInfo() ? InfoCatalog.entries() : List.of();
        filtered = catalog;
    }
    private static ResourceLocation texture(String name) { return new ResourceLocation("calculator", "textures/gui/" + name + ".png"); }
    private static ItemStack icon(String id) {
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id.contains(":") ? id : "calculator:" + id));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
    @Override protected void init() {
        super.init();
        if (!menu.isInfo()) return;
        if (detail == null) {
            search = new EditBox(font, leftPos + 5, topPos + 16, 125, 12, Component.translatable("gui.calculator.search"));
            search.setMaxLength(64);
            search.setValue(query);
            search.setResponder(value -> { query = value; filter(); });
            addRenderableWidget(search);
            for (int i = 1; i < InfoCatalog.Category.values().length; i++) {
                final InfoCatalog.Category choice = InfoCatalog.Category.values()[i];
                addRenderableWidget(Button.builder(Component.literal(""), b -> { category = choice; filter(); }).bounds(leftPos + 136, topPos + 2 + (i - 1) * 18, 18, 18).build());
            }
        } else {
            addRenderableWidget(Button.builder(Component.literal("<<"), b -> nextEntry(-1)).bounds(leftPos + 6, topPos + 140, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { if (page > 0) page--; else nextEntry(-1); }).bounds(leftPos + 26, topPos + 140, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { if (page + 1 < pages.size()) page++; else nextEntry(1); }).bounds(leftPos + 90, topPos + 140, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">>"), b -> nextEntry(1)).bounds(leftPos + 110, topPos + 140, 20, 20).build());
            for (int i = 0; i < detail.related().size(); i++) {
                final String id = detail.related().get(i);
                addRenderableWidget(Button.builder(Component.empty(), b -> catalog.stream().filter(e -> e.id().equals(id)).findFirst().ifPresent(e -> {
                    history.add(detail); category = InfoCatalog.Category.All; query = ""; filter(); show(e);
                })).bounds(leftPos + 136, topPos + 2 + i * 18, 18, 18).build());
            }
        }
    }
    private void filter() {
        filtered = catalog.stream().filter(e -> category == InfoCatalog.Category.All || e.category() == category)
                .filter(e -> e.stack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        first = 0;
    }
    private void show(InfoCatalog.Entry entry) {
        detail = entry; page = 0; pages.clear();
        List<FormattedCharSequence> current = new ArrayList<>();
        String text = entry.text().getString();
        for (String paragraph : text.split("-", -1)) {
            for (FormattedCharSequence line : font.split(Component.literal(paragraph.isBlank() ? " " : "• " + paragraph), 158)) {
                if (current.size() == 12) { pages.add(List.copyOf(current)); current.clear(); }
                current.add(line);
            }
        }
        if (!current.isEmpty()) pages.add(List.copyOf(current));
        rebuildWidgets();
    }
    private void nextEntry(int delta) {
        if (filtered.isEmpty()) return;
        history.clear();
        show(filtered.get(Math.floorMod(filtered.indexOf(detail) + delta, filtered.size())));
    }
    private int count() { return menu.isInfo() ? filtered.size() : FlawlessCalculatorItem.MODULE_CAPACITY; }
    private int maxFirst() { return Math.max(0, count() - 7); }
    private void scrollTo(double y) { first = (int)Math.round(Math.max(0, Math.min(1, (y - topPos - 37.5) / 113)) * maxFirst()); }
    @Override protected void renderBg(GuiGraphics g, float tick, int x, int y) {
        ResourceLocation tex = menu.isInfo() ? detail == null ? LIST : DETAIL : MODULE;
        g.blit(tex, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        if (detail == null) {
            for (int row = 0; row < 7; row++) g.blit(tex, leftPos + 4, topPos + 30 + row * 18, 0,
                    !menu.isInfo() && first + row == menu.currentSlot() ? 184 : 166, 119, 18);
            g.blit(tex, leftPos + 124, topPos + 30 + (maxFirst() == 0 ? 0 : first * 113 / maxFirst()), menu.isInfo() ? 119 : 136, menu.isInfo() ? 166 : 0, 8, 15);
        }
    }
    @Override protected void renderLabels(GuiGraphics g, int x, int y) {
        if (detail == null) {
            g.drawString(font, font.plainSubstrByWidth(title.getString(), 126), 5, 5, 0x404040, false);
            for (int row = 0; row < 7 && first + row < count(); row++) {
                String name; ItemStack item;
                if (menu.isInfo()) { var e = filtered.get(first + row); item = e.stack(); name = item.getHoverName().getString(); }
                else { name = menu.moduleName(first + row); String id = FlawlessCalculatorItem.MODULE_ITEMS.get(name); item = id == null ? ItemStack.EMPTY : icon(id); }
                g.renderItem(item, 5, 31 + row * 18);
                g.drawString(font, font.plainSubstrByWidth(name.equals("Empty") ? Component.translatable("gui.calculator.empty").getString() : name, 92), 26, 35 + row * 18, 0xffffff, false);
            }
        } else {
            g.renderItem(detail.stack(), 5, 5);
            g.pose().pushPose(); g.pose().scale(.8F, .8F, 1);
            g.drawString(font, font.plainSubstrByWidth(detail.stack().getHoverName().getString(), 125), 35, 12, 0x404040, false);
            if (!pages.isEmpty()) for (int i = 0; i < pages.get(page).size(); i++) g.drawString(font, pages.get(page).get(i), 6, 28 + i * 11, 0x404040, false);
            g.pose().popPose();
            g.drawCenteredString(font, (page + 1) + " / " + pages.size(), 68, 130, 0x404040);
        }
    }
    @Override public void render(GuiGraphics g, int x, int y, float tick) {
        renderBackground(g); super.render(g, x, y, tick);
        if (menu.isInfo()) {
            int size = detail == null ? CATEGORY_ICONS.length - 1 : detail.related().size();
            for (int i = 0; i < size; i++) {
                ItemStack item = icon(detail == null ? CATEGORY_ICONS[i + 1] : detail.related().get(i));
                g.renderItem(item, leftPos + 137, topPos + 3 + i * 18);
                if (x >= leftPos + 136 && x < leftPos + 154 && y >= topPos + 2 + i * 18 && y < topPos + 20 + i * 18) {
                    if (detail == null) g.renderTooltip(font, Component.translatable(InfoCatalog.Category.values()[i + 1].translationKey()), x, y);
                    else if (!item.isEmpty()) g.renderTooltip(font, item, x, y);
                    else g.renderTooltip(font, Component.translatable("gui.calculator.unavailable").append(": ").append(detail.related().get(i)), x, y);
                }
            }
        }
        renderTooltip(g, x, y);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && detail == null) {
            if (x >= leftPos + 124 && x < leftPos + 134 && y >= topPos + 30 && y < topPos + 158) { dragging = true; scrollTo(y); return true; }
            if (x >= leftPos + 4 && x < leftPos + 124 && y >= topPos + 30 && y < topPos + 156) {
                int slot = first + (int)(y - topPos - 30) / 18;
                if (slot < count()) {
                    if (menu.isInfo()) show(filtered.get(slot));
                    else if (!menu.moduleName(slot).equals("Empty") && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, slot);
                    return true;
                }
            }
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging) { scrollTo(y); return true; } return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) { dragging = false; return super.mouseReleased(x, y, button); }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (detail == null) { first = Math.max(0, Math.min(maxFirst(), first - (int)Math.signum(delta))); return true; }
        return super.mouseScrolled(x, y, delta);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (menu.isInfo() && key == 256) {
            if (detail != null) {
                if (!history.isEmpty()) show(history.remove(history.size() - 1));
                else { detail = null; rebuildWidgets(); }
                return true;
            }
            if (category != InfoCatalog.Category.All) { category = InfoCatalog.Category.All; filter(); return true; }
        }
        if (search != null && detail == null && search.isFocused() && key != 256) return search.keyPressed(key, scan, modifiers);
        return super.keyPressed(key, scan, modifiers);
    }
}
