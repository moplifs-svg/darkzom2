package com.example.darkzombies;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.pathfinder.Path;

/**
 * From shortly before dawn until dusk, a zombie hides in a dark place (cave, deep shade).
 * While hiding it stays put. It only comes out to fight when:
 *  - the target is standing in the dark AND the whole route to it is dark, or
 *  - the target is right next to it (melee reach).
 * A player standing in the light will not lure it out.
 */
public class HideInDarkGoal extends Goal {

    // Minecraft day time (0-24000). 21000 = a little before sunrise, 12500 = just after sunset starts.
    private static final int HIDE_FROM = 21000;
    private static final int HIDE_UNTIL = 12500;

    private static final int SEARCH_RANGE = 24;
    private static final int SEARCH_HEIGHT = 10;
    private static final int SAMPLES = 60;
    private static final int PATH_TESTS = 6;

    private static final int DARK_SKY_LIGHT = 5;   // "dark enough to stay"
    private static final int DEEP_SKY_LIGHT = 2;   // preferred hiding spot, deeper in the dark
    private static final int MAX_BLOCK_LIGHT = 7;  // torches etc. make a place unsafe
    private static final double MELEE_REACH_SQR = 2.5D * 2.5D;

    private final Zombie mob;
    private final double speed;

    private long cooldownUntil;
    private long chaseCheckAt;
    private boolean chaseAllowed;
    private boolean heading;
    private int repathTimer;

    public HideInDarkGoal(Zombie mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    // ---------- light / time helpers ----------

    public static boolean isHideTime(Level level) {
        if (level.dimension() != Level.OVERWORLD) {
            return false;
        }
        long t = level.getDayTime() % 24000L;
        return t >= HIDE_FROM || t < HIDE_UNTIL;
    }

    private static boolean isDark(Level level, BlockPos pos) {
        return !level.canSeeSky(pos)
                && level.getBrightness(LightLayer.SKY, pos) <= DARK_SKY_LIGHT
                && level.getBrightness(LightLayer.BLOCK, pos) <= MAX_BLOCK_LIGHT;
    }

    private static boolean isDeepDark(Level level, BlockPos pos) {
        return !level.canSeeSky(pos)
                && level.getBrightness(LightLayer.SKY, pos) <= DEEP_SKY_LIGHT
                && level.getBrightness(LightLayer.BLOCK, pos) <= MAX_BLOCK_LIGHT;
    }

    private boolean isStandable(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && level.getFluidState(pos).isEmpty()
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    // ---------- attack decision ----------

    private boolean shouldFight() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        Level level = mob.level();
        if (mob.distanceToSqr(target) <= MELEE_REACH_SQR) {
            return true; // defend itself
        }
        if (!isDark(level, mob.blockPosition())) {
            return false;
        }
        long now = level.getGameTime();
        if (now >= chaseCheckAt) {
            chaseAllowed = isDarkRouteTo(target);
            chaseCheckAt = now + 10;
        }
        return chaseAllowed;
    }

    private boolean isDarkRouteTo(LivingEntity target) {
        Level level = mob.level();
        if (!isDark(level, target.blockPosition())) {
            return false;
        }
        Path path = mob.getNavigation().createPath(target, 0);
        if (path == null) {
            return false;
        }
        for (int i = 0; i < path.getNodeCount(); i++) {
            if (!isDark(level, path.getNode(i).asBlockPos())) {
                return false;
            }
        }
        return true;
    }

    // ---------- goal lifecycle ----------

    @Override
    public boolean canUse() {
        Level level = mob.level();
        if (level.isClientSide() || !mob.isAlive() || mob.isPassenger()) {
            return false;
        }
        if (!isHideTime(level) || level.getGameTime() < cooldownUntil) {
            return false;
        }
        return !shouldFight();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        heading = false;
        repathTimer = 0;
        if (isDark(mob.level(), mob.blockPosition())) {
            mob.getNavigation().stop();
        }
    }

    @Override
    public void tick() {
        Level level = mob.level();

        // Still walking toward a hiding spot: keep going.
        if (heading && !mob.getNavigation().isDone()) {
            return;
        }
        heading = false;

        if (isDark(level, mob.blockPosition())) {
            mob.getNavigation().stop(); // hold position in the dark
            return;
        }

        if (--repathTimer > 0) {
            return;
        }
        repathTimer = 20;

        BlockPos spot = findDarkSpot();
        if (spot == null) {
            // Nowhere to hide nearby: let normal zombie AI run for a while.
            cooldownUntil = level.getGameTime() + 100;
            return;
        }
        heading = mob.getNavigation().moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, speed);
        if (!heading) {
            cooldownUntil = level.getGameTime() + 100;
        }
    }

    @Override
    public void stop() {
        heading = false;
        mob.getNavigation().stop();
    }

    // ---------- finding somewhere dark ----------

    private BlockPos findDarkSpot() {
        Level level = mob.level();
        RandomSource random = mob.getRandom();
        BlockPos origin = mob.blockPosition();

        List<BlockPos> deep = new ArrayList<>();
        List<BlockPos> shallow = new ArrayList<>();

        for (int i = 0; i < SAMPLES; i++) {
            BlockPos p = origin.offset(
                    random.nextInt(SEARCH_RANGE * 2 + 1) - SEARCH_RANGE,
                    random.nextInt(SEARCH_HEIGHT * 2 + 1) - SEARCH_HEIGHT,
                    random.nextInt(SEARCH_RANGE * 2 + 1) - SEARCH_RANGE);
            if (!level.hasChunkAt(p) || !isStandable(level, p)) {
                continue;
            }
            if (isDeepDark(level, p)) {
                deep.add(p);
            } else if (isDark(level, p)) {
                shallow.add(p);
            }
        }

        BlockPos found = firstReachable(deep, origin);
        return found != null ? found : firstReachable(shallow, origin);
    }

    private BlockPos firstReachable(List<BlockPos> candidates, BlockPos origin) {
        candidates.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
        int tests = 0;
        for (BlockPos p : candidates) {
            if (tests++ >= PATH_TESTS) {
                break;
            }
            Path path = mob.getNavigation().createPath(p, 0);
            if (path != null && path.canReach()) {
                return p;
            }
        }
        return null;
    }
}
