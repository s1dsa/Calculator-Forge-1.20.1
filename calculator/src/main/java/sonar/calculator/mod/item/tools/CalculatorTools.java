package sonar.calculator.mod.item.tools;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import sonar.calculator.mod.CalculatorContent;

/** Original tool values and attack speeds, with live config-backed material properties. */
public final class CalculatorTools {
    private CalculatorTools() {}
    private record Material(String name, String repair) implements Tier {
        ToolConfig.Stats stats() { return ToolConfig.get(name); }
        @Override public int getUses() { return Math.max(0, stats().uses()); }
        @Override public float getSpeed() { return stats().speed(); }
        @Override public float getAttackDamageBonus() { return stats().damage(); }
        @Override public int getLevel() { return stats().level(); }
        @Override public int getEnchantmentValue() { return stats().enchantment(); }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
        boolean damageable() { return stats().uses() > 0; }
    }
    private static final Map<String, Material> MATERIALS = Map.of(
            "reinforced", new Material("reinforced", "material"),
            "enrichedgold", new Material("enrichedgold", "enrichedgoldingot"),
            "reinforcediron", new Material("reinforcediron", "reinforcedironingot"),
            "redstone", new Material("redstone", "redstoneingot"),
            "weakeneddiamond", new Material("weakeneddiamond", "weakeneddiamond"),
            "flawlessdiamond", new Material("flawlessdiamond", "flawlessdiamond"),
            "firediamond", new Material("firediamond", "firediamond"),
            "electric", new Material("electric", "electricdiamond"),
            "endforged", new Material("endforged", "enddiamond"));

    public static Item create(String id) {
        for (var entry : MATERIALS.entrySet()) {
            if (!id.startsWith(entry.getKey())) continue;
            String kind = id.substring(entry.getKey().length());
            if (!java.util.Set.of("sword", "pickaxe", "axe", "shovel", "hoe").contains(kind)) continue;
            Material material = entry.getValue();
            return switch (kind) {
                case "sword" -> new Sword(material);
                case "pickaxe" -> new Pickaxe(material);
                case "axe" -> new Axe(material);
                case "shovel" -> new Shovel(material);
                case "hoe" -> new Hoe(material);
                default -> null;
            };
        }
        return null;
    }

    private static Multimap<Attribute, AttributeModifier> attributes(EquipmentSlot slot, double damage, double speed) {
        if (slot != EquipmentSlot.MAINHAND) return ImmutableMultimap.of();
        return ImmutableMultimap.of(
                Attributes.ATTACK_DAMAGE, new AttributeModifier(java.util.UUID.fromString("cb3f55d3-645c-4f38-a497-9c13a33db5cf"), "Tool modifier", damage, AttributeModifier.Operation.ADDITION),
                Attributes.ATTACK_SPEED, new AttributeModifier(java.util.UUID.fromString("fa233e1c-4180-4865-b01b-bcce9785aca3"), "Tool modifier", speed, AttributeModifier.Operation.ADDITION));
    }
    private static Item.Properties properties() { return new Item.Properties().stacksTo(1); }

    private static final class Sword extends SwordItem {
        private final Material material;
        Sword(Material material) { super(material, 3, -2.4F, properties()); this.material = material; }
        @Override public int getMaxDamage(ItemStack stack) { return material.getUses(); }
        @Override public boolean isDamageable(ItemStack stack) { return material.damageable(); }
        @Override public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) { return attributes(slot, 3 + material.getAttackDamageBonus(), -2.4F); }
        @Override public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
            if (material.name.equals("firediamond") && !player.level().isClientSide) entity.setSecondsOnFire(4);
            return false;
        }
    }
    private static final class Pickaxe extends PickaxeItem {
        private final Material material;
        Pickaxe(Material material) { super(material, 1, -2.8F, properties()); this.material = material; }
        @Override public int getMaxDamage(ItemStack stack) { return material.getUses(); }
        @Override public boolean isDamageable(ItemStack stack) { return material.damageable(); }
        @Override public float getDestroySpeed(ItemStack stack, BlockState state) { return state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? material.getSpeed() : super.getDestroySpeed(stack, state); }
        @Override public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) { return attributes(slot, 1 + material.getAttackDamageBonus(), -2.8F); }
    }
    private static final class Axe extends AxeItem {
        private final Material material;
        Axe(Material material) { super(material, 0F, -3.5F, properties()); this.material = material; }
        @Override public int getMaxDamage(ItemStack stack) { return material.getUses(); }
        @Override public boolean isDamageable(ItemStack stack) { return material.damageable(); }
        @Override public float getDestroySpeed(ItemStack stack, BlockState state) { return state.is(BlockTags.MINEABLE_WITH_AXE) ? material.getSpeed() : super.getDestroySpeed(stack, state); }
        @Override public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) { return attributes(slot, material.getAttackDamageBonus(), -3.5F); }
    }
    private static final class Shovel extends ShovelItem {
        private final Material material;
        Shovel(Material material) { super(material, 1.5F, -3F, properties()); this.material = material; }
        @Override public int getMaxDamage(ItemStack stack) { return material.getUses(); }
        @Override public boolean isDamageable(ItemStack stack) { return material.damageable(); }
        @Override public float getDestroySpeed(ItemStack stack, BlockState state) { return state.is(BlockTags.MINEABLE_WITH_SHOVEL) ? material.getSpeed() : super.getDestroySpeed(stack, state); }
        @Override public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) { return attributes(slot, 1.5F + material.getAttackDamageBonus(), -3F); }
    }
    private static final class Hoe extends HoeItem {
        private final Material material;
        Hoe(Material material) { super(material, 0, material.getAttackDamageBonus() - 3F, properties()); this.material = material; }
        @Override public int getMaxDamage(ItemStack stack) { return material.getUses(); }
        @Override public boolean isDamageable(ItemStack stack) { return material.damageable(); }
        @Override public float getDestroySpeed(ItemStack stack, BlockState state) { return state.is(BlockTags.MINEABLE_WITH_HOE) ? material.getSpeed() : super.getDestroySpeed(stack, state); }
        @Override public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) { return attributes(slot, 0, material.getAttackDamageBonus() - 3F); }
    }
}
