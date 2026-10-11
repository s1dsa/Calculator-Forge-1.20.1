package sonar.calculator.mod.item.entity;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkHooks;
import sonar.calculator.mod.CalculatorContent;
import sonar.calculator.mod.item.ItemFactory;

/** The four original impact projectiles, using the vanilla tracked item renderer. */
public class CalculatorProjectile extends ThrowableItemProjectile {
    public CalculatorProjectile(EntityType<? extends CalculatorProjectile> type, Level level) { super(type, level); }
    public CalculatorProjectile(EntityType<? extends CalculatorProjectile> type, LivingEntity owner, Level level) { super(type, owner, level); }

    @Override protected Item getDefaultItem() {
        if (getType() == ItemFactory.SOIL.get()) return CalculatorContent.item("soil");
        if (getType() == ItemFactory.BABY_GRENADE.get()) return CalculatorContent.item("babygrenade");
        if (getType() == ItemFactory.GRENADE.get()) return CalculatorContent.item("grenade");
        return CalculatorContent.item("smallstone");
    }

    @Override protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (level().isClientSide) return;
        if (getType() == ItemFactory.GRENADE.get() || getType() == ItemFactory.BABY_GRENADE.get()) {
            level().explode(this, getX(), getY(), getZ(), getType() == ItemFactory.GRENADE.get() ? 5F : 1F, Level.ExplosionInteraction.TNT);
        } else if (hit instanceof EntityHitResult entityHit) {
            if (getType() == ItemFactory.SOIL.get() && entityHit.getEntity() instanceof Player player) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 1));
            } else if (getType() == ItemFactory.STONE.get()) {
                entityHit.getEntity().hurt(damageSources().thrown(this, getOwner()), 4F);
            }
        }
        discard();
    }

    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
