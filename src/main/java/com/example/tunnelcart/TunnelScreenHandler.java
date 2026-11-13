package com.example.tunnelcart;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public class TunnelScreenHandler extends ScreenHandler {
    private final Inventory inv;
    private final int rows;
    private final TunnelCartEntity cart;
    private final PropertyDelegate props;
    private final int cartId;

    /** client */
    public TunnelScreenHandler(int id, PlayerInventory pi, PacketByteBuf buf) {
        this(id, pi, new SimpleInventory(buf.readInt() * 9), null, buf.readInt());
    }

    /** server (cart == null on the client) */
    public TunnelScreenHandler(int id, PlayerInventory pi, Inventory inv, TunnelCartEntity cart) {
        this(id, pi, inv, cart, cart == null ? -1 : cart.getId());
    }

    private TunnelScreenHandler(int id, PlayerInventory pi, Inventory inv, TunnelCartEntity cart, int cartId) {
        super(TunnelCartMod.TUNNEL_HANDLER, id);
        this.inv = inv; this.cart = cart; this.rows = inv.size() / 9; this.cartId = cartId;
        this.props = cart == null ? new ArrayPropertyDelegate(2) : new PropertyDelegate() {
            @Override public int get(int i) { return i == 0 ? (cart.isRunning() ? 1 : 0) : (cart.isTorches() ? 1 : 0); }
            @Override public void set(int i, int v) {}
            @Override public int size() { return 2; }
        };
        inv.onOpen(pi.player);
        int yo = (rows - 4) * 18;
        for (int r = 0; r < rows; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9, 8 + c * 18, 18 + r * 18));
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(pi, c + r * 9 + 9, 8 + c * 18, 103 + r * 18 + yo));
        for (int c = 0; c < 9; c++) addSlot(new Slot(pi, c, 8 + c * 18, 161 + yo));
        addProperties(props);
    }

    public int getRows() { return rows; }
    public boolean isRunning() { return props.get(0) == 1; }
    public boolean isTorches() { return props.get(1) == 1; }
    public int getCartId() { return cartId; }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (cart != null && id == 0) { cart.toggleRunning(); return true; }
        if (cart != null && id == 1) { cart.toggleTorches(); return true; }
        return false;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int idx) {
        ItemStack ret = ItemStack.EMPTY;
        Slot slot = slots.get(idx);
        if (slot != null && slot.hasStack()) {
            ItemStack st = slot.getStack();
            ret = st.copy();
            if (idx < rows * 9) { if (!insertItem(st, rows * 9, slots.size(), true)) return ItemStack.EMPTY; }
            else if (!insertItem(st, 0, rows * 9, false)) return ItemStack.EMPTY;
            if (st.isEmpty()) slot.setStack(ItemStack.EMPTY); else slot.markDirty();
        }
        return ret;
    }

    @Override public boolean canUse(PlayerEntity p) { return inv.canPlayerUse(p); }
    @Override public void onClosed(PlayerEntity p) { super.onClosed(p); inv.onClose(p); }
}
