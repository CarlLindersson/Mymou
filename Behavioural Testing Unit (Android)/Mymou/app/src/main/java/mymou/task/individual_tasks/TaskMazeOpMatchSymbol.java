package mymou.task.individual_tasks;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.animation.AnimatorListenerAdapter;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import mymou.R;
import mymou.Utils.SoundManager;
import mymou.preferences.PreferencesManager;
import mymou.task.backend.RewardSystem;
import mymou.task.backend.TaskInterface;
import mymou.task.backend.UtilsTask;

public class TaskMazeOpMatchSymbol extends Task {

    // Debug
    public static String TAG = "TaskMazeOpMatchSymbol";

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
    // private float transparencyLevel = .6f; // Example: 0.8 for 80% visible

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
    private int cueSize = 250; //200;

    // Pulse animation settings
    private float nodeAlphaLevel = 1.0f; // 0.4 -> 0.6 -> 0.8 -> 1.0
    private int pulsePeriodDuration = 250;
    private boolean player_node_pulse = false;

    // Non-valid node transparency
    private boolean non_valid_node_transparant = false;
    private float non_valid_node_transparency_level = 1.0f; // increase by 1.25 (0.5->0.625->0.75->0.875->1)

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

    // List of shapes
    List<Integer> shapeDrawables = Arrays.asList(
            R.drawable.circle_green,
            R.drawable.square_green,
            R.drawable.star_green
            //   R.drawable.triangle_green
    );

    List<Integer> shapeDrawablesPlayer = Arrays.asList(
            R.drawable.circle_blue,
            R.drawable.square_blue,
            R.drawable.star_blue
            //   R.drawable.triangle_green
    );

    // Player symbol
    public float playerSymbolScale;
    public int playerShapeInt;

    // Goal symbol
    public float goalSymbolScale;
    public int goalShapeInt;

    // Wrong goal
    private Point wrongPoint;

    // Sets for book keeping
    private final Set<Point> goals = new HashSet<>();
    private final Set<Point> correctGoals = new HashSet<>();
    private final Set<Point> wrongGoals = new HashSet<>();

    // Operation nodes
    public Point swapSize;
    public Point swapShape;

    // Grid fields
    private int gridOffsetX, gridOffsetY;


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

        // Wait for some duration before starting the next trial
        handler.postDelayed(this::startTrial, duration);
    }

    // Method to build grid positions (pixel position, not point index)
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

                // If goal node, and symbols match, give reward and end trial
                if (position == goal && playerSymbolScale == goalSymbolScale && playerShapeInt == goalShapeInt) {
                    // Set nodes to non-clickable
                    for (Button n : nodes.values()) {
                        n.setClickable(false);
                    }
                    giveRewardAndEndTrial();
                    stopPulsation(blueCircle);
                } else if (position == goal) {
                    // Set nodes to non-clickable
                    for (Button n : nodes.values()) {
                        n.setClickable(false);
                    }
                    // Delay briefly before triggering the shake animation
                    handler.postDelayed(() -> {
                        shakeScreen(parentLayout);

                    }, 150); // Short delay *before* shake

                    handler.postDelayed(() -> {
                        callback.resetTimer_();
                        callback.takePhotoFromTask_();
                        // Loads previous trial params to keep track of num_consecutive_corr.
                        loadTrialParams();
                        num_consecutive_corr = 0;
                        logEvent("num_consecutive_corr " + num_consecutive_corr, callback);
                        if (playerSymbolScale != goalSymbolScale && playerShapeInt != goalShapeInt){
                            logEvent("Fail - Wrong_Scale_and_Shape", callback);
                        }else if (playerSymbolScale != goalSymbolScale) {
                            logEvent("Fail - Wrong_Scale", callback);
                        }else if (playerShapeInt != goalShapeInt){
                            logEvent("Fail - Wrong_Shape", callback);
                        }

                        log_trial_outcome(true);
                        callback.commitTrialDataFromTask_("FAIL_TRIAL");

                        // starting ITI
                        showInterTrialInterval(1500);

                    }, 500);
                }
                else if (position == wrongPoint) {

                    // Delay briefly before triggering the shake animation
                    handler.postDelayed(() -> {
                        shakeScreen(parentLayout);

                    }, 150); // Short delay *before* shake
                }
            }
            else {
                shakeScreen(parentLayout, 2);
                //shakeScreenTwice(parentLayout);
                //shakeScreen(parentLayout);

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
        float currentScaleX = node.getScaleX(); // e.g., 0.5f or 1.0f
        float currentScaleY = node.getScaleY();

        float targetScaleX = currentScaleX * 1.5f; // scale up 50%
        float targetScaleY = currentScaleY * 1.5f;

        // Animate up
        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(node, "scaleX", currentScaleX, targetScaleX);
        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(node, "scaleY", currentScaleY, targetScaleY);

        // Animate down (return to current size, not 1.0)
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(node, "scaleX", targetScaleX, currentScaleX);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(node, "scaleY", targetScaleY, currentScaleY);

        scaleUpX.setDuration(100);
        scaleUpY.setDuration(100);
        scaleDownX.setDuration(100);
        scaleDownY.setDuration(100);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(scaleUpX).with(scaleUpY);
        animatorSet.play(scaleDownX).with(scaleDownY).after(scaleUpX);
        animatorSet.start();
    }

    /**
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
     */

    // Shake animation method for parentLayout
    public void shakeScreen(View parentLayout) {
        // Create translation animations for the X-axis
        ObjectAnimator moveRight = ObjectAnimator.ofFloat(parentLayout, "translationX",
                0f, 225f); //0f, 25f
        ObjectAnimator moveLeft = ObjectAnimator.ofFloat(parentLayout, "translationX",
                225f, -225f); // 25f, -25f
        ObjectAnimator moveBack = ObjectAnimator.ofFloat(parentLayout, "translationX",
                -225f, 10f); // -25f, 0f

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

    public void shakeScreenTwice(View parentLayout) {
        // Create translation animations for the X-axis
        ObjectAnimator moveRight = ObjectAnimator.ofFloat(parentLayout, "translationX", 0f, 225f);
        ObjectAnimator moveLeft = ObjectAnimator.ofFloat(parentLayout, "translationX", 225f, -225f);
        ObjectAnimator moveBack = ObjectAnimator.ofFloat(parentLayout, "translationX", -225f, 10f);

        // Set duration for each movement
        moveRight.setDuration(50);
        moveLeft.setDuration(50);
        moveBack.setDuration(50);

        // First animation set
        AnimatorSet firstShake = new AnimatorSet();
        firstShake.playSequentially(moveRight, moveLeft, moveBack);

        // Second animation set (clone of the first)
        ObjectAnimator moveRight2 = ObjectAnimator.ofFloat(parentLayout, "translationX", 10f, 225f);
        ObjectAnimator moveLeft2 = ObjectAnimator.ofFloat(parentLayout, "translationX", 225f, -225f);
        ObjectAnimator moveBack2 = ObjectAnimator.ofFloat(parentLayout, "translationX", -225f, 0f);
        moveRight2.setDuration(50);
        moveLeft2.setDuration(50);
        moveBack2.setDuration(50);
        AnimatorSet secondShake = new AnimatorSet();
        secondShake.playSequentially(moveRight2, moveLeft2, moveBack2);

        // Start second shake after first ends
        firstShake.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                secondShake.start();
            }
        });

        firstShake.start();
    }

    public void shakeScreen(View parentLayout, int shakeCount) {
        int duration = 50; // total time per shakeCount is duration * 3 * shakeCount
        float amplitude = 225f;

        List<Animator> allAnimations = new ArrayList<>();

        float start = 0f;

        for (int i = 0; i < shakeCount; i++) {
            float end = (i == shakeCount - 1) ? 0f : 10f;

            ObjectAnimator moveRight = ObjectAnimator.ofFloat(parentLayout, "translationX", start, amplitude);
            ObjectAnimator moveLeft = ObjectAnimator.ofFloat(parentLayout, "translationX", amplitude, -amplitude);
            ObjectAnimator moveBack = ObjectAnimator.ofFloat(parentLayout, "translationX", -amplitude, end);

            moveRight.setDuration(duration);
            moveLeft.setDuration(duration);
            moveBack.setDuration(duration);

            allAnimations.add(moveRight);
            allAnimations.add(moveLeft);
            allAnimations.add(moveBack);

            start = end;  // Use 10f as the next start to keep motion continuous
        }

        AnimatorSet fullShake = new AnimatorSet();
        fullShake.playSequentially(allAnimations);
        fullShake.start();
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
        alphaAnimation.setDuration(pulsePeriodDuration);  // Duration of each fade in/out cycle
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
        edge.setBackgroundResource(android.R.color.darker_gray); //android.R.color.darker_gray
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
        private final int gridScale;
        private final int offsetX;
        private final int offsetY;

        public Edge(Point start, Point end, int gridScale, int offsetX, int offsetY) {
            this.start = start;
            this.end = end;
            this.gridScale = gridScale;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
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

        /*@Override
        public String toString() {
            return "((" + (start.y / gridScale) + "," + (start.x / gridScale) + ") - ("
                    + (end.y / gridScale) + "," + (end.x / gridScale) + "))";
        }*/

        @Override
        public String toString() {
            int startRow = (start.y - offsetY) / gridScale;
            int startCol = (start.x - offsetX) / gridScale;
            int endRow = (end.y - offsetY) / gridScale;
            int endCol = (end.x - offsetX) / gridScale;

            return "((" + startRow + "," + startCol + ") - (" + endRow + "," + endCol + "))";
        }
    }

    public void setGoalState() {
        Random random = new Random();

        // Simulate starting values
        int simulatedShape = playerShapeInt;
        float simulatedSize = playerSymbolScale;

        // Decide which transformation path to simulate
        int pathChoice = random.nextInt(2);  // 0 = shape, 1 = size, 2 = both

        switch (pathChoice) {
            case 0:
                do {
                    simulatedShape = random.nextInt(shapeDrawables.size());
                } while (simulatedShape == playerShapeInt);
                break;
            case 1:
                simulatedSize = (simulatedSize == 1.0f) ? 0.5f : 1.0f;
                break;
            case 2:
                simulatedSize = (simulatedSize == 1.0f) ? 0.5f : 1.0f;
                do {
                    simulatedShape = random.nextInt(shapeDrawables.size());
                } while (simulatedShape == playerShapeInt);
                break;
        }

        goalShapeInt = simulatedShape;
        goalSymbolScale = simulatedSize;

        // Log goal and player symbol dimensions
        logEvent("PlayerStartScale: " + playerSymbolScale, callback);
        logEvent("PlayerStartShape: " + playerShapeInt, callback);
        logEvent("GoalScale: " + goalSymbolScale, callback);
        logEvent("GoalShape: " + goalShapeInt, callback);

        // Step 1: Create background (white) node
        Button whiteBackground = createNode(
                goal,
                R.drawable.circle_shape_white,
                null, true, false, false,
                goal.x - cueSize / 2,
                goal.y - cueSize / 2,
                gridScale
        );
        whiteBackground.setZ(0f);  // send to back

        // Step 2: Create actual goal node on top (with colored shape)
        Button goalOverlay = createNode(
                goal,
                shapeDrawables.get(goalShapeInt),
                null, true, true, false,
                goal.x - cueSize / 2,
                goal.y - cueSize / 2,
                gridScale
        );
        goalOverlay.setScaleX(goalSymbolScale);
        goalOverlay.setScaleY(goalSymbolScale);
        goalOverlay.setZ(1f);  // above white background

        // Replace map entry (so only the top layer is tracked in nodes map)
        nodes.put(goal, goalOverlay);
    }


    /*public void populateGrid(int rows, int cols, int gridScale, int offsetX, int offsetY,
                             boolean sparse) {

        // Build the grid positions
        List<Point> positions = buildGrid(rows, cols, gridScale, offsetX, offsetY);

        // Create nodes for each grid position
        for (Point position : positions) {
            Button node = createNode(position, R.drawable.circle_shape_white, // circle_shape_white,
                    null, true, true, false,
                    offsetX, offsetY, gridScale);
            nodes.put(position, node);

        }

        // Select random start and goal positions
        boolean diagonal = true;
        Point start;
        Random rand = new Random();
        Point p00 = positions.get(0); // top-left
        Point p10 = positions.get(1); // top-right
        Point p01 = positions.get(2); // bottom-left
        Point p11 = positions.get(3); // bottom-right
        if (diagonal) {
            // Pick one diagonal pair
            Point[] pair = rand.nextInt(2) == 0
                    ? new Point[]{p00, p11}
                    : new Point[]{p10, p01};

            // Randomly assign which is start vs goal
            if (rand.nextBoolean()) {
                start = pair[0];
                goal  = pair[1];
            } else {
                start = pair[1];
                goal  = pair[0];
            }
        } else {
            start = positions.get(rand.nextInt(positions.size()));
            do {
                goal = positions.get(rand.nextInt(positions.size()));
            } while (goal.equals(start));
        }

        // Highlight valid action nodes and goal
        highlightNodes(currentPosition, goal);

        // Highlight start and goal nodes
        nodes.get(start).setBackgroundResource(R.drawable.circle_shape_white);

        // Set current position to start node and set blue circle to start position.
        currentPosition = start;
        createBlueCircle(start);

        // Select swap_size position (not equal to start or goal)
        do {
            swapSize = positions.get(rand.nextInt(positions.size()));
        } while (swapSize.equals(start) || swapSize.equals(goal));

        nodes.get(swapSize).setBackgroundResource(R.drawable.circle_cyan);

        // Select swap_shape position (not equal to start, goal, or swap_size)
        do {
            swapShape = positions.get(rand.nextInt(positions.size()));
        } while (swapShape.equals(start) || swapShape.equals(goal) || swapShape.equals(swapSize));

        nodes.get(swapShape).setBackgroundResource(R.drawable.circle_red);

        // Randomly draw a shape from list of possible goal states
        setGoalState();

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
            edges = generateSpanningTree(positions, gridScale, offsetX, offsetY);
        }
        else {
            // Connect each node to its immediate neighbors
            edges = generateFullyConnectedEdges(positions, gridScale, offsetX, offsetY);
            // Remove 7 random edges while ensuring connectivity
            removeRandomEdges(0);
        }
        // Create the visual edges in the grid
        for (Edge edge : edges) {
            createEdge(edge.start, edge.end);
        }
        logEvent("edge list:" + edges.toString(), callback);

        // Highlight valid action nodes and goal
        //highlightNodes(currentPosition, goal);
    }*/

    private static class GoalState {
        int shapeIdx;
        float scale; // 1.0f or 0.5f
        GoalState(int s, float sc){ shapeIdx = s; scale = sc; }
    }

    private GoalState transformedFromPlayer() {
        Random random = new Random();

        int simShape = playerShapeInt;
        float simSize = playerSymbolScale;

        int pathChoice = random.nextInt(2); // 0 = shape, 1 = size (keep your old range/logic)
        switch (pathChoice) {
            case 0:
                do { simShape = random.nextInt(shapeDrawables.size()); }
                while (simShape == playerShapeInt);
                break;
            case 1:
                simSize = (simSize == 1.0f) ? 0.5f : 1.0f;
                break;
            // If you want "both" sometimes, change to random.nextInt(3) and add case 2
        }
        return new GoalState(simShape, simSize);
    }

    private void paintGoal(Point where, GoalState gs) {
        // Background (non-clickable)
        Button bg = createNode(
                where,
                R.drawable.circle_shape_white,
                null, /*visible*/ true, /*clickable*/ false, /*pulsate*/ false,
                gridOffsetX, gridOffsetY, gridScale     // ✅ use grid origin
        );
        bg.setZ(0f);

        // Overlay (clickable)
        Button overlay = createNode(
                where,
                shapeDrawables.get(gs.shapeIdx),
                null, /*visible*/ true, /*clickable*/ true, /*pulsate*/ false,
                gridOffsetX, gridOffsetY, gridScale     // ✅ use grid origin
        );
        overlay.setScaleX(gs.scale);
        overlay.setScaleY(gs.scale);
        overlay.setZ(1f);

        // Track the top layer as the node for that position
        nodes.put(where, overlay);
    }

    private void setDualGoalStates(Point goalA, Point goalB) {
        // correct = exactly like player
        GoalState correct = new GoalState(playerShapeInt, playerSymbolScale);
        // wrong = transformed from player using your rule
        GoalState wrong = transformedFromPlayer();

        // Randomize which goal is correct vs wrong
        Random r = new Random();
        Point correctPoint = r.nextBoolean() ? goalA : goalB;
        wrongPoint   = (correctPoint == goalA) ? goalB : goalA;

        // Paint them
        paintGoal(correctPoint, correct);
        paintGoal(wrongPoint, wrong);

        // Keep compatibility vars if other code reads them
        goal = correctPoint;                  // "goal" = correct one
        goalShapeInt = correct.shapeIdx;
        goalSymbolScale = correct.scale;

        // Book-keeping sets
        goals.add(correctPoint);
        goals.add(wrongPoint);
        correctGoals.add(correctPoint);
        wrongGoals.add(wrongPoint);

        // Logs
        logEvent("PlayerStartScale: " + playerSymbolScale, callback);
        logEvent("PlayerStartShape: " + playerShapeInt, callback);
        logEvent("CorrectGoalScale: " + correct.scale, callback);
        logEvent("CorrectGoalShape: " + correct.shapeIdx, callback);
        logEvent("WrongGoalScale: " + wrong.scale, callback);
        logEvent("WrongGoalShape: " + wrong.shapeIdx, callback);
    }


    // Row/col -> pixel Point
    private Point rcToPoint(int r, int c, int offsetX, int offsetY, int gridScale) {
        return new Point(offsetX + c * gridScale, offsetY + r * gridScale);
    }

    private boolean inBounds(int r, int c, int rows, int cols) {
        return r >= 0 && r < rows && c >= 0 && c < cols;
    }

    // Opposite direction? (for the "no backward" rule)
    private boolean isOpposite(int[] a, int[] b) {
        return a[0] == -b[0] && a[1] == -b[1];
    }

    public void populateGrid(int rows, int cols, int gridScale, int offsetX, int offsetY, boolean sparse) {
        // We will ONLY create nodes that are part of the structure (start + two arms).
        nodes.clear();
        edges.clear();
        occupiedPositions.clear();
        goals.clear();
        correctGoals.clear();
        wrongGoals.clear();

        final int ARM_LEN = 4; // length of each arm

        Random rand = new Random();

        // --- 1) Random start (r,c) ---
        int startR = rand.nextInt(rows);
        int startC = rand.nextInt(cols);
        Point start = rcToPoint(startR, startC, offsetX, offsetY, gridScale);

        // Cardinal directions
        int[][] DIRS = new int[][]{{1,0},{-1,0},{0,1},{0,-1}};

        // Utility to make a "r,c" key
        java.util.function.BiFunction<Integer,Integer,String> key = (r,c) -> r + "," + c;

        // Helper to build an arm (length = ARM_LEN) without backtracking and without overlap
        java.util.function.BiFunction<Set<String>, List<int[]>, List<Point>> buildArm = (forbidden, allowedFirstDirs) -> {
            // try each first direction in random order
            List<int[]> firstDirs = new ArrayList<>(allowedFirstDirs);
            Collections.shuffle(firstDirs, rand);

            for (int[] d0 : firstDirs) {
                // start of the arm (step 1)
                int r = startR + d0[0], c = startC + d0[1];
                if (r < 0 || r >= rows || c < 0 || c >= cols) continue;
                if (forbidden.contains(key.apply(r,c))) continue;

                List<Point> arm = new ArrayList<>();
                Set<String> localUsed = new HashSet<>(forbidden); // track within this attempt
                localUsed.add(key.apply(r,c));
                arm.add(rcToPoint(r, c, offsetX, offsetY, gridScale));

                int prevDr = d0[0], prevDc = d0[1];

                // extend to required length
                for (int step = 2; step <= ARM_LEN; step++) {
                    // candidate directions shuffled
                    List<int[]> options = new ArrayList<>(Arrays.asList(DIRS));
                    Collections.shuffle(options, rand);

                    boolean placed = false;
                    for (int[] d : options) {
                        // no backtracking
                        if (d[0] == -prevDr && d[1] == -prevDc) continue;

                        int nr = r + d[0], nc = c + d[1];
                        if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;

                        String k = key.apply(nr, nc);
                        if (localUsed.contains(k)) continue;

                        // accept
                        r = nr; c = nc;
                        prevDr = d[0]; prevDc = d[1];
                        localUsed.add(k);
                        arm.add(rcToPoint(r, c, offsetX, offsetY, gridScale));
                        placed = true;
                        break;
                    }
                    if (!placed) {
                        // dead end with this initial direction; abandon and try another first dir
                        arm = null;
                        break;
                    }
                }

                if (arm != null && arm.size() == ARM_LEN) {
                    return arm;
                }
            }
            return null; // unable to place an arm
        };

        // First arm
        Set<String> used = new HashSet<>();
        used.add(key.apply(startR, startC));
        List<int[]> allDirs = Arrays.asList(DIRS);
        List<Point> arm1 = buildArm.apply(used, allDirs);
        if (arm1 == null) { populateGrid(rows, cols, gridScale, offsetX, offsetY, false); return; }

        // mark used cells from arm1
        for (Point p : arm1) {
            int rr = (p.y - offsetY) / gridScale;
            int cc = (p.x - offsetX) / gridScale;
            used.add(key.apply(rr, cc));
        }
        Point goal1 = arm1.get(ARM_LEN - 1);

        // Second arm (cannot overlap arm1; start can be shared)
        List<Point> arm2 = buildArm.apply(used, allDirs);
        if (arm2 == null) { populateGrid(rows, cols, gridScale, offsetX, offsetY, false); return; }
        Point goal2 = arm2.get(ARM_LEN - 1);

        // --- Create ONLY the connected nodes (start + steps + goals) ---
        Button nStart = createNode(start, R.drawable.circle_shape_white, null, true, true, false, offsetX, offsetY, gridScale);
        nodes.put(start, nStart);

        // arm1 nodes
        for (Point p : arm1) {
            Button nb = createNode(p, R.drawable.circle_shape_white, null, true, true, false, offsetX, offsetY, gridScale);
            nodes.put(p, nb);
        }
        // arm2 nodes
        for (Point p : arm2) {
            Button nb = createNode(p, R.drawable.circle_shape_white, null, true, true, false, offsetX, offsetY, gridScale);
            nodes.put(p, nb);
        }

        // --- Edges along arms only ---
        // start -> first step of each arm
        edges.add(new Edge(start, arm1.get(0), gridScale, offsetX, offsetY));
        edges.add(new Edge(start, arm2.get(0), gridScale, offsetX, offsetY));
        // consecutive steps within each arm
        for (int i = 0; i < ARM_LEN - 1; i++) {
            edges.add(new Edge(arm1.get(i), arm1.get(i+1), gridScale, offsetX, offsetY));
            edges.add(new Edge(arm2.get(i), arm2.get(i+1), gridScale, offsetX, offsetY));
        }
        for (Edge e : edges) createEdge(e.start, e.end);

        // --- Player at start ---
        currentPosition = start;
        createBlueCircle(start);

        // --- Dual goal states: one correct (same as player), one wrong (transformed) ---
        setDualGoalStates(goal1, goal2);

        // Logs (unchanged)
        int g1r = (goal1.y - gridOffsetY) / gridScale;
        int g1c = (goal1.x - gridOffsetX) / gridScale;
        int g2r = (goal2.y - gridOffsetY) / gridScale;
        int g2c = (goal2.x - gridOffsetX) / gridScale;

        logEvent("startNode:" + ((start.y - gridOffsetY)/gridScale) + "," + ((start.x - gridOffsetX)/gridScale), callback);
        logEvent("goalNode1:" + g1r + "," + g1c, callback);
        logEvent("goalNode2:" + g2r + "," + g2c, callback);

        boolean goal1Correct = correctGoals.contains(goal1);
        logEvent("goal1Type:" + (goal1Correct ? "CORRECT" : "WRONG"), callback);
        logEvent("goal2Type:" + (goal1Correct ? "WRONG" : "CORRECT"), callback);
    }

    /*
    public void populateGrid(int rows, int cols, int gridScale, int offsetX, int offsetY, boolean sparse) {
        // We will ONLY create nodes that are part of the structure (start + two 2-step arms).
        nodes.clear();
        edges.clear();
        occupiedPositions.clear();
        goals.clear();
        correctGoals.clear();
        wrongGoals.clear();

        Random rand = new Random();

        // --- 1) Random start (r,c) ---
        int startR = rand.nextInt(rows);
        int startC = rand.nextInt(cols);
        Point start = rcToPoint(startR, startC, offsetX, offsetY, gridScale);

        // Cardinal directions
        int[][] DIRS = new int[][]{{1,0},{-1,0},{0,1},{0,-1}};

        // Helper to build an arm (length 2) without backtracking and without overlap
        java.util.function.BiFunction<Set<String>, List<int[]>, List<Point>> buildArm = (forbidden, allowedFirstDirs) -> {
            List<int[]> firstDirs = new ArrayList<>(allowedFirstDirs);
            Collections.shuffle(firstDirs, rand);

            for (int[] d1 : firstDirs) {
                int r1 = startR + d1[0], c1 = startC + d1[1];
                if (r1 < 0 || r1 >= rows || c1 < 0 || c1 >= cols) continue;
                String k1 = r1 + "," + c1;
                if (forbidden.contains(k1)) continue;

                List<int[]> secondDirs = new ArrayList<>(Arrays.asList(DIRS));
                Collections.shuffle(secondDirs, rand);
                for (int[] d2 : secondDirs) {
                    if (d2[0] == -d1[0] && d2[1] == -d1[1]) continue; // no backtracking
                    int r2 = r1 + d2[0], c2 = c1 + d2[1];
                    if (r2 < 0 || r2 >= rows || c2 < 0 || c2 >= cols) continue;
                    String k2 = r2 + "," + c2;
                    if (forbidden.contains(k2)) continue;

                    Point step1 = rcToPoint(r1, c1, offsetX, offsetY, gridScale);
                    Point step2 = rcToPoint(r2, c2, offsetX, offsetY, gridScale);
                    return Arrays.asList(step1, step2);
                }
            }
            return null;
        };

        // First arm
        Set<String> used = new HashSet<>();
        used.add(startR + "," + startC);
        List<int[]> allDirs = Arrays.asList(DIRS);
        List<Point> arm1 = buildArm.apply(used, allDirs);
        if (arm1 == null) { populateGrid(rows, cols, gridScale, offsetX, offsetY, false); return; }

        Point a1s1 = arm1.get(0), a1s2 = arm1.get(1);
        used.add(((a1s1.y - offsetY) / gridScale) + "," + ((a1s1.x - offsetX) / gridScale));
        used.add(((a1s2.y - offsetY) / gridScale) + "," + ((a1s2.x - offsetX) / gridScale));
        Point goal1 = a1s2;

        // Second arm (cannot overlap arm1; start can be shared)
        List<Point> arm2 = buildArm.apply(used, allDirs);
        if (arm2 == null) { populateGrid(rows, cols, gridScale, offsetX, offsetY, false); return; }

        Point a2s1 = arm2.get(0), a2s2 = arm2.get(1);
        Point goal2 = a2s2;

        // --- Create ONLY the connected nodes (start + both steps + both goals) ---
        Button nStart = createNode(start, R.drawable.circle_shape_white, null, true, true, false, offsetX, offsetY, gridScale);
        nodes.put(start, nStart);

        Button nA1S1 = createNode(a1s1, R.drawable.circle_shape_white, null, true, true, false, offsetX, offsetY, gridScale);
        nodes.put(a1s1, nA1S1);
        Button nGoal1 = createNode(goal1, R.drawable.circle_shape_white, null, true, true, false, offsetX, offsetY, gridScale);
        nodes.put(goal1, nGoal1);

        Button nA2S1 = createNode(a2s1, R.drawable.circle_shape_white, null, true, true, false, offsetX, offsetY, gridScale);
        nodes.put(a2s1, nA2S1);
        Button nGoal2 = createNode(goal2, R.drawable.circle_shape_white, null, true, true, false, offsetX, offsetY, gridScale);
        nodes.put(goal2, nGoal2);

        // --- Edges along arms only ---
        edges.add(new Edge(start, a1s1, gridScale, offsetX, offsetY));
        edges.add(new Edge(a1s1, goal1, gridScale, offsetX, offsetY));
        edges.add(new Edge(start, a2s1, gridScale, offsetX, offsetY));
        edges.add(new Edge(a2s1, goal2, gridScale, offsetX, offsetY));
        for (Edge e : edges) createEdge(e.start, e.end);

        // --- Player at start ---
        currentPosition = start;
        createBlueCircle(start);

        // --- Dual goal states: one correct (same as player), one wrong (transformed) ---
        setDualGoalStates(goal1, goal2);

        // Logs
        // Coordinates
        int g1r = (goal1.y - gridOffsetY) / gridScale;
        int g1c = (goal1.x - gridOffsetX) / gridScale;
        int g2r = (goal2.y - gridOffsetY) / gridScale;
        int g2c = (goal2.x - gridOffsetX) / gridScale;

        // Log positions as before
        logEvent("startNode:" + ((start.y - gridOffsetY)/gridScale) + "," + ((start.x - gridOffsetX)/gridScale), callback);
        logEvent("goalNode1:" + g1r + "," + g1c, callback);
        logEvent("goalNode2:" + g2r + "," + g2c, callback);

        // NEW: log which is correct vs wrong
        boolean goal1Correct = correctGoals.contains(goal1);
        logEvent("goal1Type:" + (goal1Correct ? "CORRECT" : "WRONG"), callback);
        logEvent("goal2Type:" + (goal1Correct ? "WRONG" : "CORRECT"), callback);
        // Nothing else is created => everything not connected is effectively hidden.
    }
    */


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
                    //float transparencyLevel = 0.5f; //1.0f; //0.5f + (num_consecutive_corr / 10.0f) * 0.5f;
                    //transparencyLevel = Math.min(transparencyLevel, 1.0f);

                    //node.setAlpha(transparencyLevel);
                    if (non_valid_node_transparant) {
                        node.setAlpha(non_valid_node_transparency_level); // Non-action nodes are slightly transparent
                    }
                }
            }
            nodes.get(goal).setAlpha(1);
        }
    }

    // Generate edges to fully connect all neighboring nodes
    private List<Edge> generateFullyConnectedEdges(List<Point> positions, int gridScale, int offsetX, int offsetY) {
        List<Edge> edges = new ArrayList<>();
        Set<Point> visited = new HashSet<>();

        for (Point current : positions) {
            for (Point neighbor : getNeighbors(current, positions, gridScale)) {
                if (!visited.contains(neighbor)) {
                    edges.add(new Edge(current, neighbor, gridScale, offsetX, offsetY));
                }
            }
            visited.add(current);
        }

        return edges;
    }

    // Generate a randomized spanning tree for sparse connectivity
    private List<Edge> generateSpanningTree(List<Point> positions, int gridScale, int offsetX, int offsetY) {
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
                    edges.add(new Edge(current, neighbor, gridScale, offsetX, offsetY));
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
        int minGridScale = cueSize + (cueSize/100)*100; // Minimum separation is 200 pixels
        int maxGridScale = Math.min(usableWidth, usableHeight) / 5; // Maximum separation to fit within the usable area

        // Example: Use a dynamic percentage for grid scale (e.g., 50%)
        float scalePercentage = 50; //45; //35; // Can be set dynamically from 0% to 100%
        gridScale = minGridScale + (int) ((maxGridScale - minGridScale) * (scalePercentage / 100f));

        // Define the grid size
        int gridRows = 4;
        int gridCols = 4;

        // Calculate the offset to align the bottom row of the grid 75 pixels from the bottom of the screen
        int gridHeight = gridRows * gridScale;
        int offsetX = (screenSize.x - usableWidth) / 2 - 75; // Center horizontally in the usable width
        int offsetY = (screenSize.y - usableHeight)/2 + 250 ; //gridHeight; //200; //220; //315 +350; // Position the bottom row 75 pixels from the bottom

        // Assign to field
        gridOffsetX = offsetX;
        gridOffsetY = offsetY;

        // Populate the grid with nodes and edges
       // populateGrid(gridRows, gridCols, gridScale, offsetX, offsetY, false); // Use false for non-sparse (fully connected)
        populateGrid(gridRows, gridCols, gridScale, gridOffsetX, gridOffsetY, false);

        // Set the flag to true to indicate that the cues have been placed
        cuesPlaced = true;

        // Log that the maze is now visible
        logEvent("MazeVisible", callback);

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

        //blueCircle.setBackgroundResource(R.drawable.circle_shape_blue); // Use a blue circle drawable

        // Randomly draw a shape from list
        Random random = new Random();
        playerShapeInt = random.nextInt(shapeDrawablesPlayer.size());
        int randomDrawable = shapeDrawablesPlayer.get(playerShapeInt);
        blueCircle.setBackgroundResource(randomDrawable);

        // Randomly set goal node size
        playerSymbolScale = random.nextBoolean() ? 1.0f : 0.5f;
        blueCircle.setScaleX(playerSymbolScale);
        blueCircle.setScaleY(playerSymbolScale);

        //int cueSize = 500;
        blueCircle.setLayoutParams(new ViewGroup.LayoutParams(cueSize, cueSize));
        blueCircle.setX(initialPosition.x - cueSize / 2);
        blueCircle.setY(initialPosition.y - cueSize / 2);
        parentLayout.addView(blueCircle);

        // Initially set the blue circle invisible
        //blueCircle.setVisibility(View.INVISIBLE);
        blueCircle.bringToFront();
        blueCircle.setZ(2f);

        // Make the player node blink / pulsate
        if (player_node_pulse) {
            startAlphaPulse(blueCircle);
        }

    }

    private void checkAndSwapSize(Point position, Point swapSize) {
        if (swapSize != null && position.equals(swapSize)) {
            // Toggle symbol scale
            playerSymbolScale = (playerSymbolScale == 1.0f) ? 0.5f : 1.0f;
            blueCircle.setScaleX(playerSymbolScale);
            blueCircle.setScaleY(playerSymbolScale);
            logEvent("SizeSwappedTo: " + playerSymbolScale, callback);
            // Consume the operational node
            Button node = nodes.get(swapSize);
            if (node != null) {
                node.setAlpha(1f);
                //node.setEnabled(false);
                node.setBackgroundResource(R.drawable.circle_shape_white);
            }
            this.swapSize = null;
        }
    }

    private void checkAndSwapShape(Point position, Point swapShape) {
        if (swapShape != null && position.equals(swapShape)) {
            // Swap to a new shape
            Random rand = new Random();
            int newShape;
            do {
                newShape = rand.nextInt(shapeDrawablesPlayer.size());
            } while (newShape == playerShapeInt);

            playerShapeInt = newShape;
            logEvent("ShapeSwappedTo: " + newShape, callback);
            int drawable = shapeDrawablesPlayer.get(playerShapeInt);
            blueCircle.setBackgroundResource(drawable);

            // Consume the operational node
            Button node = nodes.get(swapShape);
            if (node != null) {
                node.setAlpha(1f); // dim it
                //node.setEnabled(false); // disable interaction
                node.setBackgroundResource(R.drawable.circle_shape_white); // reset look (optional)
            }
            this.swapShape = null; // remove reference
        }
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

                // Update current position
                currentPosition = targetPosition;

                // Now perform swap logic
                checkAndSwapShape(currentPosition, swapShape);
                checkAndSwapSize(currentPosition, swapSize);
            }

            @Override
            public void onAnimationCancel(Animator animation) {}

            @Override
            public void onAnimationRepeat(Animator animation) {}
        });

        // Start the animation
        animatorSet.start();

        // Highlight valid action nodes and goal
        //highlightNodes(targetPosition, goal);

        // Make the blue circle visible if it's not already
        //blueCircle.setVisibility(View.VISIBLE);
    }


    // Method to give reward and end the trial
    private void giveRewardAndEndTrial() {
        new SoundManager(prefManager).playTone();
        RewardSystem.activateChannel(latestRewardChannel, PreferencesManager.rewardduration);
        l_rewgiven += PreferencesManager.rewardduration;

        // log reward duration given
        logEvent("Rewardduration " + PreferencesManager.rewardduration, callback);

        // Log and Clear occupiedPositions
        Log.d(TAG, "occupiedPositions " + occupiedPositions);
        occupiedPositions.clear();

        handler.postDelayed(() -> {
            callback.resetTimer_();
            callback.takePhotoFromTask_();
            // Loads previous trial params to keep track of num_consecutive_corr.
            loadTrialParams();
            num_consecutive_corr += 1;
            logEvent("num_consecutive_corr " + num_consecutive_corr, callback);
            log_trial_outcome(true);
            callback.commitTrialDataFromTask_("SUCCESSFUL_TRIAL");
            showInterTrialInterval(1500);
            //startTrial();

        }, 500); // 500 milliseconds delay to show color change
    }

    // Implement interface and listener to enable communication up to TaskManager
    TaskInterface callback;

    public void setFragInterfaceListener(TaskInterface callback) {
        this.callback = callback;
    }
}
