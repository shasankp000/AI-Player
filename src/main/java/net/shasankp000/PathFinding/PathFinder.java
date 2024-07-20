package net.shasankp000.PathFinding;

import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

public class PathFinder {

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");

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

    public static List<BlockPos> simplifyPath(List<BlockPos> path) {
        List<BlockPos> simplifiedPath = new ArrayList<>();
        BlockPos prevPos = null;
        for (BlockPos pos : path) {
            if (!pos.equals(prevPos)) {
                simplifiedPath.add(pos);
            }
            prevPos = pos;
        }
        return simplifiedPath;
    }

    public static char identifyPrimaryAxis(List<BlockPos> path) {
        int xChanges = 0;
        int yChanges = 0;
        int zChanges = 0;
        BlockPos prevPos = path.get(0);

        for (BlockPos pos : path) {
            if (pos.getX() != prevPos.getX()) xChanges++;
            if (pos.getY() != prevPos.getY()) yChanges++;
            if (pos.getZ() != prevPos.getZ()) zChanges++;
            prevPos = pos;
        }

        if (xChanges >= yChanges && xChanges >= zChanges) return 'x';
        if (yChanges >= xChanges && yChanges >= zChanges) return 'y';
        return 'z';
    }

    public static List<BlockPos> calculatePath(BlockPos start, BlockPos target) {
        // A* algorithm for pathfinding
        LOGGER.info("Finding the shortest path to the target, please wait patiently if the game seems hung");
        PriorityQueue<PathFinder.Node> openSet = new PriorityQueue<>();
        List<PathFinder.Node> closedSet = new ArrayList<>();

        PathFinder.Node startNode = new PathFinder.Node(start, null, 0, getDistance(start, target));
        openSet.add(startNode);

        while (!openSet.isEmpty()) {
            PathFinder.Node currentNode = openSet.poll();

            if (currentNode.position.equals(target)) {
                return reconstructPath(currentNode);
            }

            closedSet.add(currentNode);

            for (BlockPos neighbor : getNeighbors(currentNode.position)) {
                if (closedSet.stream().anyMatch(node -> node.position.equals(neighbor))) {
                    continue;
                }

                double tentativeGScore = currentNode.gScore + getDistance(currentNode.position, neighbor);
                PathFinder.Node neighborNode = openSet.stream().filter(node -> node.position.equals(neighbor)).findFirst().orElse(null);

                if (neighborNode == null) {
                    neighborNode = new PathFinder.Node(neighbor, currentNode, tentativeGScore, getDistance(neighbor, target));
                    openSet.add(neighborNode);
                } else if (tentativeGScore < neighborNode.gScore) {
                    neighborNode.parent = currentNode;
                    neighborNode.gScore = tentativeGScore;
                    neighborNode.fScore = tentativeGScore + neighborNode.hScore;
                }
            }
        }

        return new ArrayList<>();
    }

    private static List<BlockPos> reconstructPath(PathFinder.Node node) {
        List<BlockPos> path = new ArrayList<>();
        while (node != null) {
            path.add(0, node.position);
            node = node.parent;
        }
        return path;
    }

    private static List<BlockPos> getNeighbors(BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();
        neighbors.add(pos.add(1, 0, 0));
        neighbors.add(pos.add(-1, 0, 0));
        neighbors.add(pos.add(0, 0, 1));
        neighbors.add(pos.add(0, 0, -1));
        neighbors.add(pos.add(0, 1, 0));
        neighbors.add(pos.add(0, -1, 0));
        return neighbors;
    }

    private static double getDistance(BlockPos pos1, BlockPos pos2) {
        return Math.sqrt(pos1.getSquaredDistance(pos2));
    }


}
