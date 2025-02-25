/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2023 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.utils.combat

import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.Configurable
import net.ccbluex.liquidbounce.features.misc.FriendManager
import net.ccbluex.liquidbounce.features.module.modules.misc.ModuleAntiBot
import net.ccbluex.liquidbounce.features.module.modules.misc.ModuleTeams
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.entity.boxedDistanceTo
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.passive.PassiveEntity
import net.minecraft.entity.player.PlayerEntity

/**
 * Global enemy configurable
 *
 * Modules can have their own enemy configurable if required. If not, they should use this as default.
 * Global enemy configurable can be used to configure which entities should be considered as an enemy.
 *
 * This can be adjusted by the .enemy command and the panel inside the ClickGUI.
 */
val globalEnemyConfigurable = EnemyConfigurable()

/**
 * Configurable to configure which entities and their state (like being dead) should be considered as an enemy
 */
class EnemyConfigurable : Configurable("Enemies") {

    // Players should be considered as an enemy
    var players by boolean("Players", true)

    // Hostile mobs (like skeletons and zombies) should be considered as an enemy
    var mobs by boolean("Mobs", true)

    // Animals (like cows, pigs and so on) should be considered as an enemy
    var animals by boolean("Animals", false)

    // Invisible entities should be also considered as an enemy
    var invisible by boolean("Invisible", true)

    // Dead entities should be also considered as an enemy to bypass modern anti cheat techniques
    var dead by boolean("Dead", false)

    // Friends (client friends - other players) should be also considered as enemy - similar to module NoFriends
    var friends by boolean("Friends", false)

    // Teammates should be also considered as enemy - same thing like Teams module -> might be replaced by this
    // Todo: this is currently handled using the Teams module
    var teamMates by boolean("TeamMates", false)

    init {
        ConfigSystem.root(this)
    }

    /**
     * Check if an entity is considered an enemy
     */
    fun isTargeted(suspect: Entity, attackable: Boolean = false): Boolean {
        // Check if the enemy is living and not dead (or ignore being dead)
        if (suspect is LivingEntity && (dead || suspect.isAlive)) {
            // Check if enemy is invisible (or ignore being invisible)
            if (invisible || !suspect.isInvisible) {
                if (attackable && !teamMates && ModuleTeams.isInClientPlayersTeam(suspect)) {
                    return false
                }

                // Check if enemy is a player and should be considered as an enemy
                if (suspect is PlayerEntity && suspect != mc.player) {
                    if (attackable && !friends && FriendManager.isFriend(suspect.gameProfile.name)) {
                        return false
                    }

                    // Check if player might be a bot
                    if (ModuleAntiBot.isBot(suspect)) {
                        return false
                    }

                    return players
                } else if (suspect is PassiveEntity) {
                    return animals
                } else if (suspect is MobEntity) {
                    return mobs
                }
            }
        }

        return false
    }

}

// Extensions

@JvmOverloads
fun Entity.shouldBeShown(enemyConf: EnemyConfigurable = globalEnemyConfigurable) = enemyConf.isTargeted(this)

fun Entity.shouldBeAttacked(enemyConf: EnemyConfigurable = globalEnemyConfigurable) = enemyConf.isTargeted(
    this,
    true
)

/**
 * Find the best enemy in the current world in a specific range.
 */
fun ClientWorld.findEnemy(
    range: ClosedFloatingPointRange<Float>,
    player: Entity = mc.player!!,
    enemyConf: EnemyConfigurable = globalEnemyConfigurable
) = entities.filter { it.shouldBeAttacked(enemyConf) }.map { Pair(it, it.boxedDistanceTo(player)) }
    .filter { (_, distance) -> distance > range.start && distance <= range.endInclusive }
    .minByOrNull { (_, distance) -> distance }
