package com.github.thedeathlycow.frostiful.entity;

import com.github.thedeathlycow.frostiful.attributes.FEntityAttributes;
import com.github.thedeathlycow.frostiful.init.Frostiful;
import com.github.thedeathlycow.frostiful.item.FItems;
import com.github.thedeathlycow.frostiful.item.FrostWandItem;
import com.github.thedeathlycow.frostiful.sound.FSoundEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * By remapping {@link SpellcastingIllagerEntity.Spell}s, the Frostologer has the following spells:
 * <p>
 * SUMMON_VEX = SUMMON_MINIONS
 * DISAPPEAR = DESTROY_HEAT_SOURCES
 */
public class FrostologerEntity extends SpellcastingIllagerEntity implements RangedAttackMob {

    private static final TrackedData<Boolean> IS_USING_FROST_WAND = DataTracker.registerData(
            FrostologerEntity.class, TrackedDataHandlerRegistry.BOOLEAN
    );

    private static final float POWER_PARTICLES_FREEZING_SCALE_START = 0.9f;
    private static final int NUM_POWER_PARTICLES = 3;


    protected FrostologerEntity(EntityType<? extends FrostologerEntity> entityType, World world) {
        super(entityType, world);
        this.experiencePoints = 20;
    }

    public static DefaultAttributeContainer.Builder createFrostologerAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 12.0)
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 24.0)
                .add(FEntityAttributes.MAX_FROST, 45.0)
                .add(FEntityAttributes.FROST_RESISTANCE, -5.0);
    }

    public static boolean isHeatSource(BlockState state) {
        int minLightForWarmth = Frostiful.getConfig().freezingConfig.getMinLightForWarmth();
        return state.getLuminance() >= minLightForWarmth;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        return damageSource == DamageSource.FREEZE || super.isInvulnerableTo(damageSource);
    }

    protected void initGoals() {
        super.initGoals();
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new SpellcastingIllagerEntity.LookAtTargetGoal());

        this.goalSelector.add(2, new FrostWandCastGoal(this, 1.0, 40, 10f));

        this.goalSelector.add(2, new FleeEntityGoal<>(this, PlayerEntity.class, 8.0F, 1.2, 1.5));
        this.goalSelector.add(2, new FleeEntityGoal<>(this, IronGolemEntity.class, 8.0F, 1.2, 1.5));

        // this.goalSelector.add(3, new DestroyHeatSourcesGoal());

        this.goalSelector.add(4, new SummonMinionsGoal());
        this.goalSelector.add(4, new FrostWandAttackGoal());

        this.goalSelector.add(8, new WanderAroundGoal(this, 0.6));
        this.goalSelector.add(9, new LookAtEntityGoal(this, PlayerEntity.class, 3.0F, 1.0F));
        this.goalSelector.add(10, new LookAtEntityGoal(this, MobEntity.class, 8.0F));

        this.targetSelector.add(
                1,
                new RevengeGoal(this, RaiderEntity.class)
                        .setGroupRevenge()
        );
        this.targetSelector.add(
                2,
                new ActiveTargetGoal<>(this, PlayerEntity.class, true)
                        .setMaxTimeWithoutVisibility(300)
        );
        this.targetSelector.add(
                3,
                new ActiveTargetGoal<>(this, MerchantEntity.class, false)
                        .setMaxTimeWithoutVisibility(300)
        );
        this.targetSelector.add(
                3,
                new ActiveTargetGoal<>(this, IronGolemEntity.class, false)
        );
    }

    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData, @Nullable NbtCompound entityNbt) {
        this.initEquipment(world.getRandom(), difficulty);
        return super.initialize(world, difficulty, spawnReason, entityData, entityNbt);
    }

    @Override
    protected void initEquipment(Random random, LocalDifficulty difficulty) {
        this.setStackInHand(Hand.MAIN_HAND, new ItemStack(FItems.FROST_WAND));
        this.enchantMainHandItem(random, difficulty.getClampedLocalDifficulty());
    }

    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(IS_USING_FROST_WAND, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.world.isClient && this.getFreezingScale() >= POWER_PARTICLES_FREEZING_SCALE_START) {
            this.spawnPowerParticles();
        }
    }

    @Environment(EnvType.CLIENT)
    private void spawnPowerParticles() {

        ThreadLocalRandom random = ThreadLocalRandom.current();

        Box box = this.getBoundingBox();

        for (int i = 0; i < NUM_POWER_PARTICLES; i++) {
            // pick random pos in bounding box
            double x = box.getMin(Direction.Axis.X) + random.nextDouble(box.getXLength());
            double y = box.getMin(Direction.Axis.Y) + random.nextDouble(box.getYLength());
            double z = box.getMin(Direction.Axis.Z) + random.nextDouble(box.getZLength());

            world.addParticle(
                    ParticleTypes.SNOWFLAKE,
                    x, y, z,
                    0, 0.06f, 0
            );
        }
    }

    @Override
    public IllagerEntity.State getState() {
        if (this.isSpellcasting()) {
            return State.SPELLCASTING;
        } else {
            return this.isCelebrating() ? State.CELEBRATING : State.NEUTRAL;
        }
    }

    @Override
    public void attack(LivingEntity target, float pullProgress) {
        if (this.activeItemStack.isOf(FItems.FROST_WAND)) {
            this.getLookControl().lookAt(target);
            FrostWandItem.fireFrostSpell(this.activeItemStack.copy(), this.world, this);
        }
    }

    public boolean hasTarget() {
        LivingEntity target = this.getTarget();
        return target != null && target.isAlive();
    }

    public boolean isTargetRooted() {
        LivingEntity target = this.getTarget();
        return target != null
                && ((RootedEntity) target).frostiful$isRooted();
    }

    public boolean isUsingFrostWand() {
        return this.dataTracker.get(IS_USING_FROST_WAND);
    }

    private void startUsingFrostWand() {
        Vec3d pos = this.getPos();
        this.world.playSound(null,
                pos.x, pos.y, pos.z,
                FSoundEvents.ITEM_FROST_WAND_PREPARE_CAST,
                SoundCategory.HOSTILE,
                1.0f, 1.0f
        );
        this.dataTracker.set(IS_USING_FROST_WAND, true);
    }

    private void stopUsingFrostWand() {
        this.dataTracker.set(IS_USING_FROST_WAND, false);
    }

    @Override
    public void addBonusForWave(int wave, boolean unused) {

    }

    @Override
    public boolean isTeammate(@Nullable Entity other) {
        if (other == null) {
            return false;
        } else if (other == this) {
            return true;
        } else if (super.isTeammate(other)) {
            return true;
        } else if (other.getType() == EntityType.VEX) {
            return this.isTeammate(((VexEntity) other).getOwner());
        } else if (other instanceof LivingEntity && ((LivingEntity) other).getGroup() == EntityGroup.ILLAGER) {
            return this.getScoreboardTeam() == null && other.getScoreboardTeam() == null;
        } else {
            return false;
        }
    }

    @Override
    public SoundEvent getCelebratingSound() {
        return SoundEvents.ENTITY_PILLAGER_CELEBRATE;
    }

    @Override
    protected SoundEvent getCastSpellSound() {
        return SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL;
    }

    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(IS_USING_FROST_WAND, nbt.getBoolean("IsUsingFrostWand"));
    }

    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("IsUsingFrostWand", this.dataTracker.get(IS_USING_FROST_WAND));
    }

    protected class FrostWandAttackGoal extends AttackGoal {
        public FrostWandAttackGoal() {
            super(FrostologerEntity.this);
        }

        @Override
        public boolean canStart() {
            return FrostologerEntity.this.isTargetRooted()
                    && super.canStart();
        }

    }

    protected class FrostWandCastGoal extends ProjectileAttackGoal {

        public FrostWandCastGoal(RangedAttackMob mob, double mobSpeed, int intervalTicks, float maxShootRange) {
            super(mob, mobSpeed, intervalTicks, maxShootRange);
        }

        public boolean canStart() {
            return super.canStart()
                    && FrostologerEntity.this.hasTarget()
                    && !FrostologerEntity.this.isTargetRooted()
                    && FrostologerEntity.this.getMainHandStack().isOf(FItems.FROST_WAND);
        }

        public void start() {
            super.start();
            FrostologerEntity.this.setAttacking(true);
            FrostologerEntity.this.setCurrentHand(Hand.MAIN_HAND);
            FrostologerEntity.this.startUsingFrostWand();
        }

        public void stop() {
            super.stop();
            FrostologerEntity.this.setAttacking(false);
            FrostologerEntity.this.clearActiveItem();
            FrostologerEntity.this.stopUsingFrostWand();
        }
    }

    protected class DestroyHeatSourcesGoal extends SpellcastingIllagerEntity.CastSpellGoal {
        @Override
        protected void castSpell() {

            BlockPos origin = FrostologerEntity.this.getBlockPos();
            Vec3i distance = new Vec3i(10, 10, 10);
            for (BlockPos pos : BlockPos.iterate(origin.subtract(distance), origin.add(distance))) {
                BlockState state = FrostologerEntity.this.world.getBlockState(pos);
                if (FrostologerEntity.isHeatSource(state)) {
                    FrostologerEntity.this.world.setBlockState(pos, Blocks.AIR.getDefaultState());
                    FrostologerEntity.this.world.playSound(
                            null,
                            pos,
                            SoundEvents.BLOCK_FIRE_EXTINGUISH,
                            SoundCategory.HOSTILE,
                            0, 0
                    );
                }
            }

        }

        @Override
        protected int getSpellTicks() {
            return 20;
        }

        @Override
        protected int startTimeDelay() {
            return 0;
        }

        @Nullable
        @Override
        protected SoundEvent getSoundPrepare() {
            return FSoundEvents.ENTITY_FROST_SPELL_FREEZE;
        }

        @Override
        protected Spell getSpell() {
            return Spell.DISAPPEAR;
        }

    }

    protected class SummonMinionsGoal extends SpellcastingIllagerEntity.CastSpellGoal {
        private final TargetPredicate closeMinionPredicate = TargetPredicate.createNonAttackable()
                .setBaseMaxDistance(16.0)
                .ignoreVisibility()
                .ignoreDistanceScalingFactor();

        public boolean canStart() {
            if (FrostologerEntity.this.random.nextInt(2) == 0) {
                return false;
            } else if (!super.canStart()) {
                return false;
            } else if (!FrostologerEntity.this.isTargetRooted()) {
                return false;
            } else {
                int numNearbyMinions = FrostologerEntity.this.world.getTargets(
                        VexEntity.class,
                        this.closeMinionPredicate,
                        FrostologerEntity.this,
                        FrostologerEntity.this.getBoundingBox().expand(16.0)
                ).size();

                return FrostologerEntity.this.random.nextInt(8) + 1 > numNearbyMinions;
            }
        }

        @Override
        protected void castSpell() {
            ServerWorld serverWorld = (ServerWorld) FrostologerEntity.this.world;

            for (int i = 0; i < 3; ++i) {
                BlockPos blockPos = FrostologerEntity.this.getBlockPos()
                        .add(
                                -2 + FrostologerEntity.this.random.nextInt(5),
                                1,
                                -2 + FrostologerEntity.this.random.nextInt(5)
                        );

                // use vex entity as placeholder for custom minions
                VexEntity minionEntity = EntityType.VEX.create(FrostologerEntity.this.world);

                if (minionEntity == null) {
                    return;
                }

                minionEntity.refreshPositionAndAngles(blockPos, 0.0F, 0.0F);

                minionEntity.initialize(
                        serverWorld,
                        FrostologerEntity.this.world.getLocalDifficulty(blockPos),
                        SpawnReason.MOB_SUMMONED,
                        null, null
                );
                minionEntity.setOwner(FrostologerEntity.this);
                minionEntity.setBounds(blockPos);
                minionEntity.setLifeTicks(20 * (30 + FrostologerEntity.this.random.nextInt(90)));

                serverWorld.spawnEntityAndPassengers(minionEntity);
            }
        }

        @Override
        protected int getSpellTicks() {
            return 100;
        }

        @Override
        protected int startTimeDelay() {
            return 20;
        }

        @Nullable
        @Override
        protected SoundEvent getSoundPrepare() {
            return SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON;
        }

        @Override
        protected Spell getSpell() {
            return Spell.SUMMON_VEX;
        }
    }
}
