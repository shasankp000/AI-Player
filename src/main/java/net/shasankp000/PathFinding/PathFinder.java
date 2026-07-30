package net.shasankp000.PathFinding;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class PathFinder {

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");

    public static class PathNode {
        public final BlockPos pos;
        public final String blockName;
        public final boolean jump;
        public boolean walkable;

        public PathNode(BlockPos pos, String blockName, boolean walkable, boolean jump) {
            this.pos = pos;
            this.blockName = blockName;
            this.jump = jump;
            this.walkable = walkable;
        }

        @Override
        public String toString() {
            return "PathNode{" +
                    "pos=" + pos +
                    ", blockName='" + blockName + '\'' +
                    ", jump=" + jump +
                    '}';
        }

        public boolean jumpNeeded() {
            return jump;
        }

        public BlockPos getPos() {
            return pos;
        }
    }


    private static class Node implements Comparable<Node> {
        BlockPos position;
        Node parent;
        double gScore;
        double hScore;
        double fScore;

        Node(BlockPos position, Node parent, double gScore, double hScore) {
            this.position = position;
            this.parent = parent;
            this.gScore = gScore;
            this.hScore = hScore;
            this.fScore = gScore + hScore;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.fScore, other.fScore);
        }
    }

    public static List<PathNode> calculatePath(BlockPos start, BlockPos target, ServerWorld world) {
        LOGGER.info("Starting Bi-directional A* pathfinding with block tagging...");

        PriorityQueue<Node> openForward = new PriorityQueue<>();
        PriorityQueue<Node> openBackward = new PriorityQueue<>();
        Map<BlockPos, Node> openMapForward = new HashMap<>();
        Map<BlockPos, Node> openMapBackward = new HashMap<>();
        Map<BlockPos, Node> closedForward = new HashMap<>();
        Map<BlockPos, Node> closedBackward = new HashMap<>();

        Node startNode = new Node(start, null, 0, getDistance(start, target));
        Node goalNode = new Node(target, null, 0, getDistance(target, start));

        openForward.add(startNode);
        openMapForward.put(start, startNode);

        openBackward.add(goalNode);
        openMapBackward.put(target, goalNode);

        while (!openForward.isEmpty() && !openBackward.isEmpty()) {

            // ---------- Expand forward ----------
            Node currentForward = openForward.poll();
            openMapForward.remove(currentForward.position);
            closedForward.put(currentForward.position, currentForward);

            for (BlockPos neighbor : getNeighbors(currentForward.position, world)) {
                if (closedForward.containsKey(neighbor)) continue;

                double tentativeG = currentForward.gScore + getDistance(currentForward.position, neighbor);

                if (openMapForward.containsKey(neighbor)) {
                    Node existing = openMapForward.get(neighbor);
                    if (tentativeG < existing.gScore) {
                        existing.gScore = tentativeG;
                        existing.parent = currentForward;
                        existing.fScore = tentativeG + existing.hScore;
                        // Re-heapify by removing + re-adding
                        openForward.remove(existing);
                        openForward.add(existing);
                    }
                } else {
                    Node neighborNode = new Node(neighbor, currentForward, tentativeG, getDistance(neighbor, target));
                    openForward.add(neighborNode);
                    openMapForward.put(neighbor, neighborNode);
                }

                if (closedBackward.containsKey(neighbor)) {
                    LOGGER.info("Path overlap detected! Merging paths...");
                    List<BlockPos> rawPath = mergePaths(openMapForward.get(neighbor), closedBackward.get(neighbor));
                    return tagBlocks(rawPath, world);
                }
            }

            // ---------- Expand backward ----------
            Node currentBackward = openBackward.poll();
            openMapBackward.remove(currentBackward.position);
            closedBackward.put(currentBackward.position, currentBackward);

            for (BlockPos neighbor : getNeighbors(currentBackward.position, world)) {
                if (closedBackward.containsKey(neighbor)) continue;

                double tentativeG = currentBackward.gScore + getDistance(currentBackward.position, neighbor);

                if (openMapBackward.containsKey(neighbor)) {
                    Node existing = openMapBackward.get(neighbor);
                    if (tentativeG < existing.gScore) {
                        existing.gScore = tentativeG;
                        existing.parent = currentBackward;
                        existing.fScore = tentativeG + existing.hScore;
                        openBackward.remove(existing);
                        openBackward.add(existing);
                    }
                } else {
                    Node neighborNode = new Node(neighbor, currentBackward, tentativeG, getDistance(neighbor, start));
                    openBackward.add(neighborNode);
                    openMapBackward.put(neighbor, neighborNode);
                }

                if (closedForward.containsKey(neighbor)) {
                    LOGGER.info("Path overlap detected! Merging paths...");
                    List<BlockPos> rawPath = mergePaths(closedForward.get(neighbor), openMapBackward.get(neighbor));
                    return tagBlocks(rawPath, world);
                }
            }
        }

        LOGGER.warn("No path found between {} and {}", start, target);
        return new ArrayList<>();
    }


    public static List<PathNode> simplifyPath(List<PathNode> path, ServerWorld world) {
        if (path.isEmpty()) return path;

        List<PathNode> simplifiedPath = new ArrayList<>();
        PathNode prev = null;

        for (PathNode current : path) {
            BlockPos pos = current.pos;

            BlockPos feetPos = pos.down();
            BlockPos bodyPos = pos;
            BlockPos headPos = pos.up();

            boolean solidBelow = isSolidBlock(world, feetPos);
            boolean bodyClear = isPassable(world, bodyPos);
            boolean headClear = isPassable(world, headPos);

            boolean canStand = solidBelow && bodyClear && headClear;
            boolean jumpNeeded = current.jumpNeeded();

            if (!canStand) {
                // Try jumping up
                BlockPos upFeetPos = feetPos.up();
                BlockPos upBodyPos = bodyPos.up();
                BlockPos upHeadPos = headPos.up();

                boolean solidBelowUp = isSolidBlock(world, upFeetPos);
                boolean bodyClearUp = isPassable(world, upBodyPos);
                boolean headClearUp = isPassable(world, upHeadPos);

                if (solidBelowUp && bodyClearUp && headClearUp) {
                    canStand = true;
                    jumpNeeded = true;
                    pos = pos.up(); // Adjust to landing position
                } else {
                    LOGGER.info("Unwalkable multi-block obstacle at {}, skipping.", pos);
                    continue; // Completely blocked
                }
            }

            String feetBlockType = world.getBlockState(feetPos).getBlock().getName().getString().toLowerCase();

            PathNode node = new PathNode(pos, feetBlockType, canStand, jumpNeeded);

            if (prev == null || !prev.pos.equals(pos)) {
                simplifiedPath.add(node);
                prev = node;
            } else {
                LOGGER.info("Duplicate node at {}, skipping.", pos);
            }
        }

        LOGGER.info("Simplified path length after filtering: {}", simplifiedPath.size());
        System.out.println("Simplified path list: " + simplifiedPath);
        return simplifiedPath;
    }


    public static Queue<Segment> convertPathToSegments(List<PathNode> simplifiedPath, boolean sprint) {
        Queue<Segment> segments = new LinkedList<>();

        if (simplifiedPath.isEmpty()) {
            return segments;
        }

        PathNode segmentStart = simplifiedPath.get(0);
        String currentAxis = null;
        int currentSign = 0;

        for (int i = 1; i < simplifiedPath.size(); i++) {
            PathNode prev = simplifiedPath.get(i - 1);
            PathNode current = simplifiedPath.get(i);

            // Calculate the difference
            int dx = current.getPos().getX() - prev.getPos().getX();
            int dz = current.getPos().getZ() - prev.getPos().getZ();
            int dy = current.getPos().getY() - prev.getPos().getY();

            String axis;
            int sign;

            if (dx != 0) {
                axis = "x";
                sign = Integer.signum(dx);
            } else if (dz != 0) {
                axis = "z";
                sign = Integer.signum(dz);
            } else if (dy != 0) {
                axis = "y";
                sign = Integer.signum(dy);
            } else {
                // No movement, skip
                continue;
            }

            // Initialize axis and sign for the first comparison
            if (currentAxis == null) {
                currentAxis = axis;
                currentSign = sign;
            }

            boolean axisChanged = !axis.equals(currentAxis);
            boolean directionChanged = sign != currentSign;

            // Check if a jump is needed at either the start or end of the segment
            boolean jumpBreak = current.jumpNeeded() || prev.jumpNeeded();

            if (jumpBreak || axisChanged || directionChanged) {
                // Create the segment up to prev node, use jump flag from either node
                if (!segmentStart.getPos().equals(prev.getPos())) { // Skip zero-length segments
                    segments.add(new Segment(segmentStart.getPos(), prev.getPos(), segmentStart.jumpNeeded() || prev.jumpNeeded(), sprint));
                }
                segmentStart = prev;

                // Update current direction state
                currentAxis = axis;
                currentSign = sign;
            }
        }

        // Add final segment, use jump flag from either start or end node
        PathNode lastNode = simplifiedPath.get(simplifiedPath.size() - 1);
        if (!segmentStart.getPos().equals(lastNode.getPos())) { // Skip zero-length segments
            segments.add(new Segment(segmentStart.getPos(), lastNode.getPos(), segmentStart.jumpNeeded() || lastNode.jumpNeeded(), sprint));
        }

        return segments;
    }

    // These two are outdated methods, but I am still keeping them for a while.

//    public static List<String> identifyPrimaryAxis(List<BlockPos> path) {
//
//        int xChanges = 0, yChanges = 0, zChanges = 0;
//
//        BlockPos prevPos = path.get(0);
//
//
//
//        for (BlockPos pos : path) {
//
//            if (pos.getX() != prevPos.getX()) xChanges++;
//
//            if (pos.getY() != prevPos.getY()) yChanges++;
//
//            if (pos.getZ() != prevPos.getZ()) zChanges++;
//
//            prevPos = pos;
//
//        }
//
//
//
//        List<String> axisPriorityList = new ArrayList<>();
//
//        if (xChanges > 0) axisPriorityList.add("x");
//
//        if (yChanges > 0) axisPriorityList.add("y");
//
//        if (zChanges > 0) axisPriorityList.add("z");
//
//
//
//        final int finalX = xChanges, finalY = yChanges, finalZ = zChanges;
//
//
//
//        axisPriorityList.sort((a, b) -> {
//
//            int aChanges = a.equals("x") ? finalX : a.equals("y") ? finalY : finalZ;
//
//            int bChanges = b.equals("x") ? finalX : b.equals("y") ? finalY : finalZ;
//
//            return Integer.compare(bChanges, aChanges);
//
//        });
//
//
//
//        return axisPriorityList;
//
//    }
//
//
//    private static boolean isSameDirection(BlockPos start, BlockPos current, BlockPos segmentStart) {
//        int dx1 = Integer.signum(segmentStart.getX() - start.getX());
//        int dz1 = Integer.signum(segmentStart.getZ() - start.getZ());
//
//        int dx2 = Integer.signum(current.getX() - start.getX());
//        int dz2 = Integer.signum(current.getZ() - start.getZ());
//
//        return dx1 == dx2 && dz1 == dz2;
//    }


    private static List<PathNode> tagBlocks(List<BlockPos> rawPath, ServerWorld world) {
        List<PathNode> taggedPath = new ArrayList<>();

        for (int i = 0; i < rawPath.size(); i++) {
            BlockPos pos = rawPath.get(i);
            BlockPos feetPos = pos.down();
            BlockPos bodyPos = pos;
            BlockPos headPos = pos.up();

            boolean solidBelow = isSolidBlock(world, feetPos);
            boolean bodyIsSlab = isSlab(world, bodyPos);
            boolean canStepUpSlab = solidBelow && bodyIsSlab && isPassable(world, headPos);
            boolean bodyClear = isPassable(world, bodyPos);
            boolean headClear = isPassable(world, headPos);

            boolean canStand = solidBelow && bodyClear && headClear;
            boolean jumpRequired = false;

            BlockState bodyState = world.getBlockState(bodyPos);
            boolean isCrop = bodyState.isIn(BlockTags.CROPS);

            if (bodyClear && isCrop) {
                LOGGER.info("Detected crops at {}, setting jumpRequired=false", pos);
                jumpRequired = false;
            }

            // Extra: check for stepping up
            if (i > 0) {
                BlockPos prev = rawPath.get(i - 1);
                int dy = pos.getY() - prev.getY();

                if (dy > 0) {
                    // Upward step
                    if (bodyIsSlab || canStepUpSlab) {
                        jumpRequired = false; // treat slab as walkable step
                        LOGGER.info("Stepping up onto slab at {}, no jump needed", pos);
                    } else {
                        jumpRequired = true;
                        LOGGER.info("Stepping up full block at {}, jump required", pos);
                    }
                } else {
                    // Same level: check block in front
                    Vec3i dir = pos.subtract(prev);
                    BlockPos forward = prev.add(dir.getX(), 0, dir.getZ());
                    if (isSolidBlock(world, forward) && !isSlab(world, forward)) {
                        jumpRequired = true;
                    } else if (isSlab(world, forward)) {
                        LOGGER.info("Stepping onto slab in front at {}, no jump needed", forward);
                        jumpRequired = false;
                    }
                }
            }

            if (!canStand) {
                BlockPos upFeet = feetPos.up();
                BlockPos upBody = bodyPos.up();
                BlockPos upHead = headPos.up();

                boolean canStandUp = isSolidBlock(world, upFeet) && isPassable(world, upBody) && isPassable(world, upHead);

                if (canStandUp) {
                    BlockState upHeadState = world.getBlockState(upHead);
                    boolean isSlab = upHeadState.isIn(BlockTags.SLABS);
                    boolean hasCollision = !upHeadState.getCollisionShape(world, upHead).isEmpty();

                    if (isSlab || hasCollision) {
                        LOGGER.info("Jump blocked: obstacle above {} is slab/partial block", upHead);
                        continue; // skip blocked path
                    }

                    canStand = true;
                    jumpRequired = true;
                    pos = pos.up();
                } else {
                    continue; // skip fully blocked
                }
            }

            String feetBlockType = world.getBlockState(feetPos).getBlock().getName().getString().toLowerCase();
            PathNode node = new PathNode(pos, feetBlockType, canStand, jumpRequired);
            taggedPath.add(node);

            LOGGER.info("Tag {}: solidBelow={} bodyClear={} headClear={} canStand={} jumpRequired={} canStepUpSlab={}",
                    pos, solidBelow, bodyClear, headClear, canStand, jumpRequired, canStepUpSlab);
        }

        return taggedPath;
    }



    private static boolean isSlab(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isIn(BlockTags.SLABS);
    }



    private static boolean isPassable(ServerWorld world, BlockPos pos) {
        BlockState blockState = world.getBlockState(pos);
        return blockState.isAir() || blockState.isOf(Blocks.WATER) || !blockState.getCollisionShape(world, pos).isEmpty();
    }


    private static boolean isSolidBlock(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return !state.isAir() && state.isOpaque();
    }


    private static boolean isWalkableBlock(String blockType) {
        return blockType.equals("air") || blockType.contains("water");
    }

    private static boolean isJumpableBlock(String blockType, ServerWorld world, BlockPos pos) {
        // Example: if block is <= 1 block tall and you can jump over it
        BlockPos above = pos.up();
        Block aboveBlock = world.getBlockState(above).getBlock();
        String aboveType = aboveBlock.getName().getString().toLowerCase();

        System.out.println("Block above: " + aboveType);

        return !aboveType.equals("air");  // If block above is air, we could jump it.
    }


    private static List<BlockPos> mergePaths(Node forwardNode, Node backwardNode) {
        List<BlockPos> forwardPath = reconstructPath(forwardNode);
        List<BlockPos> backwardPath = reconstructPath(backwardNode);
        backwardPath.remove(0); // Remove duplicate overlap node
        Collections.reverse(backwardPath);
        forwardPath.addAll(backwardPath);

        LOGGER.info("Bi-directional path merged. Total raw nodes: {}", forwardPath.size());
        return forwardPath;
    }

    private static List<BlockPos> reconstructPath(Node node) {
        List<BlockPos> path = new ArrayList<>();
        while (node != null) {
            path.add(0, node.position);
            node = node.parent;
        }
        return path;
    }

    private static List<BlockPos> getNeighbors(BlockPos pos, ServerWorld world) {
        List<BlockPos> neighbors = new ArrayList<>();

        // Standard moves on same level
        neighbors.add(pos.add(1, 0, 0));  // East
        neighbors.add(pos.add(-1, 0, 0)); // West
        neighbors.add(pos.add(0, 0, 1));  // South
        neighbors.add(pos.add(0, 0, -1)); // North
        neighbors.add(pos.add(0, -1, 0)); // Down
        neighbors.add(pos.add(0, 1, 0));  // Up - careful: raw vertical climb

        // Smart step-up moves: only add if there's a block in front
        for (BlockPos flatNeighbor : List.of(
                pos.add(1, 0, 0),
                pos.add(-1, 0, 0),
                pos.add(0, 0, 1),
                pos.add(0, 0, -1))) {

            BlockPos blockInFront = flatNeighbor;
            BlockPos topOfBlock = blockInFront.up();
            BlockPos headSpace = topOfBlock.up();

            if (isSolidBlock(world, blockInFront) && isPassable(world, topOfBlock) && isPassable(world, headSpace)) {
                neighbors.add(topOfBlock); // stepping onto it
            }
        }

        return neighbors;
    }


    private static double getDistance(BlockPos pos1, BlockPos pos2) {
        return Math.sqrt(pos1.getSquaredDistance(pos2));
    }
}
