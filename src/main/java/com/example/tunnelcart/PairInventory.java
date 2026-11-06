package com.example.tunnelcart;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

public class PairInventory implements Inventory {
    private final Inventory a, b;
    public PairInventory(Inventory a, Inventory b) { this.a = a; this.b = b; }
    @Override public int size() { return a.size() + b.size(); }
    @Override public boolean isEmpty() { return a.isEmpty() && b.isEmpty(); }
    @Override public ItemStack getStack(int i) { return i < a.size() ? a.getStack(i) : b.getStack(i - a.size()); }
    @Override public ItemStack removeStack(int i, int n) { return i < a.size() ? a.removeStack(i, n) : b.removeStack(i - a.size(), n); }
    @Override public ItemStack removeStack(int i) { return i < a.size() ? a.removeStack(i) : b.removeStack(i - a.size()); }
    @Override public void setStack(int i, ItemStack s) { if (i < a.size()) a.setStack(i, s); else b.setStack(i - a.size(), s); }
    @Override public void markDirty() { a.markDirty(); b.markDirty(); }
    @Override public boolean canPlayerUse(PlayerEntity p) { return a.canPlayerUse(p); }
    @Override public void clear() { a.clear(); b.clear(); }
}
