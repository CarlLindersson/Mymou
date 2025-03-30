package mymou.task.individual_tasks;

import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.view.MotionEvent;
import android.graphics.Rect;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.preference.PreferenceManager;
import mymou.R;
import mymou.Utils.SoundManager;
import mymou.preferences.PreferencesManager;
import mymou.task.backend.RewardSystem;
import mymou.task.backend.TaskInterface;
import mymou.task.backend.UtilsTask;

import java.util.Random;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

/**
 * Shaping 1: Circle Shrinking and Moving
 *
 * Valid touch area starts as the entire screen, and gets progressively smaller
 * The cue also moves randomly around the screen
 * An idle timeout resets size of the cue to the entire screen
 * Must get specified amount of presses in a row to receive reward
 *
 * @param num_consecutive_corr the current number of consecutive presses
 *
 * DONE: (1) Make circle larger at smallest stage (200).
 * DONE: (2) Circle disappears when touched.
 * DONE: (3) Detect, log, and save presses outside of the circle cue and their location.
 * DONE: (4) Detect, log, and save presses with multiple fingers and their location.
 * DONE: (5) Shrink the smallest cue size after a certain amount of correct presses.
 * DONE: (6) Ensure there is no blinking/lagging background in ITI.
 * DONE: (7) locate database and saved data/photos.
 * DONE: (8) Fix date and time issue with saving.
 * DONE: (9) Ensure data and photos are saved correctly.
 * DONE: (10) Fix location issue with onTouch vs onClick.
 * DONE: (11) investigate camera bug
 * DONE: (12) fix camera bug
 * DONE: (13) add logs for consecutive corr and reward duration.
 * TODO: (14) Make SETTINGS CHANGEABLE FROM TABLET without computer.
 * Potentially TODO: Make Click listener adaptive to only register single finger or multi finger clicks.
 * Potentially TODO: use onTouch (on DOWN) instead of onClick (on DOWN+UP)
 */


public class TaskCircleShrinking extends Task {

    // Debug
    public static String TAG = "TaskCircleShrinking";

    private final String preftag_successful_trial = "t_three_successful_trial";
    private final String preftag_num_consecutive_corr = "t_three_num_consecutive_corr";
    private static final int rew_scalar = 1;
    private static int num_consecutive_corr;
    private static PreferencesManager prefManager;
    private SharedPreferences settings;
    private static int latestRewardChannel;
    private ConstraintLayout parentLayout; // Declare as ConstraintLayout

    // to calculate amount reward given
    double reward_duration_double = PreferencesManager.rewardduration * rew_scalar;
    int reward_duration_int = (int) reward_duration_double;

    // Touch detection variables
    private boolean useSingleFinger = false; // New setting for single-finger interaction
    private final boolean consumeTouch = false;
    private boolean multiFingerTouchDetected = false;
    private final HashSet<Integer> activeFingers = new HashSet<>(); // HashSet to track active fingers
    private final Map<Integer, String> lastLoggedEvent = new HashMap<>();
    private final Map<Integer, Long> lastEventTimestamp = new HashMap<>();
    private final Map<Integer, Float> lastTouchX = new HashMap<>();
    private final Map<Integer, Float> lastTouchY = new HashMap<>();
    private static final float MIN_MOVE_DISTANCE = 50.0f; // Minimum distance between movements to log a new event

    // Task objects
    private static Button cue;

    // Cue variables
    private int INITIAL_SIZE;
    private int INITIAL_MIN_SIZE = 200;
    private int MIN_SIZE;
    private int LOWER_LIMIT = 50;

    // Task difficulty variables
    private boolean taskLevels = false;
    private static int consecutiveCueTouches;

    // Loggers to track session variables
    private static int l_rewgiven = 0;

    // Utility methods
    private final Handler handler = new Handler(); // Handler to manage delays

    // Set up the touch listener for the entire window
    @Override
    public void onResume() {
        super.onResume();

        // Get the root view of the window (the DecorView)
        View decorView = getActivity().getWindow().getDecorView();

        // Set a global touch listener for the entire window
        decorView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Call your touch detection method
                detectTouches(v, event, false);

                // Return false to allow the event to propagate to child views, or true to consume the event
                return false;  // Return true to consume event and prevent propagation if needed
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_task_empty, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState); // Call the superclass method

        // Find the parent layout by its ID
        parentLayout = getView().findViewById(R.id.parent_task_empty);

        // Set the background color of the root view to black
        view.setBackgroundColor(getResources().getColor(android.R.color.black));

        logEvent(TAG + " started", callback);

        loadTrialParams();

        assignObjects(); // Assign the objects and setup the cue with OnClickListener

        Log.d(TAG, "In continuousMazeTasks: timeoutbackground = " + PreferencesManager.timeoutbackground);
    }

    private void assignObjects() {
        // Load preferences
        prefManager = new PreferencesManager(getContext());
        prefManager.TrainingTasks();

        // Create one giant cue
        cue = UtilsTask.addColorCue(0, prefManager.t_one_screen_colour,
                getContext(), buttonClickListener, getView().findViewById(R.id.parent_task_empty));

        // Set the circular shape background (Green)
        cue.setBackgroundResource(R.drawable.circle_shape);

        // Set up an OnTouchListener for the cue to log touch events without overriding its click behavior
        cue.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Log events specifically on the cue
                detectTouches(v, event, true);

                // Return true to consume the event for the cue, ensuring cue-specific touches are handled
                return false;
            }
        });

        // Determine the maximum size for the circular cue
        Display display = getActivity().getWindowManager().getDefaultDisplay();
        Point screen_size = new Point();
        display.getSize(screen_size);
        int max_x = screen_size.x - PreferencesManager.cue_size;
        int max_y = screen_size.y - PreferencesManager.cue_size;

        // Define the initial and minimum sizes
        INITIAL_SIZE = PreferencesManager.cue_size + Math.min(max_x, max_y); // Initial maximum size

        // Calculate the minimum size based on consecutive trials
        if (taskLevels) {
            if (consecutiveCueTouches > 20) {
                // Determine the number of 20-trial increments (e.g., 1 for 20-39, 2 for 40-59, etc.)
                int level = consecutiveCueTouches / 20;  // Integer division

                // Reduce the size by 10% per level, starting at 200 and going down
                MIN_SIZE = (int) (INITIAL_MIN_SIZE * Math.pow(0.9, level));

                // Ensure the minimum size does not go below a certain limit (if needed)
                if (MIN_SIZE < LOWER_LIMIT) {  // For example, 50 is the lower limit
                    MIN_SIZE = 50;
                }
            } else {
                MIN_SIZE = INITIAL_MIN_SIZE; // Default Level 1

            }
        } else {
            MIN_SIZE = INITIAL_MIN_SIZE; // Default Level 1
        }

        /*
        // Determine the size decrement per step
        final int STEPS_TO_MIN_SIZE = 10;
        final float sizeDecrementPerStep = (INITIAL_SIZE - MIN_SIZE) / (float) STEPS_TO_MIN_SIZE;


        // Calculate the scalar and the current size
        float scalar;
        if (num_consecutive_corr > STEPS_TO_MIN_SIZE) {
            scalar = MIN_SIZE; // If 10 or more correct, set to minimum size
        } else {
            scalar = INITIAL_SIZE - (num_consecutive_corr * sizeDecrementPerStep);
        }
        */

        // Constants
        final int START_LEVEL = 5;             // Level to start shrinking from (e.g., level 5 size)
        final int DECREMENT_LEVELS = 5;        // Number of shrinking levels (e.g., levels 6, 7, 8, 9, 10)

        // Calculate the size at `START_LEVEL` and the decrement per step
        final float sizeAtStartLevel = INITIAL_SIZE - ((START_LEVEL - 1) * ((INITIAL_SIZE - MIN_SIZE) / 10.0f));
        final float sizeDecrementPerStep = (sizeAtStartLevel - MIN_SIZE) / (float) DECREMENT_LEVELS;

        // Calculate the scalar based on the number of consecutive correct answers, starting immediately from `START_LEVEL` size
        float scalar;
        if (num_consecutive_corr > DECREMENT_LEVELS) {
            scalar = MIN_SIZE;  // If beyond the max shrinking steps, set to minimum size
        } else {
            // Start at `sizeAtStartLevel` and decrement immediately for each consecutive correct answer
            scalar = sizeAtStartLevel - (num_consecutive_corr * sizeDecrementPerStep);
        }

        int size = (int) Math.max(scalar, MIN_SIZE); // Ensure size doesn't go below the minimum

        // Set the circular cue size
        cue.setWidth(size);
        cue.setHeight(size);
        logEvent("Cue size set to " + size + "x" + size, callback);

        // Place the cue at a random location within the screen
        Float x_range = (float) (screen_size.x - size);
        Float y_range = (float) (screen_size.y - size);

        Random r = new Random();
        int x_loc = (int) (r.nextFloat() * x_range);
        int y_loc = (int) (r.nextFloat() * y_range);

        cue.setX(x_loc);
        cue.setY(y_loc);

        Rect cueHitRect = new Rect();
        getHitRect(cue, cueHitRect);

        logEvent("Cue toggled on at location " + x_loc + " " + y_loc, callback);
    }

    private static void getHitRect(View v, Rect rect) {
        rect.left = (int) (v.getLeft() + v.getTranslationX());
        rect.top = (int) (v.getTop() + v.getTranslationY());
        rect.right = rect.left + v.getWidth();
        rect.bottom = rect.top + v.getHeight();
    }

    private boolean isTouchInsideCue(float touchX, float touchY) {
        // Create a Rect object to hold the hitbox of the cue
        Rect cueHitRect = new Rect();

        // Get the hit rectangle of the cue
        getHitRect(cue, cueHitRect);

        return cueHitRect.contains((int) touchX, (int) touchY);
    }

    // Enum representing the valid states for each pointer
    private enum TouchState {
        INITIAL,  // No touch yet
        TOUCH_DOWN,  // Finger touched down
        MOVE,  // Finger moved
        LIFT  // Finger lifted
    }

    // Mapping to store the current state of each pointer (finger)
    private final Map<Integer, TouchState> pointerStates = new HashMap<>();
    private boolean detectTouches(View v, MotionEvent event, boolean localTouch) {
        long currentTime = System.currentTimeMillis(); // Get the current time in milliseconds

        // Get the cue's location on the screen
        int[] cueLocationOnScreen = new int[2];
        cue.getLocationOnScreen(cueLocationOnScreen); // Get the top-left position of the cue on the screen

        // Loop through all active touches (multiple fingers)
        for (int i = 0; i < event.getPointerCount(); i++) {
            int pointerId = event.getPointerId(i);
            float localTouchX = event.getX(i);  // Local X coordinate (relative to cue)
            float localTouchY = event.getY(i);  // Local Y coordinate (relative to cue)

            // Declare globalTouchX and globalTouchY outside the if-else block
            float globalTouchX;
            float globalTouchY;

            // If touch detected in a cue, convert local coordinates to global (screen) coordinates
            if (localTouch) {
                globalTouchX = localTouchX + cueLocationOnScreen[0];
                globalTouchY = localTouchY + cueLocationOnScreen[1];
            } else {
                globalTouchX = localTouchX;
                globalTouchY = localTouchY;
            }

            // Track active finger IDs
            activeFingers.add(pointerId);

            // Check if single-finger mode is enabled
            if (useSingleFinger && event.getPointerCount() > 1) {
                logEventIfUnique(pointerId, "MULTI FINGER TOUCH", globalTouchX, globalTouchY, currentTime, MotionEvent.ACTION_POINTER_DOWN);
                multiFingerTouchDetected = true;
                cue.setClickable(false);
            }

            // Check if the touch is inside or outside the cue
            boolean insideCue = isTouchInsideCue(globalTouchX, globalTouchY);

            // If touch is outside the cue, saveTouchOutsideCueStatus
            if (!insideCue) {
                saveTouchOutsideCueStatus(false);
            };

            // Handle different touch events for each pointer
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (insideCue) {
                        logEventIfUnique(pointerId, "Finger " + pointerId + " touched inside the cue at " + globalTouchX + " " + globalTouchY, globalTouchX, globalTouchY, currentTime, MotionEvent.ACTION_DOWN);
                    } else {
                        logEventIfUnique(pointerId, "Finger " + pointerId + " touched outside the cue at " + globalTouchX + " " + globalTouchY, globalTouchX, globalTouchY, currentTime, MotionEvent.ACTION_DOWN);
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (shouldLogMovement(pointerId, globalTouchX, globalTouchY)) {
                        logEventIfUnique(pointerId, "Finger " + pointerId + " moved at " + globalTouchX + " " + globalTouchY, globalTouchX, globalTouchY, currentTime, MotionEvent.ACTION_MOVE);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    logEventIfUnique(pointerId, "Finger " + pointerId + " lifted at " + globalTouchX + " " + globalTouchY, globalTouchX, globalTouchY, currentTime, MotionEvent.ACTION_UP);
                    activeFingers.remove(pointerId);
                    break;
            }

            // Reset touch state if in single-finger mode and all fingers are lifted
            if (useSingleFinger && activeFingers.isEmpty()) {
                logEventIfUnique(pointerId, "All fingers lifted, resetting touch state", globalTouchX, globalTouchY, currentTime, MotionEvent.ACTION_UP);
                multiFingerTouchDetected = false;
                cue.setClickable(true);
            }
        }

        // Return false to allow child views to process the event (i.e., the cue)
        return false;
    }

    // Updated helper method to log an event if it hasn't been logged recently for this pointer
    private void logEventIfUnique(int pointerId, String eventDescription, float touchX, float touchY, long currentTime, int eventAction) {
        // Initialize the state for this pointer if it's not already set
        pointerStates.putIfAbsent(pointerId, TouchState.INITIAL);
        TouchState currentState = pointerStates.get(pointerId);

        // Check for valid state transitions
        boolean isValidTransition = false;
        switch (currentState) {
            case INITIAL:
                if (eventAction == MotionEvent.ACTION_DOWN || eventAction == MotionEvent.ACTION_POINTER_DOWN) {
                    isValidTransition = true;
                    pointerStates.put(pointerId, TouchState.TOUCH_DOWN); // Transition to TOUCH_DOWN
                }
                break;

            case TOUCH_DOWN:
                if (eventAction == MotionEvent.ACTION_MOVE) {
                    isValidTransition = true;
                    pointerStates.put(pointerId, TouchState.MOVE); // Transition to MOVE
                } else if (eventAction == MotionEvent.ACTION_UP || eventAction == MotionEvent.ACTION_POINTER_UP) {
                    isValidTransition = true;
                    pointerStates.put(pointerId, TouchState.LIFT); // Transition to LIFT
                }
                break;

            case MOVE:
                if (eventAction == MotionEvent.ACTION_MOVE) {
                    isValidTransition = true; // Allow multiple move events
                } else if (eventAction == MotionEvent.ACTION_UP || eventAction == MotionEvent.ACTION_POINTER_UP) {
                    isValidTransition = true;
                    pointerStates.put(pointerId, TouchState.LIFT); // Transition to LIFT
                }
                break;

            case LIFT:
                if (eventAction == MotionEvent.ACTION_DOWN || eventAction == MotionEvent.ACTION_POINTER_DOWN) {
                    isValidTransition = true;
                    pointerStates.put(pointerId, TouchState.TOUCH_DOWN); // Transition to TOUCH_DOWN
                }
                break;
        }

        // Log the event only if the transition is valid
        if (isValidTransition) {
            logEvent(eventDescription, callback);
        }
    }

    // Helper method to check if a movement should be logged based on the distance for each finger
    private boolean shouldLogMovement(int pointerId, float touchX, float touchY) {
        Float lastX = lastTouchX.get(pointerId);
        Float lastY = lastTouchY.get(pointerId);

        // If this is the first time movement is logged for this pointer, accept it
        if (lastX == null || lastY == null) {
            lastTouchX.put(pointerId, touchX);
            lastTouchY.put(pointerId, touchY);
            return true;
        }

        // Calculate the distance moved
        float deltaX = touchX - lastX;
        float deltaY = touchY - lastY;
        float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);

        // Log the movement only if the distance is greater than the minimum move distance
        if (distance > MIN_MOVE_DISTANCE) {
            lastTouchX.put(pointerId, touchX);
            lastTouchY.put(pointerId, touchY);
            return true;
        } else {
            return false; // Movement is too small, don't log
        }
    }

    // Load previous trial params
    private void loadTrialParams() {
        settings = PreferenceManager.getDefaultSharedPreferences(getContext());
        boolean prev_trial_correct = settings.getBoolean(preftag_successful_trial, false);
        num_consecutive_corr = settings.getInt(preftag_num_consecutive_corr, 0);
        boolean prev_trial_touch_outside = settings.getBoolean("prev_trial_touch_outside", false);  // Check if previous trial had a touch outside
        if (prev_trial_touch_outside) {
            consecutiveCueTouches = 0;
        }

        useSingleFinger = false;
        if(!prev_trial_correct) {
            num_consecutive_corr = 0;
        }

        // Now save values, and they will be overwritten upon correct trial happening
        update_outcome_in_sharedPrefs(false);

        Log.d(TAG, num_consecutive_corr+" "+prev_trial_correct);
    }

    private void update_outcome_in_sharedPrefs(boolean outcome) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(preftag_successful_trial, outcome);
        editor.putInt(preftag_num_consecutive_corr, num_consecutive_corr);
        editor.commit();
    }

    private void saveTouchOutsideCueStatus(boolean touchedOutside) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("prev_trial_touch_outside", touchedOutside);  // Save whether touch outside occurred
        editor.commit();
    }


    private final View.OnClickListener buttonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {

            logEvent("Cue pressed", callback);

            // Disable cue interaction
            //UtilsTask.toggleCue(cue, false);
            cue.setClickable(false);
            cue.setVisibility(View.INVISIBLE);  // Make the cue disappear after the press

            // Reset timer for idle timeout on each press - not done in taskManager
            callback.resetTimer_();

            // Updated num_consecutive_corr
            num_consecutive_corr += 1;
            update_outcome_in_sharedPrefs(true);

            // Log num_consecutive_corr
            logEvent("num_consecutive_corr " + num_consecutive_corr, callback);

            // Update consecutiveCueTouches
            consecutiveCueTouches += 1;
            logEvent("consecutiveCueTouches " + consecutiveCueTouches, callback);

            // Log delivered reward duration
            logEvent("reward_duration " + reward_duration_int, callback);

            // Update amount of reward given
            l_rewgiven = l_rewgiven + PreferencesManager.rewardduration;

            // Take photo at button press (to collect 1 photo per trial)
            callback.takePhotoFromTask_();

            // endOfTrial performs an enormous amount of operations which might seem nice
            // but it makes the method inflexible, hard to read, and consequently hard to work
            // with when designing new tasks. The flexibility and readability of the existing code
            // could be improved allowing the task script to be built and read with more ease.
            // Here is what endOfTrial does:
            // - Kills the task using KillTask() in taskManager
            //    - which as a side effect kills any task-specific settings (e.g. reward duration)
            //      unless specified using edited sharedPrefs which remain after task is killed.
            //    - restart static fragmentTransaction so it can be used.
            //    - timerRunning = false;
            //    - trial_running = false;
            // - Updates l_numcorr = l_numcorr + 1; Sometimes twice at once (bug???)
            // - Checks if feedback should be given
            //      - if handleFeedback == false: call another method also called "endoftrial"
            //        which first calles logEvent:
            //              if the successfulTrial boolean is true:
            //                  add preferencesManager.ec_correct_trial to the session event log
            //              if the successfulTrial boolean is false:
            //                  add preferencesManager.ec_incorrect_trial to the session event log
            //        this logEvent method both saves the string to data and logs in the d logcat.
            //      - calls the method "commitTrialData" which:
            //          - Resets trial counter if we passed midnight
            //          - Writes event log to a text file at Storage/self/primary/Mymou/
            //            with the tablet date as the name (year/month/day)
            //          - if (preferencesManager.facerecog):
            //              Place photo (the latest photo taken that trial: at go cue or in task)
            //              in correct monkey's folder
            //          - clear the trial Data list by trialData = new ArrayList<String>();
            //          - Increment trial counter by trialCounter++;
            //      - calls prepareForNewTrial which:
            //          - setBrightness based on preference manager and mcontext
            //          - waits for newTrialDelay which is either:
            //                 - 0 if handleFeedback == false,
            //                 - preferencesManager.rewardduration + 5 if handleFeedback == true and
            //                   successfulTrial == True.
            //                 - preferencesManager.timeoutduration if handleFeedback == true and
            //                   successfulTrial == false.
            //          - sets task background
            //          - toggle go cue depending on preferencesManager.skip_go_cue
            //          - starts new trial
            // - if Correct trial:
            //      - if reward scalar = 0: return (does nothing - does not restart new trial)
            //      - changes background to preferencesManager.rewardbackground
            //      - if (preferencesManager.num_reward_chans == 1) {
            //            deliverReward(preferencesManager.default_rew_chan, rew_scalar);
            //            - this function deliver sound, reward,
            //            - and calls endoftrial (log data, commit data, and restart trial).
            //        } else {
            //            // Otherwise reveal reward cues
            //            UtilsTask.randomlyPositionCues(cues_Reward, possible_cue_locs);
            //            UtilsTask.toggleCues(cues_Reward, true);
            //            updateTvExplanation("Correct trial! Choose your reward");
            // - if incorrect trial:
            //      - change to timeoutbackground
            //      - call endOfTrial (log data, commit data, and restart trial).
            endOfTrial(true, callback, prefManager); // reward scalar is 1 for correct trials and 0 for incorrect trials

        }
    };

    // Implement interface and listener to enable communication up to TaskManager
    TaskInterface callback;
    public void setFragInterfaceListener(TaskInterface callback) {
        this.callback = callback;
    }

}
