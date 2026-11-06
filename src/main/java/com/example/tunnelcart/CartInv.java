package com.example.tunnelcart;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.collection.DefaultedList;

/** Inventory helpers that also look inside / fill shulker boxes. */
public final class CartInv {
    private CartInv() {}

    public static boolean isShulker(ItemStack s) {
        return s.getItem() instanceof BlockItem b && b.getBlock() instanceof ShulkerBoxBlock;
    }

    /** Take one item: plain slots first, then from inside shulker boxes. */
    public static boolean take(Inventory inv, Item item) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack t = inv.getStack(i);
            if (t.isOf(item)) { t.decrement(1); return true; }
        }
        for (int i = 0; i < inv.size(); i++) {
            ItemStack box = inv.getStack(i);
            if (!isShulker(box)) continue;
            NbtCompound tag = box.getSubNbt("BlockEntityTag");
            if (tag == null || !tag.contains("Items", 9)) continue;
            DefaultedList<ItemStack> l = DefaultedList.ofSize(27, ItemStack.EMPTY);
            Inventories.readNbt(tag, l);
            for (ItemStack t : l) {
                if (t.isOf(item)) { t.decrement(1); Inventories.writeNbt(tag, l, true); return true; }
            }
        }
        return false;
    }

    /** Insert: merge into stacks, then empty slots, then shulker boxes. Returns the leftover. */
    public static ItemStack insert(Inventory inv, ItemStack s) {
        for (int i = 0; i < inv.size() && !s.isEmpty(); i++) merge(inv.getStack(i), s);
        for (int i = 0; i < inv.size() && !s.isEmpty(); i++) {
            if (inv.getStack(i).isEmpty()) { inv.setStack(i, s.copy()); s.setCount(0); }
        }
        if (isShulker(s)) return s;
        for (int i = 0; i < inv.size() && !s.isEmpty(); i++) {
            ItemStack box = inv.getStack(i);
            if (!isShulker(box)) continue;
            NbtCompound tag = box.getOrCreateSubNbt("BlockEntityTag");
            DefaultedList<ItemStack> l = DefaultedList.ofSize(27, ItemStack.EMPTY);
            Inventories.readNbt(tag, l);
            for (ItemStack t : l) merge(t, s);
            for (int j = 0; j < 27 && !s.isEmpty(); j++) {
                if (l.get(j).isEmpty()) { l.set(j, s.copy()); s.setCount(0); }
            }
            Inventories.writeNbt(tag, l, true);
        }
        return s;
    }

    private static void merge(ItemStack t, ItemStack s) {
        if (!t.isEmpty() && !s.isEmpty() && ItemStack.canCombine(t, s)) {
            int m = Math.min(s.getCount(), t.getMaxCount() - t.getCount());
            if (m > 0) { t.increment(m); s.decrement(m); }
        }
    }
}
