package org.crafterscr.craftersstorm.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface DeathLootInvoker {
    @Invoker("dropAllDeathLoot")
    void craftersstorm$dropDeathLoot(ServerLevel level, DamageSource source);
}
