/*
** 2016 March 12
**
** The author disclaims copyright to this source code. In place of
** a legal notice, here is a blessing:
**    May you do good and not evil.
**    May you find forgiveness for yourself and forgive others.
**    May you share freely, never taking more than you give.
 */
package info.ata4.minecraft.dragon.server.entity.helper;

import info.ata4.minecraft.dragon.server.entity.EntityTameableDragon;
import info.ata4.minecraft.dragon.server.entity.ai.air.EntityAICatchOwnerAir;
import info.ata4.minecraft.dragon.server.entity.ai.air.EntityAILand;
import info.ata4.minecraft.dragon.server.entity.ai.air.EntityAIRideAir;
import info.ata4.minecraft.dragon.server.entity.ai.ground.EntityAICatchOwnerGround;
import info.ata4.minecraft.dragon.server.entity.ai.ground.EntityAIDragonFollowOwner;
import info.ata4.minecraft.dragon.server.entity.ai.ground.EntityAIDragonMate;
import info.ata4.minecraft.dragon.server.entity.ai.ground.EntityAIHunt;
import info.ata4.minecraft.dragon.server.entity.ai.ground.EntityAIRideGround;
import info.ata4.minecraft.dragon.server.entity.ai.ground.EntityAIWatchIdle;
import info.ata4.minecraft.dragon.server.entity.ai.ground.EntityAIWatchLiving;
import info.ata4.minecraft.dragon.server.util.EntityClassPredicate;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAIFollowParent;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAIOwnerHurtByTarget;
import net.minecraft.entity.ai.EntityAIOwnerHurtTarget;
import net.minecraft.entity.ai.EntityAIPanic;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.ai.EntityAITempt;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntityRabbit;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathNavigateGround;

/**
 *
 * @author Nico Bergemann <barracuda415 at yahoo.de>
 */
public class DragonBrain extends DragonHelper {

    // mutex 1: movement
    // mutex 2: looking
    // mutex 4: special state
    private final EntityAITasks tasks;
    
    // mutex 1: waypointing
    // mutex 2: continuous waypointing
    private final EntityAITasks airTasks;
    
    // mutex 1: generic targeting
    private final EntityAITasks targetTasks;
    
    public DragonBrain(EntityTameableDragon dragon) {
        super(dragon);
        tasks = dragon.tasks;
        airTasks = dragon.airTasks;
        targetTasks = dragon.targetTasks;
    }
    
    public void setAvoidsWater(boolean avoidWater) {
        PathNavigate pathNavigate = dragon.getNavigator();
        if (pathNavigate instanceof PathNavigateGround) {
            PathNavigateGround pathNavigateGround = (PathNavigateGround) pathNavigate;
            pathNavigateGround.setAvoidsWater(avoidWater);
        }
    }
    
    public void clearTasks() {
        while (!tasks.taskEntries.isEmpty()) {
            EntityAIBase entityAIBase = tasks.taskEntries.get(0).action;
            tasks.removeTask(entityAIBase);
        }
        while (!airTasks.taskEntries.isEmpty()) {
            EntityAIBase entityAIBase = airTasks.taskEntries.get(0).action;
            airTasks.removeTask(entityAIBase);
        }
        while (!targetTasks.taskEntries.isEmpty()) {
            EntityAIBase entityAIBase = targetTasks.taskEntries.get(0).action;
            targetTasks.removeTask(entityAIBase);
        }
    }
    
    public void updateAITasks() {
        // eggs and hatchlings can't fly
        dragon.setCanFly(!dragon.isEgg() && !dragon.isHatchling());
        
        // only hatchlings are small enough for doors
        // (eggs don't move on their own anyway and are ignored)
        // guessed, based on EntityAIRestrictOpenDoor - break the door down, don't open it
        if (dragon.getNavigator() instanceof PathNavigateGround) {
            PathNavigateGround pathNavigateGround = (PathNavigateGround) dragon.getNavigator();
            pathNavigateGround.setEnterDoors(dragon.isHatchling());
        }
        
        // clear existing tasks
        clearTasks();
        
        // eggs don't have any tasks
        if (dragon.isEgg()) {
            return;
        }

        tasks.addTask(0, new EntityAICatchOwnerGround(dragon)); // mutex all
        tasks.addTask(1, new EntityAIRideGround(dragon, 1)); // mutex all
        tasks.addTask(2, new EntityAISwimming(dragon)); // mutex 4
        tasks.addTask(4, dragon.getAISit()); // mutex 4+1
        
        tasks.addTask(6, new EntityAITempt(dragon, 0.75, dragon.getBreed().getFavoriteFood(), false)); // mutex 2+1
        tasks.addTask(7, new EntityAIAttackOnCollide(dragon, 1, true)); // mutex 2+1
        
        tasks.addTask(9, new EntityAIDragonFollowOwner(dragon, 1, 12, 128)); // mutex 2+1
        tasks.addTask(10, new EntityAIWander(dragon, 1)); // mutex 1
        tasks.addTask(11, new EntityAIWatchIdle(dragon)); // mutex 2
        tasks.addTask(11, new EntityAIWatchLiving(dragon, 16, 0.05f)); // mutex 2
        
        targetTasks.addTask(5, new EntityAIHunt(dragon, EntityAnimal.class, false,
            new EntityClassPredicate(
                    EntitySheep.class,
                    EntityPig.class,
                    EntityChicken.class,
                    EntityRabbit.class
            )
        )); // mutex 1

        if (dragon.isHatchling()) {
            tasks.addTask(8, new EntityAIFollowParent(dragon, 0.8)); // mutex 2+1
            tasks.addTask(9, new EntityAIPanic(dragon, 1)); // mutex 1
        } else {
            targetTasks.addTask(2, new EntityAIOwnerHurtByTarget(dragon)); // mutex 1
            targetTasks.addTask(3, new EntityAIOwnerHurtTarget(dragon)); // mutex 1
            targetTasks.addTask(4, new EntityAIHurtByTarget(dragon, false)); // mutex 1
        }
        
        if (dragon.isAdult()) {
            tasks.addTask(5, new EntityAIDragonMate(dragon, 0.6)); // mutex 2+1
        }
        
        if (dragon.isCanFly()) {
            airTasks.addTask(0, new EntityAIRideAir(dragon)); // mutex all
            airTasks.addTask(0, new EntityAILand(dragon)); // mutex 0
            airTasks.addTask(0, new EntityAICatchOwnerAir(dragon)); // mutex all
        }
    }
}
