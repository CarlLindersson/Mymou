package mymou.task.individual_tasks;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.widget.FrameLayout;

import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;
import android.graphics.drawable.Drawable;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.animation.ScaleAnimation;
import android.view.animation.Animation;
import android.view.animation.AlphaAnimation;

import androidx.constraintlayout.widget.ConstraintLayout; // Import ConstraintLayout

import androidx.preference.PreferenceManager;
import mymou.R;
import mymou.Utils.SoundManager;
import mymou.preferences.PreferencesManager;
import mymou.task.backend.RewardSystem;
import mymou.task.backend.TaskInterface;
import mymou.task.backend.UtilsTask;

import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import android.os.Handler; // Import the Handler class
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.Collections;

/**
 * Shaping 1: Maze Transition
 *
 * Displays an initial white cue with a gray edge.
 * When the white cue is pressed, it turns blue and a green node appears at the end of the edge.
 * Pressing the green node ends the trial.
 *
 * @param num_consecutive_corr the current number of consecutive presses
 *
 * TODO: (1) Make node larger at smallest stage (200).
 * TODO: (2) Shorten edges to be to nodes not under nodes.
 * TODO: (3) make level transition based on performance.
 * TODO: (4) Ensure data and photos are saved correctly.
 * TODO: (5) have setting that allow to animate transition
 * TODO: (6) Make LEVEL 3 where transitions are visible from the start
 * TODO: (7) Make SETTINGS CHANGEABLE FROM TABLET without computer.
 * TODO: (8) add touch listeners and make them log data.
 * TODO: (9) add logs for all other important data.
 */

public class TaskMaze extends Task {

    // Debug
    public static String TAG = "TaskMaze";

    private String preftag_successful_trial = "t_three_successful_trial";
    private String preftag_num_consecutive_corr = "t_three_num_consecutive_corr";
    private static int rew_scalar = 1;
    private static int num_consecutive_corr;
    private static PreferencesManager prefManager;
    private SharedPreferences settings;
    private static Activity activity;
    private static int latestRewardChannel;
    private static int reward_duration = 500;

    // Task objects
    private Point currentPosition;
    private Map<Point, Button> nodes = new HashMap<>();
    List<Edge> edges = new ArrayList<>();
    private Point goal;
    private int gridScale;
    private float transparencyLevel = 0.5f; // Example: 50% transparent

    private Button cue1; // First cue
    private Button cue2; // Second cue
    private Button cue3; // Third cue
    private Button cue4;
    private Point cue1Position;
    private Point cue2Position;
    private Point cue3Position;
    private Point cue4Position;

    private View blueCircle; // Player position

    private View edge1;   // Gray Edge connecting cue1 and cue2
    private View edge2;   // Gray Edge connecting cue1 and cue2
    private View edge3;

    // Track occupied positions in maze
    // Initialize a list to track occupied positions
    List<Point> occupiedPositions = new ArrayList<>();

    // Loggers to track session variables
    private static int l_rewgiven = 0;

    // starting taskLevel
    private static int taskLevel = -1;

    // if the task should adapt the level after performance or stay on start level
    private boolean adaptive = true;
    private double adaptationType = 1.4; // 0=KeepAchievedLevelAfterIdle; 1= ResetLevelsAfterIdle; 2=discrete steps of blinking; 3 = hard adaptation steps, 4 = blinking fade
    private ConstraintLayout parentLayout; // Declare as ConstraintLayout

    // Cue size
    private int cueSize = 200;
    private float nodeAlphaLevel = 0.3f;

    // Move duration
    private int moveDuration = 100;

    // Flag to track if the cues have been placed
    private boolean cuesPlaced = false;

    // Utility methods
    private Handler handler = new Handler(); // Handler to manage delays

    // A map to store the connected cues (edges between them)
    private Map<Button, List<Button>> cueConnections = new HashMap<>();

    // Tolarance when finding neighbors (allow or rounding errors)
    private static final int TOLERANCE = 5; // Allow for small rounding errors

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_task_empty, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState); // Call the superclass method

        // Set the background color of the root view to black
        view.setBackgroundColor(getResources().getColor(android.R.color.black));

        // Initialize parent layout after view is created
        parentLayout = view.findViewById(R.id.parent_task_empty); // Initialize parentLayout correctly

        logEvent(TAG + " started", callback);

        loadTrialParams();

        assignObjects();
    }

    // Method to start or restart the trial
    public void startTrial() {
        Log.d(TAG, "Starting a new trial");

        // Reset the current task views and state
        resetTrial();

        // Re-initialize cues and add them back to the parent layout
        assignObjects();

    }

    // Method to reset the task by removing all views and clearing the state
    private void resetTrial() {
        Log.d(TAG, "Resetting trial");

        // Remove all views from the parent layout
        if (parentLayout != null) {
            parentLayout.removeAllViews();
        }

        // Reset flags and states
        cuesPlaced = false;

    }

    // Method to show a black screen for ITI
    private void showInterTrialInterval(int duration) {
        Log.d(TAG, "Intertrial Interval - Showing black screen");

        // Remove all task elements (cues and edge) by clearing the parent layout
        resetTrial();

        // Set the background color to black
        parentLayout.setBackgroundColor(getResources().getColor(android.R.color.black));

        // Wait for 1 second (1000 milliseconds) before starting the next trial
        handler.postDelayed(() -> {
        }, duration);
    }

    // Method to build grid positions
    private List<Point> buildGrid(int rows, int cols, int gridScale, int offsetX, int offsetY) {
        List<Point> positions = new ArrayList<>();

        // Loop through the grid and calculate positions based on the grid scale and offset
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = offsetX + col * gridScale; // Calculate x position
                int y = offsetY + row * gridScale; // Calculate y position
                positions.add(new Point(x, y)); // Add to list of positions
            }
        }

        return positions;
    }

    // Helper method to create a node
    private Button createNode(Point position, int drawableResource, View.OnClickListener listener,
                              boolean visible, boolean clickable, boolean pulsate,
                              int offsetX, int offsetY, int gridScale) {
        Button node = UtilsTask.addColorCue(0, getResources().getColor(android.R.color.white),
                getContext(), listener, parentLayout);
        node.setBackgroundResource(drawableResource);
        // int cueSize = 500;

        node.setWidth(cueSize);
        node.setHeight(cueSize);
        node.setX(position.x - cueSize / 2);
        node.setY(position.y - cueSize / 2);

        int row = (position.y - offsetY) / gridScale;
        int col = (position.x - offsetX) / gridScale;

        String nodeId = row + "," + col; // Node ID as "row,col"

        node.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        node.setClickable(clickable);

        // Attach a listener to the node
        node.setOnClickListener(v -> {

            if (isNeighbor(currentPosition, position)) {
                animateBlueCircle(position, () -> {
                    // Update the current position after animation
                    currentPosition = position;
                    updateNodeClickability(); // Update node clickability based on new currentPosition
                });

                // Animate valid node click
                animateValidNodeClick(nodes.get(position));

                // Log the clicked node's ID
                logEvent("clickedNode: " + nodeId, callback);
                // If goal node, give reward and end trial
                if (position == goal) {
                    giveRewardAndEndTrial();
                }
            }
            else {

                shakeScreen(parentLayout);

                // Log the clicked non-neighbor node's ID
                logEvent("clickedNonNeighborNode: " + nodeId, callback); // saved in data
                Log.d(TAG, "Node not connected to the current position."); // for debugging
            }
        });

        // Start pulsation if enabled
        if (pulsate) {
            startAlphaPulse(node);
        } else {
            stopPulsation(node);
        }

        occupiedPositions.add(position);
        node.bringToFront();
        // Set the node to z-order 1 (layer 1)
        node.setZ(1f);

        return node;
    }

    public void animateValidNodeClick(Button node) {
        // Create an animation to scale up (increase size)
        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(node, "scaleX", 1.0f, 1.5f);
        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(node, "scaleY", 1.0f, 1.5f);

        // Create an animation to scale down (return to original size)
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(node, "scaleX", 1.5f, 1.0f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(node, "scaleY", 1.5f, 1.0f);

        // Set duration for animations
        scaleUpX.setDuration(100); // 100ms to scale up
        scaleUpY.setDuration(100);
        scaleDownX.setDuration(100); // 100ms to scale down
        scaleDownY.setDuration(100);

        // Combine animations into a sequence
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(scaleUpX).with(scaleUpY); // Play scale-up animations together
        animatorSet.play(scaleDownX).with(scaleDownY).after(scaleUpX); // Play scale-down after scale-up

        // Start the animation
        animatorSet.start();
    }

    // Shake animation method for parentLayout
    public void shakeScreen(View parentLayout) {
        // Create translation animations for the X-axis
        ObjectAnimator moveRight = ObjectAnimator.ofFloat(parentLayout, "translationX",
                0f, 25f); //0f, 25f
        ObjectAnimator moveLeft = ObjectAnimator.ofFloat(parentLayout, "translationX",
                25f, -25f); // 25f, -25f
        ObjectAnimator moveBack = ObjectAnimator.ofFloat(parentLayout, "translationX",
                -25f, 0f); // -25f, 0f

        // Set duration for each movement
        moveRight.setDuration(50); // 50
        moveLeft.setDuration(50); // 50
        moveBack.setDuration(50); // 50

        // Combine animations into a sequence
        AnimatorSet shakeAnimation = new AnimatorSet();
        shakeAnimation.playSequentially(moveRight, moveLeft, moveBack);

        // Start the animation
        shakeAnimation.start();
    }

    private boolean isNeighbor(Point current, Point target) {
        return edges.stream().anyMatch(edge ->
                (edge.start.equals(current) && edge.end.equals(target)) ||
                        (edge.end.equals(current) && edge.start.equals(target))
        );
    }

    private void updateNodeClickability() {
        for (Point position : occupiedPositions) {
            Button node = getNodeAtPosition(position); // Assume a method to retrieve the node at a position
            if (isNeighbor(currentPosition, position)) {
                node.setClickable(true);
            } else {
                node.setClickable(true);
            }
        }
    }

    private Button getNodeAtPosition(Point position) {
        // Assuming you have a mapping of Point to Button
        return nodes.get(position); // nodes is a Map<Point, Button>
    }

    // Method to start the pulsation animation, centered on the node's midpoint
    private void startPulsation(View view) {
        // Create a scale animation centered on the view's midpoint
        ScaleAnimation scaleAnimation = new ScaleAnimation(
                1.0f, 1.2f,  // Scale from 100% to 120% in X direction
                1.0f, 1.2f,  // Scale from 100% to 120% in Y direction
                Animation.RELATIVE_TO_SELF, 0.5f,  // Pivot at the center in X direction
                Animation.RELATIVE_TO_SELF, 0.5f   // Pivot at the center in Y direction
        );
        scaleAnimation.setDuration(500);          // Duration of each pulse
        scaleAnimation.setRepeatMode(Animation.REVERSE);  // Scale back down
        scaleAnimation.setRepeatCount(Animation.INFINITE);  // Repeat indefinitely
        view.startAnimation(scaleAnimation);
    }

    // Method to start the alpha (opacity) pulse animation
    private void startAlphaPulse(View view) {
        AlphaAnimation alphaAnimation = new AlphaAnimation(1.0f, nodeAlphaLevel);  // Pulse from fully visible to 30% opacity
        alphaAnimation.setDuration(500);  // Duration of each fade in/out cycle
        alphaAnimation.setRepeatMode(Animation.REVERSE);  // Reverse to fade back in
        alphaAnimation.setRepeatCount(Animation.INFINITE);  // Repeat indefinitely
        view.startAnimation(alphaAnimation);
    }

    // Method to stop the pulsation
    private void stopPulsation(View view) {
        view.clearAnimation();
    }

    // Helper method to create an edge
    public View createEdge(Point start, Point end) {
        View edge = new View(getContext());
        edge.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        int edgeThickness = 50;

        if (start.x == end.x) {
            edge.setLayoutParams(new ViewGroup.LayoutParams(edgeThickness, Math.abs(end.y - start.y)));
            edge.setX(start.x - edgeThickness / 2);
            edge.setY(Math.min(start.y, end.y));
        } else {
            edge.setLayoutParams(new ViewGroup.LayoutParams(Math.abs(end.x - start.x), edgeThickness));
            edge.setX(Math.min(start.x, end.x));
            edge.setY(start.y - edgeThickness / 2);
        }

        // Add the edge to the parent layout first (to ensure it is under the cues)
        parentLayout.addView(edge, 0);

        return edge;
    }

    public class Edge {
        public Point start;
        public Point end;

        public Edge(Point start, Point end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Edge edge = (Edge) obj;
            return (start.equals(edge.start) && end.equals(edge.end)) ||
                    (start.equals(edge.end) && end.equals(edge.start)); // Undirected edge
        }

        @Override
        public int hashCode() {
            return start.hashCode() + end.hashCode(); // Symmetric hash for undirected edges
        }

        @Override
        public String toString() {
            return "((" + (start.y / gridScale) + "," + (start.x / gridScale) + ") - ("
                    + (end.y / gridScale) + "," + (end.x / gridScale) + "))";
        }
    }

    public void populateGrid(int rows, int cols, int gridScale, int offsetX, int offsetY,
                             boolean sparse) {

        // Build the grid positions
        List<Point> positions = buildGrid(rows, cols, gridScale, offsetX, offsetY);

        // Create nodes for each grid position
        for (Point position : positions) {
            Button node = createNode(position, R.drawable.circle_shape_white,
                    null, true, true, false,
                    offsetX, offsetY, gridScale);
            nodes.put(position, node);

        }

        // Select random start and goal positions
        Random rand = new Random();
        Point start = positions.get(rand.nextInt(positions.size()));
        do {
            goal = positions.get(rand.nextInt(positions.size()));
        } while (goal.equals(start));

        // Highlight valid action nodes and goal
        highlightNodes(currentPosition, goal);

        // Highlight start and goal nodes
        nodes.get(start).setBackgroundResource(R.drawable.circle_shape_white);
        nodes.get(goal).setBackgroundResource(R.drawable.circle_shape);

        // Set current position to start node and set blue circle to start position.
        currentPosition = start;
        createBlueCircle(start);

        // log start position
        int start_row = (start.y - offsetY) / gridScale;
        int start_col = (start.x - offsetX) / gridScale;
        String startNodeId = start_row + "," + start_col; // Node ID as "row,col"
        logEvent("startNode:" + startNodeId, callback);

        // log goal node position
        int goal_row = (goal.y - offsetY) / gridScale;
        int goal_col = (goal.x - offsetX) / gridScale;
        String goalNodeId = goal_row + "," + goal_col; // Node ID as "row,col"
        logEvent("goalNode:" + goalNodeId, callback);

        // Create edges for the grid
        if (sparse) {
            // Generate a sparse graph using a spanning tree
            edges = generateSpanningTree(positions, gridScale);
        }
        else {
            // Connect each node to its immediate neighbors
            edges = generateFullyConnectedEdges(positions, gridScale);
            // Remove two random edges while ensuring connectivity
            removeRandomEdges(7); // 7 Remove 3 edges
        }

        // Create the visual edges in the grid
        for (Edge edge : edges) {
            createEdge(edge.start, edge.end);
        }

        logEvent("edge list:" + edges, callback);

        // Highlight valid action nodes and goal
        highlightNodes(currentPosition, goal);
    }

    public void highlightNodes(Point currentPosition, Point goal) {
        for (Map.Entry<Point, Button> entry : nodes.entrySet()) {
            Point position = entry.getKey();
            Button node = entry.getValue();

            // Highlight valid action nodes
            if (isNeighbor(currentPosition, position)) {
                node.setAlpha(1.0f); // Action nodes are fully opaque
            }
            else {
                if (position != goal) {
                    node.setAlpha(transparencyLevel); // Non-action nodes are slightly transparent
                }
            }

            nodes.get(goal).setAlpha(1);

        }
    }

    // Generate edges to fully connect all neighboring nodes
    private List<Edge> generateFullyConnectedEdges(List<Point> positions, int gridScale) {
        List<Edge> edges = new ArrayList<>();
        Set<Point> visited = new HashSet<>();

        for (Point current : positions) {
            for (Point neighbor : getNeighbors(current, positions, gridScale)) {
                if (!visited.contains(neighbor)) {
                    edges.add(new Edge(current, neighbor));
                }
            }
            visited.add(current);
        }

        return edges;
    }

    // Generate a randomized spanning tree for sparse connectivity
    private List<Edge> generateSpanningTree(List<Point> positions, int gridScale) {
        List<Edge> edges = new ArrayList<>();
        Set<Point> visited = new HashSet<>();
        Stack<Point> stack = new Stack<>();
        Random rand = new Random();

        Point start = positions.get(rand.nextInt(positions.size()));
        stack.push(start);
        visited.add(start);

        while (!stack.isEmpty()) {
            Point current = stack.pop();
            List<Point> neighbors = getNeighbors(current, positions, gridScale);

            Collections.shuffle(neighbors, rand);
            for (Point neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    edges.add(new Edge(current, neighbor));
                    stack.push(neighbor);
                    visited.add(neighbor);
                }
            }
        }

        return edges;
    }

    // Get neighbors of a point in the grid
    private List<Point> getNeighbors(Point current, List<Point> positions, int gridScale) {
        List<Point> neighbors = new ArrayList<>();
        for (Point pos : positions) {
            if ((Math.abs(pos.x - current.x) == gridScale && pos.y == current.y) ||
                    (Math.abs(pos.y - current.y) == gridScale && pos.x == current.x)) {
                neighbors.add(pos);
            }
        }
        return neighbors;
    }
    private void removeRandomEdges(int numberOfEdgesToRemove) {
        Random random = new Random();
        int edgesRemoved = 0;

        while (edgesRemoved < numberOfEdgesToRemove && !edges.isEmpty()) {
            // Select a random edge to remove
            Edge edgeToRemove = edges.get(random.nextInt(edges.size()));

            // Temporarily remove the edge
            edges.remove(edgeToRemove);

            // Check if the graph remains connected
            if (isGraphConnected()) {
                Log.d(TAG, "Removed edge: " + edgeToRemove);
                edgesRemoved++;
            } else {
                // Restore the edge if the graph becomes disconnected
                edges.add(edgeToRemove);
                Log.d(TAG, "Restored edge to maintain connectivity: " + edgeToRemove);
            }
        }
    }

    // Check if the graph is connected
    private boolean isGraphConnected() {
        if (edges.isEmpty()) return false;

        Set<Point> visited = new HashSet<>();
        Map<Point, List<Point>> adjacencyList = buildAdjacencyList(edges);

        // Start traversal from the first node
        Point start = edges.get(0).start;
        traverse(adjacencyList, start, visited);

        // Check if all nodes are visited
        return visited.size() == nodes.size();
    }

    // Build adjacency list from edges
    private Map<Point, List<Point>> buildAdjacencyList(List<Edge> edges) {
        Map<Point, List<Point>> adjacencyList = new HashMap<>();
        for (Edge edge : edges) {
            adjacencyList.computeIfAbsent(edge.start, k -> new ArrayList<>()).add(edge.end);
            adjacencyList.computeIfAbsent(edge.end, k -> new ArrayList<>()).add(edge.start);
        }
        return adjacencyList;
    }

    // Depth-first traversal to visit all nodes
    private void traverse(Map<Point, List<Point>> adjacencyList, Point current, Set<Point> visited) {
        if (visited.contains(current)) return;
        visited.add(current);

        for (Point neighbor : adjacencyList.getOrDefault(current, new ArrayList<>())) {
            traverse(adjacencyList, neighbor, visited);
        }
    }

    private void assignObjects() {

        // Check if the cues are already placed
        if (cuesPlaced) {
            Log.d(TAG, "Cues are already placed, not repositioning.");
            return; // Exit the method if cues are already placed
        }

        // Load preferences
        prefManager = new PreferencesManager(getContext());
        prefManager.TrainingTasks();

        // Get screen dimensions in pixels
        Display display = getActivity().getWindowManager().getDefaultDisplay();
        Point screenSize = new Point();
        display.getSize(screenSize);

        // Define margin from the edges
        int margin = cueSize/2 + 100; // 25// Margin in pixels

        // Calculate usable width and height
        int usableWidth = screenSize.x - (2 * margin); // Width with 75 pixels margin on each side
        int usableHeight = screenSize.y - (2 * margin); // Height with 75 pixels margin on each side

        // Calculate the center offset to place the grid in the middle of the usable area
        //int offsetX = margin;
        //int offsetY = margin;

        // Define minimum and maximum grid scale
        int minGridScale = cueSize + (cueSize/100)*75; // Minimum separation is 200 pixels
        int maxGridScale = Math.min(usableWidth, usableHeight) / 5; // Maximum separation to fit within the usable area

        // Example: Use a dynamic percentage for grid scale (e.g., 50%)
        float scalePercentage = 45; //45; //35; // Can be set dynamically from 0% to 100%
        gridScale = minGridScale + (int) ((maxGridScale - minGridScale) * (scalePercentage / 100f));

        // Define the grid size
        int gridRows = 7;
        int gridCols = 4;

        // Calculate the offset to align the bottom row of the grid 75 pixels from the bottom of the screen
        int gridHeight = gridRows * gridScale;
        int offsetX = (screenSize.x - usableWidth) / 2; // Center horizontally in the usable width
        int offsetY = (screenSize.y - usableHeight)/2; //gridHeight; //200; //220; //315 +350; // Position the bottom row 75 pixels from the bottom

        // Populate the grid with nodes and edges
        populateGrid(gridRows, gridCols, gridScale, offsetX, offsetY, false); // Use false for non-sparse (fully connected)

        // Set the flag to true to indicate that the cues have been placed
        cuesPlaced = true;
    }

    // Load previous trial params
    private void loadTrialParams() {
        settings = PreferenceManager.getDefaultSharedPreferences(getContext());
        boolean prev_trial_correct = settings.getBoolean(preftag_successful_trial, false);
        num_consecutive_corr = settings.getInt(preftag_num_consecutive_corr, 0);
        if (!prev_trial_correct) {
            num_consecutive_corr = 0;
        }

        // Now save values, and they will be overwritten upon correct trial happening
        log_trial_outcome(false);

        Log.d(TAG, "" + num_consecutive_corr + " " + prev_trial_correct);
    }

    private void log_trial_outcome(boolean outcome) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(preftag_successful_trial, outcome);
        editor.putInt(preftag_num_consecutive_corr, num_consecutive_corr);
        editor.commit(); // Use apply() for asynchronous commit
    }

    private void createBlueCircle(Point initialPosition) {
        blueCircle = new View(getContext());
        blueCircle.setBackgroundResource(R.drawable.circle_shape_blue); // Use a blue circle drawable
        //int cueSize = 500;
        blueCircle.setLayoutParams(new ViewGroup.LayoutParams(cueSize, cueSize));
        blueCircle.setX(initialPosition.x - cueSize / 2);
        blueCircle.setY(initialPosition.y - cueSize / 2);
        parentLayout.addView(blueCircle);

        // Initially set the blue circle invisible
        //blueCircle.setVisibility(View.INVISIBLE);
        blueCircle.bringToFront();
        blueCircle.setZ(2f);
    }

    private void animateBlueCircle(Point targetPosition, Runnable onAnimationEnd) {
        // Get the current position of the blue circle
        float startX = blueCircle.getX();
        float startY = blueCircle.getY();

        // Calculate the target position
        float targetX = targetPosition.x - blueCircle.getWidth() / 2;
        float targetY = targetPosition.y - blueCircle.getHeight() / 2;

        // Use ObjectAnimator to animate the X and Y properties of the blue circle
        ObjectAnimator animatorX = ObjectAnimator.ofFloat(blueCircle, "x", startX, targetX);
        ObjectAnimator animatorY = ObjectAnimator.ofFloat(blueCircle, "y", startY, targetY);

        // Set animation duration and play both animations together
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setDuration(moveDuration);  // Set duration
        animatorSet.playTogether(animatorX, animatorY);

        // Set a listener to execute the code when the animation ends
        animatorSet.addListener(new AnimatorSet.AnimatorListener() {

            @Override
            public void onAnimationStart(Animator animation) {}

            @Override
            public void onAnimationEnd(Animator animation) {

                // Run the code passed in as onAnimationEnd
                if (onAnimationEnd != null) {
                    onAnimationEnd.run();
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {}

            @Override
            public void onAnimationRepeat(Animator animation) {}
        });

        // Start the animation
        animatorSet.start();

        // Highlight valid action nodes and goal
        highlightNodes(targetPosition, goal);

        // Make the blue circle visible if it's not already
        //blueCircle.setVisibility(View.VISIBLE);
    }

    // Method to give reward and end the trial
    private void giveRewardAndEndTrial() {
        new SoundManager(prefManager).playTone();
        RewardSystem.activateChannel(latestRewardChannel, PreferencesManager.rewardduration);
        l_rewgiven += PreferencesManager.rewardduration;

        // log reward duration given
        logEvent(" 'Rewardduration'" + PreferencesManager.rewardduration, callback);

        // Log and Clear occupiedPositions
        Log.d(TAG, "occupiedPositions" + occupiedPositions);
        occupiedPositions.clear();

        handler.postDelayed(() -> {
            callback.resetTimer_();
            callback.takePhotoFromTask_();

            num_consecutive_corr += 1;
            logEvent("num_consecutive_corr " + num_consecutive_corr, callback);
            log_trial_outcome(true);

            showInterTrialInterval(1000);
            startTrial();

        }, 500); // 500 milliseconds delay to show color change
    }

    // Implement interface and listener to enable communication up to TaskManager
    TaskInterface callback;

    public void setFragInterfaceListener(TaskInterface callback) {
        this.callback = callback;
    }
}
