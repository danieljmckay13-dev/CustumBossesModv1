package com.example.custommod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

@Mod(CustomBossesMod.MOD_ID)
public class CustomBossesMod {
    public static final String MOD_ID = "custombossesmod";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);

    public static final List<DeferredHolder<Item, Item>> BOSS_WEAPONS = new ArrayList<>();
    public static final List<DeferredHolder<EntityType<?>, EntityType<DimensionalBossEntity>>> BOSS_ENTITIES = new ArrayList<>();
    public static final List<ResourceKey<Biome>> CUSTOM_BIOMES = new ArrayList<>();

    // Boss 1 Unique Custom Mace
    public static final DeferredHolder<Item, Item> CUSTOM_MACE = ITEMS.register("boss_mace_1",
            () -> new MaceItem(new Item.Properties().rarity(Rarity.EPIC).durability(5000).fireResistant()));

    // Boss Netherite Armor Set
    public static final DeferredHolder<Item, Item> BOSS_HELMET = ITEMS.register("boss_helmet",
            () -> new ArmorItem(ArmorMaterials.NETHERITE, ArmorItem.Type.HELMET, new Item.Properties().rarity(Rarity.EPIC)));
    public static final DeferredHolder<Item, Item> BOSS_CHESTPLATE = ITEMS.register("boss_chestplate",
            () -> new ArmorItem(ArmorMaterials.NETHERITE, ArmorItem.Type.CHESTPLATE, new Item.Properties().rarity(Rarity.EPIC)));
    public static final DeferredHolder<Item, Item> BOSS_LEGGINGS = ITEMS.register("boss_leggings",
            () -> new ArmorItem(ArmorMaterials.NETHERITE, ArmorItem.Type.LEGGINGS, new Item.Properties().rarity(Rarity.EPIC)));
    public static final DeferredHolder<Item, Item> BOSS_BOOTS = ITEMS.register("boss_boots",
            () -> new ArmorItem(ArmorMaterials.NETHERITE, ArmorItem.Type.BOOTS, new Item.Properties().rarity(Rarity.EPIC)));

    public static final DeferredHolder<EntityType<?>, EntityType<DimensionalBossEntity>> DIMENSIONAL_BOSS = ENTITIES.register("dimensional_boss",
            () -> EntityType.Builder.of(DimensionalBossEntity::new, MobCategory.MONSTER)
                    .sized(1.5F, 3.0F)
                    .build("dimensional_boss"));

    public CustomBossesMod(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        ENTITIES.register(modEventBus);

        modEventBus.addListener(this::registerAttributes);
        NeoForge.EVENT_BUS.register(this);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(DIMENSIONAL_BOSS.get(), DimensionalBossEntity.createAttributes().build());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("spawnboss")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("bossName", StringArgumentType.string())
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            DimensionalBossEntity boss = DIMENSIONAL_BOSS.get().create(player.level());
                            if (boss != null) {
                                boss.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                                player.level().addFreshEntity(boss);
                                context.getSource().sendSuccess(() -> Component.literal("Boss Spawned!"), true);
                            }
                            return 1;
                        })));
    }

    public static class DimensionalBossEntity extends Monster {
        private final ServerBossEvent bossEvent = new ServerBossEvent(
                Component.literal("Dimensional Boss"),
                BossEvent.BossBarColor.PURPLE,
                BossEvent.BossBarOverlay.PROGRESS
        );

        public DimensionalBossEntity(EntityType<? extends Monster> type, Level level) {
            super(type, level);
            this.setHealth(this.getMaxHealth());
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Monster.createMonsterAttributes()
                    .add(Attributes.MAX_HEALTH, 500.0D)
                    .add(Attributes.MOVEMENT_SPEED, 0.35D)
                    .add(Attributes.ATTACK_DAMAGE, 20.0D)
                    .add(Attributes.ARMOR, 10.0D)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(1, new FloatGoal(this));
            this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, false));
            this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0D));
            this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
            this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

            this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        }

        @Override
        public void startSeenByPlayer(ServerPlayer player) {
            super.startSeenByPlayer(player);
            this.bossEvent.addPlayer(player);
        }

        @Override
        public void stopSeenByPlayer(ServerPlayer player) {
            super.stopSeenByPlayer(player);
            this.bossEvent.removePlayer(player);
        }

        @Override
        public void customServerAiStep() {
            super.customServerAiStep();
            this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());

            if (this.tickCount % 100 == 0) {
                this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 1));
            }
        }

        @Override
        protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean hitByPlayer) {
            super.dropCustomDeathLoot(level, damageSource, hitByPlayer);
            this.spawnAtLocation(CUSTOM_MACE.get());
        }
    }
}
