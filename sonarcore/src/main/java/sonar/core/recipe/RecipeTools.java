package sonar.core.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.items.IItemHandler;

/** Deterministic, count-aware helpers for data-driven machine recipes. */
public final class RecipeTools {
    private RecipeTools() { }

    /** Matches one unit per ingredient, allowing several ingredients to share a sufficiently large stack. */
    public static Optional<int[]> matchShapeless(List<Ingredient> ingredients, IItemHandler inventory, int... inputSlots) {
        int[] slots = checkedSlots(inventory, inputSlots);
        if (ingredients.size() > 256) throw new IllegalArgumentException("At most 256 recipe ingredients");
        int[] available = new int[slots.length];
        List<int[]> candidates = new ArrayList<>();
        for (int i = 0; i < slots.length; i++) {
            ItemStack stored = inventory.getStackInSlot(slots[i]);
            available[i] = stored.isEmpty() ? 0 : inventory.extractItem(slots[i], stored.getCount(), true).getCount();
        }
        for (Ingredient ingredient : ingredients) {
            int[] eligible = new int[slots.length];
            int count = 0;
            for (int i = 0; i < slots.length; i++)
                if (available[i] > 0 && ingredient.test(inventory.getStackInSlot(slots[i]))) eligible[count++] = i;
            if (count == 0) return Optional.empty();
            candidates.add(Arrays.copyOf(eligible, count));
        }
        // Capacitated bipartite matching with reassignment: overlapping tags do not cause greedy false negatives.
        Integer[] order = new Integer[ingredients.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingInt(i -> candidates.get(i).length));
        List<List<Integer>> assigned = new ArrayList<>();
        for (int i = 0; i < slots.length; i++) assigned.add(new ArrayList<>());
        int[] result = new int[ingredients.size()];
        Arrays.fill(result, -1);
        for (int ingredient : order) {
            if (!assign(ingredient, candidates, assigned, available, result, new boolean[slots.length])) return Optional.empty();
        }
        for (int i = 0; i < result.length; i++) result[i] = slots[result[i]];
        return Optional.of(result);
    }

    private static boolean assign(int ingredient, List<int[]> candidates, List<List<Integer>> assigned,
                                  int[] capacities, int[] result, boolean[] visited) {
        for (int slot : candidates.get(ingredient)) {
            if (visited[slot]) continue;
            visited[slot] = true;
            List<Integer> occupants = assigned.get(slot);
            if (occupants.size() < capacities[slot]) {
                occupants.add(ingredient);
                result[ingredient] = slot;
                return true;
            }
            for (int i = 0; i < occupants.size(); i++) {
                if (assign(occupants.get(i), candidates, assigned, capacities, result, visited)) {
                    occupants.set(i, ingredient);
                    result[ingredient] = slot;
                    return true;
                }
            }
        }
        return false;
    }

    /** Preflight all counts before extracting. Execute immediately after matching on the server thread. */
    public static boolean consumeMatched(IItemHandler inventory, int[] matchedSlots) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (int slot : matchedSlots) {
            if (slot < 0 || slot >= inventory.getSlots()) return false;
            counts.merge(slot, 1, Integer::sum);
        }
        for (var entry : counts.entrySet())
            if (inventory.extractItem(entry.getKey(), entry.getValue(), true).getCount() != entry.getValue()) return false;
        for (var entry : counts.entrySet()) {
            ItemStack extracted = inventory.extractItem(entry.getKey(), entry.getValue(), false);
            if (extracted.getCount() != entry.getValue())
                throw new IllegalStateException("Item handler changed between simulation and execution");
        }
        return true;
    }

    /** Reserves virtual space across the entire output list, including duplicate output types. */
    public static boolean canInsertAll(IItemHandler inventory, List<ItemStack> outputs, int... outputSlots) {
        int[] slots = checkedSlots(inventory, outputSlots);
        ItemStack[] virtual = new ItemStack[slots.length];
        int[] reserved = new int[slots.length];
        for (int i = 0; i < slots.length; i++) virtual[i] = inventory.getStackInSlot(slots[i]).copy();
        for (ItemStack output : outputs) {
            if (output.isEmpty()) continue;
            int remaining = output.getCount();
            for (int pass = 0; pass < 2 && remaining > 0; pass++) for (int i = 0; i < slots.length && remaining > 0; i++) {
                ItemStack current = virtual[i];
                if ((pass == 0) == current.isEmpty() || !inventory.isItemValid(slots[i], output)) continue;
                if (!current.isEmpty() && !ItemStack.isSameItemSameTags(current, output)) continue;
                int limit = Math.min(inventory.getSlotLimit(slots[i]), output.getMaxStackSize());
                int space = Math.max(0, limit - current.getCount());
                ItemStack probe = output.copyWithCount(Math.min(limit, remaining + reserved[i]));
                int accepts = probe.getCount() - inventory.insertItem(slots[i], probe, true).getCount();
                int moved = Math.min(remaining, Math.min(space, Math.max(0, accepts - reserved[i])));
                if (moved == 0) continue;
                if (current.isEmpty()) virtual[i] = output.copyWithCount(moved);
                else current.grow(moved);
                reserved[i] += moved;
                remaining -= moved;
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    /** Inserts in slot order and returns every uninserted remainder; never discards a stack. */
    public static List<ItemStack> insertAll(IItemHandler inventory, List<ItemStack> outputs, int... outputSlots) {
        int[] slots = checkedSlots(inventory, outputSlots);
        List<ItemStack> remainders = new ArrayList<>();
        for (ItemStack output : outputs) {
            ItemStack remaining = output.copy();
            for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) for (int slot : slots) {
                if (remaining.isEmpty()) break;
                if ((pass == 0) == inventory.getStackInSlot(slot).isEmpty()) continue;
                remaining = inventory.insertItem(slot, remaining, false);
            }
            if (!remaining.isEmpty()) remainders.add(remaining);
        }
        return remainders;
    }

    private static int[] checkedSlots(IItemHandler inventory, int[] requested) {
        int[] slots = requested;
        if (slots.length == 0) {
            slots = new int[inventory.getSlots()];
            for (int i = 0; i < slots.length; i++) slots[i] = i;
        }
        boolean[] seen = new boolean[inventory.getSlots()];
        for (int slot : slots) {
            if (slot < 0 || slot >= seen.length || seen[slot]) throw new IllegalArgumentException("Invalid or duplicate slot " + slot);
            seen[slot] = true;
        }
        return slots;
    }
}
