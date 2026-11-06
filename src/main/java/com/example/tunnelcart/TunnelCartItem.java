package com.example.tunnelcart;

import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

import java.util.List;

/** One item class for both carts: extension == false -> tunnel cart, true -> seat/storage extension. */
public class TunnelCartItem extends Item {
    private final boolean extension;

    public TunnelCartItem(Settings settings, boolean extension) { super(settings); this.extension = extension; }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (!state.isIn(BlockTags.RAILS) || !(state.getBlock() instanceof AbstractRailBlock rail)) return ActionResult.FAIL;
        RailShape shape = state.get(rail.getShapeProperty());
        PlayerEntity player = ctx.getPlayer();
        double x = pos.getX() + 0.5, y = pos.getY() + 0.0625, z = pos.getZ() + 0.5;

        if (extension) {
            List<TunnelCartEntity> found = world.getEntitiesByClass(TunnelCartEntity.class,
                    new Box(pos).expand(16), t -> t.getExtUuid() == null);
            if (found.isEmpty()) {
                if (player != null) player.sendMessage(Text.translatable("tunnelcart.no_leader"), true);
                return ActionResult.FAIL;
            }
            if (!world.isClient) {
                TunnelCartEntity leader = found.get(0);
                ExtensionCartEntity ext = new ExtensionCartEntity(world, x, y, z);
                ext.setLeader(leader.getUuid());
                leader.setExtUuid(ext.getUuid());
                world.spawnEntity(ext);
            }
        } else {
            Direction facing = ctx.getHorizontalPlayerFacing();
            Direction heading;
            if (shape == RailShape.NORTH_SOUTH) heading = facing.getAxis() == Direction.Axis.Z ? facing : Direction.SOUTH;
            else if (shape == RailShape.EAST_WEST) heading = facing.getAxis() == Direction.Axis.X ? facing : Direction.EAST;
            else return ActionResult.FAIL;
            if (!world.isClient) {
                TunnelCartEntity cart = new TunnelCartEntity(world, x, y, z);
                cart.setup(pos, heading);
                world.spawnEntity(cart);
            }
        }
        if (!world.isClient) world.emitGameEvent(player, GameEvent.ENTITY_PLACE, pos);
        ctx.getStack().decrement(1);
        return ActionResult.success(world.isClient);
    }
}
