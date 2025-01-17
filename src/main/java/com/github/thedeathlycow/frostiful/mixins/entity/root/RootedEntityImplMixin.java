package com.github.thedeathlycow.frostiful.mixins.entity.root;

import com.github.thedeathlycow.frostiful.enchantment.FEnchantmentHelper;
import com.github.thedeathlycow.frostiful.enchantment.IceBreakerEnchantment;
import com.github.thedeathlycow.frostiful.entity.RootedEntity;
import com.github.thedeathlycow.frostiful.entity.damage.FDamageTypes;
import com.github.thedeathlycow.frostiful.tag.FEntityTypeTags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class RootedEntityImplMixin extends Entity implements RootedEntity {

    @Shadow
    public abstract boolean damage(DamageSource source, float amount);

    private static final TrackedData<Integer> FROZEN_TICKS = DataTracker.registerData(RootedEntityImplMixin.class, TrackedDataHandlerRegistry.INTEGER);

    public RootedEntityImplMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(
            method = "initDataTracker",
            at = @At("TAIL")
    )
    private void trackFrostData(CallbackInfo ci) {
        this.dataTracker.startTracking(FROZEN_TICKS, 0);
    }

    @Override
    public int frostiful$getRootedTicks() {
        return this.dataTracker.get(FROZEN_TICKS);
    }

    @Override
    public void frostiful$setRootedTicks(int amount) {
        this.dataTracker.set(FROZEN_TICKS, amount);
    }

    @Override
    public boolean frostiful$canRoot(@Nullable Entity attacker) {
        final LivingEntity instance = (LivingEntity) (Object) this;

        if (instance.getType().isIn(FEntityTypeTags.ROOT_IMMUNE)) {
            return false;
        }

        if (attacker != null && this.isTeammate(attacker)) {
            return false;
        }

        return instance.thermoo$canFreeze();
    }

    @Inject(
            method = "damage",
            at = @At("RETURN")
    )
    private void shatterIceOnDamaged(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || source.isOf(FDamageTypes.BROKEN_ICE) || !this.frostiful$isRooted()) {
            return;
        }

        LivingEntity attacker = source.getAttacker() instanceof LivingEntity user ? user : null;
        int level = attacker != null ? FEnchantmentHelper.getIceBreakerLevel(attacker) : 0;
        IceBreakerEnchantment.applyIceBreakDamage(attacker, this, level);
        RootedEntity.breakRootOnEntity((LivingEntity) (Object) this);
    }

    @Inject(
            method = "tickMovement",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiler/Profiler;pop()V",
                    ordinal = 1
            ),
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/entity/LivingEntity;setVelocity(DDD)V"
                    )
            )
    )
    private void tickRoot(CallbackInfo ci) {
        World world = getWorld();

        if (this.isSpectator()) {
            this.frostiful$setRootedTicks(0);
            return;
        }
        Profiler profiler = world.getProfiler();
        profiler.push("frostiful.rooted_tick");
        if (this.frostiful$isRooted()) {
            // remove tick
            int ticksLeft = this.frostiful$getRootedTicks();
            this.frostiful$setRootedTicks(--ticksLeft);

            if (!world.isClient && this.isOnFire()) {
                this.frostiful$breakRoot();
                this.extinguish();
                ServerWorld serverWorld = (ServerWorld) world;
                serverWorld.spawnParticles(
                        ParticleTypes.POOF,
                        this.getX(), this.getY(), this.getZ(),
                        100,
                        0.5, 0.5, 0.5,
                        0.1f
                );
                this.playExtinguishSound();
            }
        }
        profiler.pop();
    }

    @Inject(
            method = "writeCustomDataToNbt",
            at = @At("TAIL")
    )
    private void writeRootedTicksToNbt(NbtCompound nbt, CallbackInfo ci) {
        RootedEntity.frostiful$addRootedTicksToNbt(this, nbt);
    }

    @Inject(
            method = "readCustomDataFromNbt",
            at = @At("TAIL")
    )
    private void readRootedTicksFromNbt(NbtCompound nbt, CallbackInfo ci) {
        RootedEntity.frostiful$setRootedTicksFromNbt(this, nbt);
    }
}


