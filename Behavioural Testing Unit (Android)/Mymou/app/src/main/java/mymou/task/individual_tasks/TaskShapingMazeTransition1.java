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

public class TaskShapingMazeTransition1 extends Task {

    // Debug
    public static String TAG = "TaskShapingMazeTransition1";

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
    private double adaptationType = 1.3; // 0=KeepAchievedLevelAfterIdle; 1= ResetLevelsAfterIdle; 2=discrete steps of blinking; 3 = hard adaptation steps, 4 = blinking fade
    private ConstraintLayout parentLayout; // Declare as ConstraintLayout

    // Cue size
    private int cueSize = 500;
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
                              boolean visible, boolean clickable, boolean pulsate ) {
        Button node = UtilsTask.addColorCue(0, getResources().getColor(android.R.color.white),
                getContext(), listener, parentLayout);
        node.setBackgroundResource(drawableResource);
       // int cueSize = 500;

        node.setWidth(cueSize);
        node.setHeight(cueSize);
        node.setX(position.x - cueSize / 2);
        node.setY(position.y - cueSize / 2);


        node.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        node.setClickable(clickable);

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

    // Helper function to create edges and store the connections between cues
    public View createEdgeAndConnections(Button startCue, Button endCue,
                                         Point startPoint, Point endPoint) {

        // Pass Point objects to the createEdge method
        View edge = createEdge(startPoint, endPoint);

        // Store the connection between the cues
        if (!cueConnections.containsKey(startCue)) {
            cueConnections.put(startCue, new ArrayList<>());
        }
        if (!cueConnections.containsKey(endCue)) {
            cueConnections.put(endCue, new ArrayList<>());
        }

        // Add each cue as a neighbor of the other
        cueConnections.get(startCue).add(endCue);
        cueConnections.get(endCue).add(startCue);

        return edge;
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
        int margin = cueSize/2 + 25; // Margin in pixels

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
        float scalePercentage = 35; // Can be set dynamically from 0% to 100%
        int gridScale = minGridScale + (int) ((maxGridScale - minGridScale) * (scalePercentage / 100f));

        // Define the grid size
        int gridRows = 3;
        int gridCols = 2;

        // Calculate the offset to align the bottom row of the grid 75 pixels from the bottom of the screen
        int gridHeight = gridRows * gridScale;
        int offsetX = (screenSize.x - usableWidth) / 2; // Center horizontally in the usable width
        int offsetY = screenSize.y - gridHeight+350; // Position the bottom row 75 pixels from the bottom

        // Generate grid positions within the usable area
        List<Point> gridPositions = buildGrid(gridRows, gridCols, gridScale, offsetX, offsetY);

        // Check if gridPositions is populated correctly
        if (gridPositions == null || gridPositions.isEmpty()) {
            Log.e(TAG, "Failed to generate grid positions. Aborting task setup.");
            return; // Exit the method if gridPositions is empty or null
        } else {
            Log.e(TAG, "Grid positions populated! " + gridPositions);
        }

        // Randomly select a position for cue1
        Random random = new Random();
        cue1Position = gridPositions.get(random.nextInt(gridPositions.size()));
        Log.d(TAG, "cue1 position " + cue1Position);

        // Find a neighboring node position for cue2
        List<Point> neighbors = findNeighbors(cue1Position, gridPositions, gridScale, occupiedPositions);
        Log.d(TAG, "neighbors " + neighbors);
        cue2Position = neighbors.get(random.nextInt(neighbors.size())); // Randomly select a neighbor

        edge1 = createEdge(cue1Position, cue2Position);

        // If adaptive, adapt taskLevel after performance
        if (adaptive) {
            if (adaptationType == 0) {
                if (num_consecutive_corr > 10) {
                    taskLevel += 1;
                    num_consecutive_corr = 0;
                }
                if (taskLevel > 3) { // 3 is highest level
                    taskLevel = 3;
                }
            }
            if (adaptationType == 1) {
                if (num_consecutive_corr > 10) {
                    taskLevel = 1;
                }
                if (num_consecutive_corr > 20) {
                    taskLevel = 2;
                }
                if (num_consecutive_corr > 30) {
                    taskLevel = 3;
                }
                if (num_consecutive_corr < 10) {
                    taskLevel = 0;
                }
            }
            if (adaptationType == 1.2) {
                if (num_consecutive_corr > 10) {
                    taskLevel = 2;
                }
                if (num_consecutive_corr > 20) {
                    taskLevel = 3;
                }
                if (num_consecutive_corr > 30) {
                    taskLevel = 4;
                }
                if (num_consecutive_corr < 10) {
                    taskLevel = 1;
                }
            }
            if (adaptationType == 1.3) {
                if (num_consecutive_corr > 1) {
                    taskLevel = 2;
                }
                if (num_consecutive_corr > 10) {
                    taskLevel = 3;
                }
                if (num_consecutive_corr > 20) {
                    taskLevel = 4;
                }
                if (num_consecutive_corr < 1) {
                    taskLevel = 1;
                }
            }
            if (adaptationType == 1.4) {
                if (num_consecutive_corr <= 10) {
                    taskLevel = 3;
                }
                if (num_consecutive_corr > 10) {
                    taskLevel = 4;
                }
                //if (num_consecutive_corr > 20) {
                //    taskLevel = 4;
                //}

            }
            if (adaptationType == 2) {
                if (num_consecutive_corr > 5) {
                   // cueSize = 450;
                    nodeAlphaLevel = 0.5f;
                }
                if (num_consecutive_corr > 10) {
                    //  cueSize = 400;
                    nodeAlphaLevel = 0.7f;
                }
                if (num_consecutive_corr > 15) {
                    // cueSize = 350;
                    nodeAlphaLevel = 0.9f;
                }
                if (num_consecutive_corr > 20) {
                    // cueSize = 300;
                    nodeAlphaLevel = 1.0f;
                }
                if (num_consecutive_corr < 10) {
                    //  cueSize = 500;
                }
            }
            if (adaptationType == 3) {
                if (num_consecutive_corr > 10) {
                    taskLevel = 3;
                }
                if (num_consecutive_corr > 20) {
                    taskLevel = 4;
                }
                if (num_consecutive_corr < 10) {
                    taskLevel = 2;
                }
            }
            float minAlpha = 0.3f;
            float maxAlpha = 1.0f;
            int minCorr = 0;
            int maxCorr = 20;

            if (adaptationType == 4) {
                if (num_consecutive_corr >= minCorr) {
                    // Calculate a gradual alpha level based on num_consecutive_corr
                    nodeAlphaLevel = minAlpha + (maxAlpha - minAlpha) * ((float)(num_consecutive_corr - minCorr) / (maxCorr - minCorr));

                    // Clamp nodeAlphaLevel to maxAlpha if it exceeds maxCorr
                    if (nodeAlphaLevel > maxAlpha) {
                        nodeAlphaLevel = maxAlpha;
                    }
                } else {
                    nodeAlphaLevel = minAlpha;
                }
            }

        }



        if (taskLevel == -1) {
            createBlueCircle(cue1Position);
            startAlphaPulse(blueCircle);

            cue1 = createNode(cue1Position, R.drawable.circle_shape_white, cueMinus1ClickListener,
                    true, true, false);
            cue2 = createNode(cue2Position, R.drawable.circle_shape, cue2ClickListener,
                    false, false, false);

            //edge1 = createEdgeAndConnections(cue1, cue2, cue1Position, cue2Position);

        }

        if (taskLevel == 0) {
            createBlueCircle(cue1Position);
            cue1 = createNode(cue1Position, R.drawable.circle_shape_white, cue1ClickListener,
                    true, true, false);
            cue2 = createNode(cue2Position, R.drawable.circle_shape, cue2ClickListener,
                    false, false, false);

            //edge1 = createEdgeAndConnections(cue1, cue2, cue1Position, cue2Position);

        }

        if (taskLevel == 1) {
            createBlueCircle(cue1Position);
            cue1 = createNode(cue1Position, R.drawable.circle_shape_white, cue1Level1ClickListener,
                    true, true, false);
            cue2 = createNode(cue2Position, R.drawable.circle_shape_white, cue2Level1ClickListener,
                    false, false, false);

            List<Point> neighbors2 = findNeighbors(cue2Position, gridPositions, gridScale,
                    occupiedPositions);
            cue3Position = neighbors2.get(random.nextInt(neighbors2.size()));
            edge2 = createEdge(cue2Position, cue3Position);

            cue3 = createNode(cue3Position, R.drawable.circle_shape, null,
                    false, false, false);

        }

        if (taskLevel == 2) {
            createBlueCircle(cue1Position);
            cue1 = createNode(cue1Position, R.drawable.circle_shape_white, cue1Level2ClickListener,
                    true, false, false);
            cue2 = createNode(cue2Position, R.drawable.circle_shape_white, cue2Level1ClickListener,
                    true, true, false);
            List<Point> neighbors2 = findNeighbors(cue2Position, gridPositions, gridScale,
                    occupiedPositions);
            cue3Position = neighbors2.get(random.nextInt(neighbors2.size()));
            edge2 = createEdge(cue2Position, cue3Position);
            cue3 = createNode(cue3Position, R.drawable.circle_shape, null,
                    true, false, false);
        }

        if (taskLevel == 3) {
            createBlueCircle(cue1Position);
            cue1 = createNode(cue1Position, R.drawable.circle_shape_white, cue1Level2ClickListener,
                    true, false, false);
            cue2 = createNode(cue2Position, R.drawable.circle_shape_white, cue2Level3ClickListener,
                    true, true, false);
            List<Point> neighbors2 = findNeighbors(cue2Position, gridPositions, gridScale,
                    occupiedPositions);
            cue3Position = neighbors2.get(random.nextInt(neighbors2.size()));
            edge2 = createEdge(cue2Position, cue3Position);
            cue3 = createNode(cue3Position, R.drawable.circle_shape_white, cue3Level3ClickListener,
                    true, false, false);
            List<Point> neighbors3 = findNeighbors(cue3Position, gridPositions, gridScale,
                    occupiedPositions);
            cue4Position = neighbors3.get(random.nextInt(neighbors3.size()));
            edge3 = createEdge(cue3Position, cue4Position);
            cue4 = createNode(cue4Position, R.drawable.circle_shape, cue4ClickListener,
                    true, false, false);
        }
        if (taskLevel == 4) {
            createBlueCircle(cue2Position);
            cue1 = createNode(cue1Position, R.drawable.circle_shape_white, cue1Level2ClickListener,
                    true, true, false);
            cue2 = createNode(cue2Position, R.drawable.circle_shape_white, cue2Level3ClickListener,
                    true, true, false);
            List<Point> neighbors2 = findNeighbors(cue2Position, gridPositions, gridScale,
                    occupiedPositions);
            cue3Position = neighbors2.get(random.nextInt(neighbors2.size()));
            edge2 = createEdge(cue2Position, cue3Position);
            cue3 = createNode(cue3Position, R.drawable.circle_shape_white, cue3Level3ClickListener,
                    true, true, false);
            List<Point> neighbors3 = findNeighbors(cue3Position, gridPositions, gridScale,
                    occupiedPositions);
            cue4Position = neighbors3.get(random.nextInt(neighbors3.size()));
            edge3 = createEdge(cue3Position, cue4Position);
            cue4 = createNode(cue4Position, R.drawable.circle_shape, cue4ClickListener,
                    true, false, false);
        }

        if (taskLevel == 44) {
            // add cue 1 and 2 ( already has edge)
            cue1 = createNode(cue1Position, R.drawable.circle_shape_white, cue1Level2ClickListener,
                    true, false, false);
            cue2 = createNode(cue2Position, R.drawable.circle_shape_white, cue2Level4ClickListener,
                    true, true, false);
            // Find a neighboring node position for cue2
            List<Point> neighbors2 = findNeighbors(cue2Position, gridPositions, gridScale,
                    occupiedPositions);
            cue3Position = neighbors2.get(random.nextInt(neighbors2.size()));
            // add edge 2 --- 3
            edge2 = createEdge(cue2Position, cue3Position);
            // add cue 3
            cue3 = createNode(cue3Position, R.drawable.circle_shape_white, cue3Level4ClickListener,
                    true, false, false);
            // Find position for cue4
            List<Point> neighbors3 = findNeighbors(cue2Position, gridPositions, gridScale,
                    occupiedPositions);
            cue4Position = neighbors3.get(random.nextInt(neighbors3.size()));
            // add edge 2 --- 4
            edge3 = createEdge(cue2Position, cue4Position);
            // add cue 4
            cue4 = createNode(cue4Position, R.drawable.circle_shape, cue4Level4ClickListener,
                    true, false, false);
        }

        logEvent("Task Level: " + taskLevel, callback);
        logEvent("Cue1 placed at: " + cue1Position.x + "," + cue1Position.y + " and edge placed to connect to cue 2 node at: " + cue2Position.x + "," + cue2Position.y, callback);

        // Set the flag to true to indicate that the cues have been placed
        cuesPlaced = true;
    }

    // Method to find neighboring nodes that are not occupied
    /*private List<Point> findNeighbors(Point cue1Position, List<Point> gridPositions, int gridScale, List<Point> occupiedPositions) {
        List<Point> neighbors = new ArrayList<>();

        // Calculate possible neighbors
        for (Point point : gridPositions) {
            boolean isNeighbor = (Math.abs(point.x - cue1Position.x) == gridScale && point.y == cue1Position.y) ||
                    (Math.abs(point.y - cue1Position.y) == gridScale && point.x == cue1Position.x);

            // Add the point only if it's a neighbor and not occupied
            if (isNeighbor && !occupiedPositions.contains(point)) {
                neighbors.add(point); // Neighboring nodes in the same row or column at gridScale distance
            }
        }

        if (neighbors.isEmpty()){
            assignObjects(); // re-run the assignment of objects
        }

        return neighbors;
    }*/

    private List<Point> findNeighbors(Point cue1Position, List<Point> gridPositions, int gridScale, List<Point> occupiedPositions) {
        List<Point> neighbors = new ArrayList<>();

        for (Point point : gridPositions) {
         //   boolean isNeighbor = (Math.abs(point.x - cue1Position.x) == gridScale && point.y == cue1Position.y) ||
           //         (Math.abs(point.y - cue1Position.y) == gridScale && point.x == cue1Position.x);
            //
            boolean isNeighbor = (Math.abs(point.x - cue1Position.x) >= gridScale - TOLERANCE &&
                    Math.abs(point.x - cue1Position.x) <= gridScale + TOLERANCE &&
                    point.y == cue1Position.y) ||
                    (Math.abs(point.y - cue1Position.y) >= gridScale - TOLERANCE &&
                            Math.abs(point.y - cue1Position.y) <= gridScale + TOLERANCE &&
                            point.x == cue1Position.x);
            if (isNeighbor && !occupiedPositions.contains(point)) {
                neighbors.add(point);
            }
        }

        // Ensure no infinite recursion
        if (neighbors.isEmpty()) {
            Log.e(TAG, "No valid neighbors found for position: " + cue1Position);
            return new ArrayList<>(); // Return an empty list to prevent further recursion
        }

        return neighbors;
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

    /*
    // Method to animate the movement of the blue circle from the current position to the target cue
    private void animateBlueCircle(Point targetPosition) {
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
        animatorSet.setDuration(200);  // Set duration to 500ms or customize as needed
        animatorSet.playTogether(animatorX, animatorY);
        animatorSet.start();

        // Make the blue circle visible if it's not already
        blueCircle.setVisibility(View.VISIBLE);
    }*/

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

        // Make the blue circle visible if it's not already
        //blueCircle.setVisibility(View.VISIBLE);
    }

    // Click listener for the first cue
    private View.OnClickListener cueMinus1ClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            cue1.setClickable(false);
            stopPulsation(blueCircle);
            logEvent("cue1Click", callback);
            //new SoundManager(prefManager).playTone();

            // Animate the blue circle and pass a Runnable for actions after the animation ends
            animateBlueCircle(cue1Position, new Runnable() {
                @Override
                public void run() {
                    // Change the background resource to maintain circular shape while changing color to deep blue
                    // cue1.setBackgroundResource(R.drawable.circle_shape_blue); // Use blue circle shape

                    // Show the green node (cue2)
                    cue2.setVisibility(View.VISIBLE);
                    startAlphaPulse(cue2);

                    cue2.setClickable(true); // Enable cue2 interaction

                }
            });

        }
    };

    // Click listener for the first cue
    private View.OnClickListener cue1ClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            logEvent("cue1Click", callback);

            // Animate the blue circle and pass a Runnable for actions after the animation ends
            animateBlueCircle(cue1Position, new Runnable() {
                @Override
                public void run() {
                    // Change the background resource to maintain circular shape while changing color to deep blue
                    // cue1.setBackgroundResource(R.drawable.circle_shape_blue); // Use blue circle shape
                    cue1.setClickable(false); // Disable further clicks on cue1

                    // Show the green node (cue2)
                    cue2.setVisibility(View.VISIBLE);

                    cue2.setClickable(true); // Enable cue2 interaction
                }
            });


        }
    };
    // Click listener for the first cue
    private View.OnClickListener cue1Level1ClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            logEvent("cue1Click", callback);

            // Animate the blue circle and pass a Runnable for actions after the animation ends
            animateBlueCircle(cue1Position, new Runnable() {
                @Override
                public void run() {
                    // Change the background resource to maintain circular shape while changing color to deep blue
                    // cue1.setBackgroundResource(R.drawable.circle_shape_blue); // Use blue circle shape
                    cue1.setClickable(false); // Disable further clicks on cue1

                    // Show the green node (cue2)
                    cue2.setBackgroundResource(R.drawable.circle_shape_white); // Use blue circle shape
                    cue2.setVisibility(View.VISIBLE);
                    cue2.setClickable(true); // Enable cue2 interaction

                    cue3.setClickable(false); // disable cue3 interaction
                }
            });


        }
    };

    // Click listener for the first cue
    private View.OnClickListener cue1Level2ClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            logEvent("cue1Click", callback);

            // Animate the blue circle and pass a Runnable for actions after the animation ends
            animateBlueCircle(cue1Position, new Runnable() {
                @Override
                public void run() {
                    // Change the background resource to maintain circular shape while changing color to deep blue
                    //cue1.setBackgroundResource(R.drawable.circle_shape_blue); // Use blue circle shape
                    cue1.setClickable(false); // Disable further clicks on cue1

                    // set background and enable cue2
                    cue2.setBackgroundResource(R.drawable.circle_shape_white); // Use blue circle shape
                    cue2.setClickable(true); // Enable cue2 interaction

                    cue3.setClickable(false); // Disable further clicks on cue1
                }
            });

        }
    };

    private View.OnClickListener cue2ClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            cue2.setClickable(false);
            logEvent("cue2Click", callback);
            stopPulsation(cue2);

            // Animate the blue circle and pass a Runnable for actions after the animation ends
            animateBlueCircle(cue2Position, new Runnable() {
                @Override
                public void run() {
                    // Change the color of cue2 and give reward after the animation finishes
                    //cue2.setBackgroundResource(R.drawable.circle_shape_blue); // Change green cue2 to blue

                    // Call giveRewardAndEndTrial() after the animation completes
                    giveRewardAndEndTrial();
                }
            });
        }
    };

    // Click listener for cue2 in taskLevel 1
    private View.OnClickListener cue2Level1ClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            logEvent("cue2Click", callback);
            // Animate the blue circle and pass a Runnable for actions after the animation ends
            animateBlueCircle(cue2Position, new Runnable() {
                @Override
                public void run() {
                    cue1.setBackgroundResource(R.drawable.circle_shape_white); // Change blue cue1 to white
                    //cue2.setBackgroundResource(R.drawable.circle_shape_blue);
                    cue2.setClickable(false);
                    cue1.setClickable(true);

                    cue3.setVisibility(View.VISIBLE);
                    cue3.setClickable(true);

                    cue3.setOnClickListener(cue3ClickListener);
                }
            });

        }
    };

    // Click listener for cue2 in taskLevel 1
    private View.OnClickListener cue2Level3ClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            logEvent("cue2Click", callback);

            // Animate the blue circle and pass a Runnable for actions after the animation ends
            animateBlueCircle(cue2Position, new Runnable() {
                @Override
                public void run() {
                    cue1.setBackgroundResource(R.drawable.circle_shape_white); // Change blue cue1 to white
                    cue3.setBackgroundResource(R.drawable.circle_shape_white);
                    // cue2.setBackgroundResource(R.drawable.circle_shape_blue);
                    cue2.setClickable(false);
                    cue1.setClickable(true);

                    cue3.setClickable(true);
                    cue4.setClickable(false);
                }
            });
        }
    };

    private View.OnClickListener cue2Level4ClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            logEvent("cue2Click", callback);
            // Animate the blue circle and pass a Runnable for actions after the animation ends
            animateBlueCircle(cue2Position, new Runnable() {
                @Override
                public void run() {
                    cue1.setBackgroundResource(R.drawable.circle_shape_white); // Change blue cue1 to white
                    cue3.setBackgroundResource(R.drawable.circle_shape_white);
                    // cue2.setBackgroundResource(R.drawable.circle_shape_blue);
                    cue2.setClickable(false);
                    cue1.setClickable(true);
                    cue3.setClickable(true);
                    cue4.setClickable(true);
                }
            });

        }
    };


    // Click listener for cue3 in taskLevel 3
    private View.OnClickListener cue3Level3ClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            logEvent("cue3Click", callback);
            cue3.setClickable(false);
            // Animate the blue circle and pass a Runnable for actions after the animation ends
            animateBlueCircle(cue3Position, new Runnable() {
                @Override
                public void run() {
                    cue1.setClickable(false);
                    cue2.setBackgroundResource(R.drawable.circle_shape_white); // Change blue cue1 to white
                    // cue3.setBackgroundResource(R.drawable.circle_shape_blue);
                    cue2.setClickable(true);

                    cue4.setClickable(true);
                    cue4.setOnClickListener(cue4ClickListener);
                }
            });

        }
    };

    private View.OnClickListener cue3Level4ClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            logEvent("cue3Click", callback);
            cue3.setClickable(false);
            // Animate the blue circle and pass a Runnable for actions after the animation ends
            animateBlueCircle(cue3Position, new Runnable() {
                @Override
                public void run() {
                    cue2.setBackgroundResource(R.drawable.circle_shape_white); // Change blue cue1 to white
                    //cue3.setBackgroundResource(R.drawable.circle_shape_blue);
                    cue2.setClickable(true);
                }
            });

        }
    };

    // Click listener for cue3 in taskLevel 1
    private View.OnClickListener cue3ClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            logEvent("cue3Click", callback);
            cue3.setClickable(false);
            // Animate the blue circle and pass a Runnable for actions after the animation ends
            animateBlueCircle(cue3Position, new Runnable() {
                @Override
                public void run() {
                    cue1.setBackgroundResource(R.drawable.circle_shape_white); // Change blue cue1 to white
                    cue2.setBackgroundResource(R.drawable.circle_shape_white); // Change blue cue1 to white
                    // cue3.setBackgroundResource(R.drawable.circle_shape_blue);
                    giveRewardAndEndTrial();
                }
            });

        }
    };

    private View.OnClickListener cue4ClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            logEvent("cue4Click", callback);
            cue4.setClickable(false);
            // Animate the blue circle and pass a Runnable for actions after the animation ends
            animateBlueCircle(cue4Position, new Runnable() {
                @Override
                public void run() {
                    cue3.setBackgroundResource(R.drawable.circle_shape_white); // Change blue cue1 to white
                    //cue4.setBackgroundResource(R.drawable.circle_shape_blue);
                    giveRewardAndEndTrial();
                }
            });

        }
    };

    private View.OnClickListener cue4Level4ClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            logEvent("cue4Click", callback);
            cue4.setClickable(false);
            // Animate the blue circle and pass a Runnable for actions after the animation ends
            animateBlueCircle(cue4Position, new Runnable() {
                @Override
                public void run() {
                    cue2.setBackgroundResource(R.drawable.circle_shape_white);
                    //cue4.setBackgroundResource(R.drawable.circle_shape_blue)
                    cue3.setClickable(false);
                    cue2.setClickable(false);
                    cue1.setClickable(false);
                    giveRewardAndEndTrial();
                }
            });
        }
    };

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
