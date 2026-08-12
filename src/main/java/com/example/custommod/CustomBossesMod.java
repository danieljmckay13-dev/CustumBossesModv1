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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
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

    static {
        BOSS_WEAPONS.add(CUSTOM_MACE);

        // Register 19 Boss Swords with scaling attack damage
        for (int i = 2; i <= 20; i++) {
            final int index = i;
            BOSS_WEAPONS.add(ITEMS.register("boss_weapon_" + index,
                    () -> new SwordItem(Tiers.NETHERITE, new Item.Properties()
                            .attributes(SwordItem.createAttributes(Tiers.NETHERITE, 16 + index, -2.0F))
                            .rarity(Rarity.EPIC)
                            .fireResistant())));
        }

        // Register 20 Boss Entities
        for (int i = 1; i <= 20; i++) {
            final int index = i;
            BOSS_ENTITIES.add(ENTITIES.register("boss_" + index,
                    () -> EntityType.Builder.<DimensionalBossEntity>of((type, level) -> new DimensionalBossEntity(type, level, index), MobCategory.MONSTER)
                            .sized(2.2F, 4.2F)
                            .build(MOD_ID + ":boss_" + index)));
        }

        // 20 Biomes Registry Keys
        for (int i = 1; i <= 20; i++) {
            CUSTOM_BIOMES.add(ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(MOD_ID, "custom_biome_" + i)));
        }
    }

    public CustomBossesMod(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        ENTITIES.register(modEventBus);

        modEventBus.addListener(this::registerBossAttributes);
        NeoForge.EVENT_BUS.register(this);
    }

    // Dynamic Boss Scaling Attributes (Health: 800-3650 HP, Damage: 22-98)
    private void registerBossAttributes(EntityAttributeCreationEvent event) {
        for (int i = 0; i < 20; i++) {
            double health = 800.0D + (i * 150.0D);
            double damage = 22.0D + (i * 4.0D);

            event.put(BOSS_ENTITIES.get(i).get(), Monster.createMonsterAttributes()
                    .add(Attributes.MAX_HEALTH, health)
                    .add(Attributes.ATTACK_DAMAGE, damage)
                    .add(Attributes.MOVEMENT_SPEED, 0.38D)
                    .add(Attributes.ARMOR, 24.0D)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                    .add(Attributes.FOLLOW_RANGE, 64.0D)
                    .build());
        }
    }

    // Lock Player Max Health to 40 HP (20 Hearts)
    @SubscribeEvent
    public void onPlayerJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Player player && !event.getLevel().isClientSide()) {
            AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null && maxHealth.getBaseValue() != 40.0D) {
                maxHealth.setBaseValue(40.0D);
                player.setHealth(40.0F);
            }
        }
    }

    // Prevent standard Mace crafting
    @SubscribeEvent
    public void onCraftingCheck(PlayerEvent.ItemCraftedEvent event) {
        if (event.getCrafting().getItem() == Items.MACE) {
            event.getCrafting().setCount(0);
            if (event.getEntity() instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.literal("§cCrafting disabled! Defeat Boss 1 to get the Custom Mace."));
            }
        }
    }

    // Command: /locator bar null
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("locator")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("bar")
                .then(Commands.argument("target", StringArgumentType.string())
                    .executes(context -> {
                        String targetArg = StringArgumentType.getString(context, "target");
                        CommandSourceStack source = context.getSource();

                        if ("null".equalsIgnoreCase(targetArg)) {
                            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                                player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 1200, 0, false, false));
                                source.sendSuccess(() -> Component.literal("§a[OP Locator] Position: §e" 
                                    + player.getName().getString() 
                                    + " §aat: §f" + player.blockPosition().toShortString()), true);
                            }
                            return 1;
                        }
                        return 0;
                    }))));
    }

    // Dimensional Boss AI & Attack Mechanics
    public static class DimensionalBossEntity extends Monster {
        private final int bossId;
        private final ServerBossEvent bossEvent;
        private int abilityTimer = 0;

        public DimensionalBossEntity(EntityType<? extends Monster> type, Level level, int bossId) {
            super(type, level);
            this.bossId = bossId;
            this.xpReward = 600 * bossId;

            this.bossEvent = new ServerBossEvent(
                    Component.literal(getBossTitle(bossId)),
                    getBossBarColor(bossId),
                    BossEvent.BossBarOverlay.PROGRESS
            );
        }

        private static String getBossTitle(int id) {
            if (id <= 16) return "§e§lOverworld Boss #" + id;
            if (id == 17) return "§c§lNether Boss #1 - Infernal Drake";
            if (id == 18) return "§c§lNether Boss #2 - Soulbound Titan";
            if (id == 19) return "§5§lEnd Boss #1 - Void Sovereign";
            return "§5§lEnd Boss #2 - Apex Oblivion";
        }

        private static BossEvent.BossBarColor getBossBarColor(int id) {
            if (id <= 16) return BossEvent.BossBarColor.GREEN;
            if (id <= 18) return BossEvent.BossBarColor.RED;
            return BossEvent.BossBarColor.PURPLE;
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(1, new FloatGoal(this));
            this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.35D, false));
            this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8D));
            this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 16.0F));
            this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

            this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        }

        @Override
        public void aiStep() {
            super.aiStep();
            this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());

            if (!this.level().isClientSide()) {
                abilityTimer++;

                // Enrage below 50% HP
                if (this.getHealth() < (this.getMaxHealth() / 2.0F)) {
                    this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, false, false));
                    this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 1, false, false));
                }

                // Special ability every 8 seconds
                if (abilityTimer >= 160) {
                    abilityTimer = 0;
                    executeDimensionalAttack();
                }
            }
        }

        private void executeDimensionalAttack() {
            AABB area = this.getBoundingBox().inflate(10.0D);
            List<Player> targets = this.level().getEntitiesOfClass(Player.class, area);

            for (Player player : targets) {
                if (bossId == 17 || bossId == 18) { // Nether Attacks
                    player.igniteForSeconds(10);
                    this.level().addParticle(ParticleTypes.FLAME, player.getX(), player.getY() + 1, player.getZ(), 0, 0.1, 0);
                    player.sendSystemMessage(Component.literal("§c§lHellfire engulfs you!"));
                } else if (bossId == 19 || bossId == 20) { // End Attacks
                    player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 120, 1));
                    this.teleportTo(player.getX(), player.getY(), player.getZ());
                    player.sendSystemMessage(Component.literal("§5§lVoid energy disrupts gravity!"));
                } else { // Overworld Attacks
                    player.hurt(this.damageSources().mobAttack(this), 14.0F + bossId);
                    player.knockback(2.2D, this.getX() - player.getX(), this.getZ() - player.getZ());
                    player.sendSystemMessage(Component.literal("§a§lA seismic shockwave knocks you back!"));
                }
            }
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
        protected void dropCustomDeathLoot(net.minecraft.world.damagesource.DamageSource source, int looting, boolean hitByPlayer) {
            super.dropCustomDeathLoot(source, looting, hitByPlayer);
            if (bossId >= 1 && bossId <= 20) {
                this.spawnAtLocation(new ItemStack(BOSS_WEAPONS.get(bossId - 1).get()));
            }
        }
    }
}
