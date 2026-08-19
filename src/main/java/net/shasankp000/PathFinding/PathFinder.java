package net.shasankp000.PathFinding;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.shape.VoxelShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/** Directional, collision-aware A* for a player-sized server-side bot. */
public final class PathFinder {
    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static final double HALF_WIDTH = 0.29;
    private static final double HEIGHT = 1.79;
    private static final double EPSILON = 1.0E-4;
    private static final int MAX_EXPANSIONS = 100_000;
    private static final int[][] CARDINAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAGONAL = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final double SQRT_TWO = Math.sqrt(2.0);

    private PathFinder() {}

    public static final class PathNode {
        public final BlockPos pos;
        public final String blockName;
        public final boolean jump;
        public final boolean walkable;
        private final Vec3d target;
        private final MovementType movement;

        public PathNode(BlockPos pos, String blockName, boolean walkable, boolean jump) {
            this(new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), jump ? MovementType.JUMP_UP : MovementType.WALK,
                    blockName, walkable);
        }

        public PathNode(Vec3d target, MovementType movement, String blockName, boolean walkable) {
            this.target = target;
            this.pos = BlockPos.ofFloored(target.x, target.y + EPSILON, target.z);
            this.movement = movement;
            this.blockName = blockName;
            this.jump = movement == MovementType.JUMP_UP;
            this.walkable = walkable;
        }

        public boolean jumpNeeded() { return jump; }
        public BlockPos getPos() { return pos; }
        public Vec3d target() { return target; }
        public MovementType movement() { return movement; }

        @Override
        public String toString() { return "PathNode{target=" + target + ", movement=" + movement + '}'; }
    }

    private record NavKey(int x, int y, int z, boolean water) {}

    private static final class SearchNode implements Comparable<SearchNode> {
        final NavKey key;
        final Vec3d target;
        final MovementType movement;
        final SearchNode parent;
        final double g;
        final double h;

        SearchNode(NavKey key, Vec3d target, MovementType movement, SearchNode parent, double g, double h) {
            this.key = key;
            this.target = target;
            this.movement = movement;
            this.parent = parent;
            this.g = g;
            this.h = h;
        }

        @Override public int compareTo(SearchNode other) { return Double.compare(g + h, other.g + other.h); }
    }

    public enum SearchStatus { SEARCHING, FOUND, NO_PATH, INVALID_GOAL }

    /** Stateful search so callers can budget expansions over server ticks. */
    public static final class Search {
        private final NavigationWorldView world;
        private final PriorityQueue<SearchNode> open = new PriorityQueue<>();
        private final Map<NavKey, Double> bestOpenG = new HashMap<>();
        private final Set<NavKey> closed = new HashSet<>();
        private final Vec3d requestedGoal;
        private final Vec3d effectiveGoal;
        private final Set<BlockPos> penalizedTargets;
        private final GoalDisposition disposition;
        private SearchStatus status = SearchStatus.SEARCHING;
        private List<PathNode> result = List.of();
        private int expansions;

        private Search(NavigationWorldView world, Vec3d startPosition, BlockPos goal, int horizon,
                       Set<BlockPos> penalizedTargets) {
            this.world = world;
            this.penalizedTargets = Set.copyOf(penalizedTargets);
            this.requestedGoal = new Vec3d(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5);
            HorizonTarget horizonTarget = capToLoadedHorizon(world, startPosition, requestedGoal, horizon);
            Vec3d localGoal = horizonTarget.target();
            Optional<SearchNode> start = normalizeStart(world, startPosition);
            Optional<NormalizedGoal> normalizedGoal = normalizeGoal(world,
                    BlockPos.ofFloored(localGoal.x, localGoal.y, localGoal.z));
            if (start.isEmpty() || normalizedGoal.isEmpty()) {
                status = SearchStatus.INVALID_GOAL;
                effectiveGoal = localGoal;
                disposition = horizonTarget.capped() ? GoalDisposition.HORIZON_FRONTIER : GoalDisposition.EXACT;
                return;
            }
            effectiveGoal = normalizedGoal.get().node().target;
            disposition = horizonTarget.capped() ? GoalDisposition.HORIZON_FRONTIER
                    : normalizedGoal.get().normalized() ? GoalDisposition.NORMALIZED_FINAL : GoalDisposition.EXACT;
            SearchNode first = start.get();
            SearchNode seeded = new SearchNode(first.key, first.target, MovementType.START, null, 0,
                    heuristic(first.target, effectiveGoal));
            open.add(seeded);
            bestOpenG.put(seeded.key, seeded.g);
        }

        public SearchStatus advance(int budget) {
            return advance(budget, Long.MAX_VALUE);
        }

        SearchStatus advance(int budget, long deadlineNanos) {
            if (status != SearchStatus.SEARCHING) return status;
            for (int i = 0; i < budget && !open.isEmpty() && expansions < MAX_EXPANSIONS
                    && System.nanoTime() < deadlineNanos; i++, expansions++) {
                SearchNode current = open.poll();
                Double best = bestOpenG.get(current.key);
                if (isStaleQueueEntry(current.g, best) || !closed.add(current.key)) continue;
                bestOpenG.remove(current.key);
                if (horizontalDistance(current.target, effectiveGoal) <= 0.51
                        && Math.abs(current.target.y - effectiveGoal.y) <= 0.76) {
                    result = smooth(reconstruct(current), world);
                    status = SearchStatus.FOUND;
                    return status;
                }
                for (SearchNode unpenalized : successors(world, current, effectiveGoal)) {
                    SearchNode next = penalizedTargets.contains(BlockPos.ofFloored(
                            unpenalized.target.x, unpenalized.target.y, unpenalized.target.z))
                            ? new SearchNode(unpenalized.key, unpenalized.target, unpenalized.movement,
                            unpenalized.parent, unpenalized.g + 8.0, unpenalized.h)
                            : unpenalized;
                    if (closed.contains(next.key)) continue;
                    Double existing = bestOpenG.get(next.key);
                    if (existing == null || next.g + EPSILON < existing) {
                        open.add(next);
                        bestOpenG.put(next.key, next.g);
                    }
                }
            }
            if (open.isEmpty() || expansions >= MAX_EXPANSIONS) status = SearchStatus.NO_PATH;
            return status;
        }

        public List<PathNode> result() { return result; }
        public Vec3d requestedGoal() { return requestedGoal; }
        public Vec3d effectiveGoal() { return effectiveGoal; }
        public GoalDisposition disposition() { return disposition; }
        public int expansions() { return expansions; }
        public int openSize() { return open.size(); }
        public int closedSize() { return closed.size(); }
    }

    static boolean isStaleQueueEntry(double queuedCost, Double bestKnownCost) {
        return bestKnownCost == null || queuedCost > bestKnownCost + EPSILON;
    }

    public static Search beginSearch(ServerWorld world, Vec3d start, BlockPos goal, int horizon) {
        return new Search(NavigationWorldView.live(world), start, goal, horizon, Set.of());
    }

    public static Search beginSearch(ServerWorld world, Vec3d start, BlockPos goal, int horizon,
                                     Set<BlockPos> penalizedTargets) {
        return new Search(NavigationWorldView.live(world), start, goal, horizon, penalizedTargets);
    }

    public static Search beginSearch(NavigationWorldView world, Vec3d start, BlockPos goal, int horizon,
                                     Set<BlockPos> penalizedTargets) {
        return new Search(world, start, goal, horizon, penalizedTargets);
    }

    /** Compatibility entry point; live navigation advances a Search over ticks. */
    public static List<PathNode> calculatePath(BlockPos start, BlockPos target, ServerWorld world) {
        Search search = beginSearch(world,
                new Vec3d(start.getX() + 0.5, start.getY(), start.getZ() + 0.5), target,
                NavigationOptions.DEFAULT_HORIZON);
        while (search.advance(2_000) == SearchStatus.SEARCHING) { }
        return search.status == SearchStatus.FOUND ? search.result() : List.of();
    }

    public static List<PathNode> simplifyPath(List<PathNode> path, ServerWorld world) {
        return smooth(path, NavigationWorldView.live(world));
    }

    public static Queue<Segment> convertPathToSegments(List<PathNode> path, boolean sprint) {
        Queue<Segment> segments = new ArrayDeque<>();
        for (int i = 1; i < path.size(); i++) {
            PathNode from = path.get(i - 1);
            PathNode to = path.get(i);
            segments.add(new Segment(from.target(), to.target(), to.movement(), sprint));
        }
        return segments;
    }

    public static boolean isWaypointStillValid(ServerWorld world, PathNode node) {
        NavigationWorldView view = NavigationWorldView.live(world);
        if (node.movement().targetIsWater()) {
            BlockPos nodePos = BlockPos.ofFloored(node.target().x, node.target().y, node.target().z);
            return isWaterCell(view, nodePos);
        }
        return bodyClear(view, node.target()) && hasSupportAt(view, node.target());
    }

    private static List<SearchNode> successors(NavigationWorldView world, SearchNode current, Vec3d goal) {
        List<SearchNode> out = new ArrayList<>(12);
        if (current.key.water) addVerticalWaterSuccessors(world, current, goal, out);
        for (int[] direction : CARDINAL) {
            int x = current.key.x + direction[0];
            int z = current.key.z + direction[1];
            SearchNode ground = bestGroundSuccessor(world, current, x, z, goal, false, direction[0], direction[1]);
            if (ground != null) out.add(ground);
            int waterY = (int) Math.floor(current.target.y);
            for (int candidateY : new int[]{waterY, waterY - 1}) {
                BlockPos waterPos = new BlockPos(x, candidateY, z);
                if (isWaterCell(world, waterPos)) {
                    Vec3d target = new Vec3d(waterPos.getX() + 0.5, waterPos.getY() + 0.5, waterPos.getZ() + 0.5);
                    MovementType type = current.key.water ? MovementType.SWIM : MovementType.ENTER_WATER;
                    if (transitionClear(world, current.target, target, type)) {
                        out.add(node(current, keyForWater(waterPos), target, type, goal, movementCost(type)));
                    }
                    break;
                }
            }
        }
        if (!current.key.water) {
            for (int[] direction : DIAGONAL) {
                SearchNode diagonal = bestGroundSuccessor(world, current,
                        current.key.x + direction[0], current.key.z + direction[1], goal,
                        true, direction[0], direction[1]);
                if (diagonal != null) out.add(diagonal);
            }
        }
        return out;
    }

    private static SearchNode bestGroundSuccessor(NavigationWorldView world, SearchNode current, int x, int z,
                                                   Vec3d goal, boolean diagonal, int dx, int dz) {
        int baseSupportY = current.key.water ? (int) Math.floor(current.target.y) : current.key.y;
        for (int supportY = baseSupportY + 1; supportY >= baseSupportY - 3; supportY--) {
            Optional<Vec3d> landing = groundTarget(world, new BlockPos(x, supportY, z));
            if (landing.isEmpty()) continue;
            Vec3d target = landing.get();
            double rise = target.y - current.target.y;
            MovementType type;
            if (current.key.water) type = MovementType.EXIT_WATER;
            else if (rise > 0.61 && rise <= 1.25) type = MovementType.JUMP_UP;
            else if (rise > EPSILON && rise <= 0.61) type = MovementType.STEP_UP;
            else if (rise >= -0.61) type = MovementType.WALK;
            else if (rise >= -3.01) type = MovementType.DROP;
            else continue;
            if (diagonal && type != MovementType.WALK && type != MovementType.DROP) continue;
            if (diagonal && !diagonalCornersClear(world, current, target, type, dx, dz, goal)) continue;
            if (type != MovementType.WALK && type != MovementType.STEP_UP
                    && !transitionClear(world, current.target, target, type)) continue;
            double lengthCost = diagonal ? SQRT_TWO : 1.0;
            double cost = movementCost(type) * lengthCost
                    + Math.max(0, -rise) * 0.35 + clearancePenalty(world, target)
                    + hazardProximityPenalty(world, target);
            return node(current, keyForGround(x, supportY, z), target, type, goal, cost);
        }
        return null;
    }

    private static boolean diagonalCornersClear(NavigationWorldView world, SearchNode current, Vec3d target,
                                                 MovementType type, int dx, int dz, Vec3d goal) {
        SearchNode first = bestGroundSuccessor(world, current, current.key.x + dx, current.key.z,
                goal, false, dx, 0);
        SearchNode second = bestGroundSuccessor(world, current, current.key.x, current.key.z + dz,
                goal, false, 0, dz);
        if (first == null || second == null) return false;
        if (first.movement != type || second.movement != type) return false;
        if (Math.abs(first.target.y - target.y) > 0.1 || Math.abs(second.target.y - target.y) > 0.1) return false;
        return transitionClear(world, current.target, target, type);
    }

    private static void addVerticalWaterSuccessors(NavigationWorldView world, SearchNode current, Vec3d goal,
                                                    List<SearchNode> out) {
        for (int dy : new int[]{1, -1}) {
            BlockPos pos = new BlockPos(current.key.x, current.key.y + dy, current.key.z);
            if (isWaterCell(world, pos)) {
                Vec3d target = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                out.add(node(current, keyForWater(pos), target, MovementType.SWIM, goal,
                        movementCost(MovementType.SWIM) + 0.25));
            }
        }
    }

    private static SearchNode node(SearchNode parent, NavKey key, Vec3d target, MovementType type,
                                    Vec3d goal, double edgeCost) {
        return new SearchNode(key, target, type, parent, parent.g + edgeCost, heuristic(target, goal));
    }

    private static Optional<SearchNode> normalizeStart(NavigationWorldView world, Vec3d position) {
        BlockPos feet = BlockPos.ofFloored(position.x, position.y, position.z);
        if (isWaterCell(world, feet)) {
            return Optional.of(new SearchNode(keyForWater(feet),
                    new Vec3d(feet.getX() + 0.5, feet.getY() + 0.5, feet.getZ() + 0.5),
                    MovementType.START, null, 0, 0));
        }
        for (int y = feet.getY(); y >= feet.getY() - 3; y--) {
            BlockPos support = new BlockPos(feet.getX(), y, feet.getZ());
            Optional<Vec3d> target = groundTarget(world, support);
            if (target.isPresent() && Math.abs(target.get().y - position.y) <= 1.3) {
                return Optional.of(new SearchNode(
                        keyForGround(support.getX(), support.getY(), support.getZ()),
                        target.get(), MovementType.START, null, 0, 0));
            }
        }
        return Optional.empty();
    }

    private record NormalizedGoal(SearchNode node, boolean normalized) {}

    private static Optional<NormalizedGoal> normalizeGoal(NavigationWorldView world, BlockPos requested) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int radius = 0; radius <= 2; radius++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) == radius)
                            candidates.add(requested.add(dx, dy, dz));
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(requested)));
        for (BlockPos feet : candidates) {
            if (isWaterCell(world, feet)) {
                SearchNode node = new SearchNode(keyForWater(feet),
                        new Vec3d(feet.getX() + 0.5, feet.getY() + 0.5, feet.getZ() + 0.5),
                        MovementType.SWIM, null, 0, 0);
                return Optional.of(new NormalizedGoal(node, !feet.equals(requested)));
            }
            for (int supportY : new int[]{feet.getY() - 1, feet.getY()}) {
                BlockPos support = new BlockPos(feet.getX(), supportY, feet.getZ());
                Optional<Vec3d> target = groundTarget(world, support);
                if (target.isPresent()) {
                    SearchNode node = new SearchNode(
                            keyForGround(support.getX(), support.getY(), support.getZ()),
                            target.get(), MovementType.WALK, null, 0, 0);
                    Vec3d requestedTarget = new Vec3d(requested.getX() + 0.5, requested.getY(),
                            requested.getZ() + 0.5);
                    boolean normalized = !feet.equals(requested)
                            || target.get().distanceTo(requestedTarget) > 0.1;
                    return Optional.of(new NormalizedGoal(node, normalized));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Vec3d> groundTarget(NavigationWorldView world, BlockPos support) {
        if (!world.isLoaded(support) || isHazard(world, support)) return Optional.empty();
        VoxelShape shape = world.collisionShape(support);
        if (shape.isEmpty()) return Optional.empty();
        double top = shape.getMax(Direction.Axis.Y);
        if (top < 0.25 || top > 1.0 + EPSILON) return Optional.empty();
        Vec3d target = new Vec3d(support.getX() + 0.5, support.getY() + top, support.getZ() + 0.5);
        BlockPos body = BlockPos.ofFloored(target.x, target.y, target.z);
        if (isHazard(world, body) || isWater(world, body)) return Optional.empty();
        return bodyClear(world, target) ? Optional.of(target) : Optional.empty();
    }

    private static boolean bodyClear(NavigationWorldView world, Vec3d feet) {
        BlockPos pos = BlockPos.ofFloored(feet.x, feet.y, feet.z);
        return world.isLoaded(pos) && world.isLoaded(pos.up())
                && world.noBlockCollision(playerBox(feet));
    }

    private static boolean hasSupportAt(NavigationWorldView world, Vec3d feet) {
        int blockX = (int) Math.floor(feet.x);
        int blockY = (int) Math.floor(feet.y - EPSILON);
        int blockZ = (int) Math.floor(feet.z);
        BlockPos support = new BlockPos(blockX, blockY, blockZ);
        if (!world.isLoaded(support)) return false;
        VoxelShape shape = world.collisionShape(support);
        if (shape.isEmpty()) return false;
        return collisionBoxesSupport(shape.getBoundingBoxes(),
                feet.x - blockX, feet.z - blockZ, feet.y - blockY);
    }

    static boolean collisionBoxesSupport(List<Box> boxes, double localX, double localZ, double expectedTop) {
        for (Box box : boxes) {
            boolean insideX = localX >= box.minX - EPSILON && localX <= box.maxX + EPSILON;
            boolean insideZ = localZ >= box.minZ - EPSILON && localZ <= box.maxZ + EPSILON;
            if (insideX && insideZ && Math.abs(box.maxY - expectedTop) <= 0.08) return true;
        }
        return false;
    }

    private static boolean transitionClear(NavigationWorldView world, Vec3d from, Vec3d to, MovementType type) {
        int samples = Math.max(2, (int) Math.ceil(from.distanceTo(to) * 4));
        for (int i = 1; i <= samples; i++) {
            double t = i / (double) samples;
            Vec3d point = from.lerp(to, t);
            if (type == MovementType.JUMP_UP) point = new Vec3d(point.x, jumpArcY(from.y, to.y, t), point.z);
            if (type == MovementType.STEP_UP || type == MovementType.EXIT_WATER) {
                point = new Vec3d(point.x, to.y, point.z);
            }
            if (type == MovementType.DROP) {
                point = t < 0.5
                        ? new Vec3d(from.x + (to.x - from.x) * t * 2, from.y,
                        from.z + (to.z - from.z) * t * 2)
                        : new Vec3d(to.x, from.y + (to.y - from.y) * (t - 0.5) * 2, to.z);
            }
            if (!bodyClear(world, point)) return false;
            if (type == MovementType.WALK && !hasSupportAt(world, point)) return false;
            if (isHazard(world, BlockPos.ofFloored(point.x, point.y, point.z))) return false;
        }
        return true;
    }

    /**
     * Conservative approximation of a vanilla jump: rapid initial lift, a short
     * apex no more than 1.25 blocks above takeoff, then descent onto the landing.
     * A symmetric sine arc rises too late and falsely collides with one-block steps.
     */
    static double jumpArcY(double fromY, double toY, double progress) {
        double t = Math.max(0.0, Math.min(1.0, progress));
        double apex = Math.max(toY, fromY + 1.25);
        if (t <= 0.40) return fromY + (apex - fromY) * (t / 0.40);
        if (t <= 0.65) return apex;
        return apex + (toY - apex) * ((t - 0.65) / 0.35);
    }

    private static boolean isWaterCell(NavigationWorldView world, BlockPos pos) {
        return world.isLoaded(pos) && isWater(world, pos) && !isHazard(world, pos)
                && world.noBlockCollision(playerBox(
                new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)));
    }

    private static boolean isWater(NavigationWorldView world, BlockPos pos) {
        return world.isWater(pos);
    }

    private static boolean isHazard(NavigationWorldView world, BlockPos pos) {
        return world.isHazard(pos);
    }

    private static double clearancePenalty(NavigationWorldView world, Vec3d target) {
        int blocked = 0;
        BlockPos feet = BlockPos.ofFloored(target.x, target.y, target.z);
        for (int[] direction : CARDINAL) {
            BlockPos side = feet.add(direction[0], 0, direction[1]);
            if (!world.isLoaded(side) || !world.collisionShape(side).isEmpty()) blocked++;
        }
        return blocked * 0.12;
    }

    private static double hazardProximityPenalty(NavigationWorldView world, Vec3d target) {
        BlockPos feet = BlockPos.ofFloored(target.x, target.y, target.z);
        double penalty = 0.0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (world.isHazard(feet.add(dx, 0, dz))
                        || world.isHazard(feet.add(dx, -1, dz))) penalty += 1.5;
            }
        }
        return penalty;
    }

    static double movementCost(MovementType type) {
        return switch (type) {
            case WALK -> 1.0;
            case STEP_UP -> 1.15;
            case JUMP_UP -> 1.85;
            case DROP -> 1.35;
            case ENTER_WATER, EXIT_WATER -> 2.0;
            case SWIM -> 2.35;
            case START -> 0.0;
        };
    }

    private static List<PathNode> reconstruct(SearchNode end) {
        LinkedList<PathNode> path = new LinkedList<>();
        for (SearchNode node = end; node != null; node = node.parent) {
            path.addFirst(new PathNode(node.target, node.movement, node.key.water ? "water" : "ground", true));
        }
        return path;
    }

    private static List<PathNode> smooth(List<PathNode> path, NavigationWorldView world) {
        if (path.size() < 3) return List.copyOf(path);
        List<PathNode> smoothed = new ArrayList<>();
        int anchor = 0;
        smoothed.add(path.get(0));
        while (anchor < path.size() - 1) {
            int farthest = anchor + 1;
            for (int candidate = anchor + 2; candidate < path.size(); candidate++) {
                PathNode destination = path.get(candidate);
                boolean boundary = false;
                for (int i = anchor + 1; i <= candidate; i++) {
                    if (path.get(i).movement().isActionBoundary()) { boundary = true; break; }
                }
                if (boundary || Math.abs(path.get(anchor).target().y - destination.target().y) > 0.05
                        || !transitionClear(world, path.get(anchor).target(), destination.target(),
                        MovementType.WALK)) break;
                farthest = candidate;
            }
            PathNode selected = path.get(farthest);
            MovementType movement = farthest > anchor + 1 ? MovementType.WALK : selected.movement();
            smoothed.add(new PathNode(selected.target(), movement, selected.blockName, true));
            anchor = farthest;
        }
        return List.copyOf(smoothed);
    }

    private static Box playerBox(Vec3d feet) {
        return new Box(feet.x - HALF_WIDTH, feet.y + EPSILON, feet.z - HALF_WIDTH,
                feet.x + HALF_WIDTH, feet.y + HEIGHT, feet.z + HALF_WIDTH);
    }

    private static NavKey keyForGround(int x, int supportY, int z) {
        return new NavKey(x, supportY, z, false);
    }

    private static NavKey keyForWater(BlockPos pos) {
        return new NavKey(pos.getX(), pos.getY(), pos.getZ(), true);
    }

    static double heuristic(Vec3d a, Vec3d b) {
        double dx = Math.abs(a.x - b.x);
        double dz = Math.abs(a.z - b.z);
        double octile = Math.max(dx, dz) + (SQRT_TWO - 1.0) * Math.min(dx, dz);
        return octile + Math.abs(a.y - b.y) * 0.75;
    }

    private static double horizontalDistance(Vec3d a, Vec3d b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private record HorizonTarget(Vec3d target, boolean capped) {}

    private static HorizonTarget capToLoadedHorizon(NavigationWorldView world, Vec3d start, Vec3d goal, int horizon) {
        double distance = horizontalDistance(start, goal);
        if (distance <= horizon && world.isLoaded(BlockPos.ofFloored(goal.x, goal.y, goal.z)))
            return new HorizonTarget(goal, false);
        double scale = Math.min(1.0, horizon / Math.max(distance, 1.0E-6));
        Vec3d candidate = new Vec3d(start.x + (goal.x - start.x) * scale,
                start.y + Math.max(-2, Math.min(2, goal.y - start.y)),
                start.z + (goal.z - start.z) * scale);
        Vec3d directionBack = start.subtract(candidate).normalize();
        while (horizontalDistance(candidate, start) > 8
                && !world.isLoaded(BlockPos.ofFloored(candidate.x, candidate.y, candidate.z))) {
            candidate = candidate.add(directionBack);
        }
        return new HorizonTarget(candidate, true);
    }
}
