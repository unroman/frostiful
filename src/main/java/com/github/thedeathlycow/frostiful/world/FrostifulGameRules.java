package com.github.thedeathlycow.frostiful.world;

import com.github.thedeathlycow.frostiful.init.Frostiful;
import com.github.thedeathlycow.frostiful.util.TextStyles;
import net.fabricmc.fabric.api.gamerule.v1.CustomGameRuleCategory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;

/**
 * Custom game rules for Lost in the Cold
 */
public class FrostifulGameRules {

    /**
     * Mod's dedicated category for game rules
     */
    public static CustomGameRuleCategory CATEGORY = new CustomGameRuleCategory(
            new Identifier(Frostiful.MODID, "gamerule_category"),
            new TranslatableText("gamerule.category.lost-in-the-cold")
                    .setStyle(TextStyles.GAME_RULE_TITLE)
    );

    /**
     * Whether to apply passive freezing. When false, player freezing will revert
     * to vanilla, except for the increased frost resistance provided by their
     * frost resistance attribute.
     */
    public static final GameRules.Key<GameRules.BooleanRule> DO_PASSIVE_FREEZING =
            GameRuleRegistry.register(
                    "doPassiveFreezing",
                    CATEGORY,
                    GameRuleFactory.createBooleanRule(true)
            );

    public static void registerGamerules() {
        Frostiful.LOGGER.info("Registering gamerules...");
        // game rules already registered - calling this method just loads this class
        Frostiful.LOGGER.info("Gamerules registered!");
    }
}