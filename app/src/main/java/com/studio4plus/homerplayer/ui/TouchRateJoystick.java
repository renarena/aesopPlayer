package com.studio4plus.homerplayer.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.concurrent.TimeUnit;

public class TouchRateJoystick implements View.OnTouchListener {
    public interface Listener {
        void onTouchRate(Direction direction, int counter);
    }

    public enum Direction {UP,DOWN,LEFT,RIGHT}
    static public final int RELEASE = -1;
    static public final int REVERSE = -2;
    private final Listener listener;

    private final int minimumScrollMovement;
    private final Handler handler = new Handler();

    private float startX;
    private float startY;
    private Direction direction = Direction.UP; // Arbitrary

    private int state; // a FSM state counter
    private int counter; // Number of times the callback has been made (since last start)

    private final long INTERVAL = 250; // 1/4 second
    private final long INTERVALNs = TimeUnit.MILLISECONDS.toNanos(INTERVAL);

    private long startT;
    private long delayNs = 0;
    private static final float SCALE = 2.0f; // scale factor for interval conversions

    /*
     Similar to a "rate" joystick, where the distance from the initial touchdown position
     controls the rate at which callbacks to 'listener' are made.
     The first callback (counter == 0) indicates that the process has started. Subsequent calls
     (with counter incrementing) indicate that the user continues to hold the button, with
     the frequency increasing or decreasing proportional to the distance from the initial
     touchdown. Only the four cardinal directions are possible, and once it has committed
     to an up/down or left/right direction the user must stop and restart for the other.
     When the direction is reversed the counter is reset to zero and the origin set to that point.
     There is a dead spot near the initial touchdown point.

     The dead spot, limiting to just the cardinal axes, and not changing from up/down to left/right
     are to allow for user finger drift.

     The callbacks should provide audible feedback on the selected direction (when counter == 0)
     and on the progress (ticks) as well as having the actual effect.

     A callback with RELEASE indicates user disengagement and should be used for cleanup.
     A callback with REVERSE indicates a direction reversal.
     */

    public TouchRateJoystick (Context context, TouchRateJoystick.Listener listener) {
        this.listener = listener;

        ViewConfiguration configuration = ViewConfiguration.get(context);
        minimumScrollMovement = configuration.getScaledTouchSlop();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getPointerCount() != 1)
            return false;

        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            return onTouchDown(event);
        case MotionEvent.ACTION_UP:
            return onTouchUp();
        case MotionEvent.ACTION_MOVE:
            return onMove(event);
        default:
            return false;
        }
    }

    @SuppressWarnings("SameReturnValue")
    private boolean onTouchDown(MotionEvent ev) {
        final float x = ev.getX();
        final float y = 10000 - ev.getY(); // make it top down
        final long nanoT = System.nanoTime();

        startX = x;
        startY = y;
        startT = nanoT;
        state = 0;
        delayNs = 4*INTERVALNs;

        return false; // We didn't handle, it might be a tap
    }

    private boolean onTouchUp() {
        handler.removeCallbacksAndMessages(null);

        if (state != 0) {
            state = 0;
            listener.onTouchRate(direction, RELEASE);
            return true; // not a tap
        }

        return false; // allow tap
    }

    // Returns either "infinity" or rate of from 1-4 times the clock interval, expressed
    // as an interval.
    private long movementToDelay(float delta) {
        delta = Math.abs(delta);

        // 4 zones - innermost is "nothing", then faster as the pointer moves away
        // Test for the dead zone
        if (delta < minimumScrollMovement/2) return INTERVALNs * 1000; // "forever"

        // Convert distance to an interval
        delta /= 4; // Adjusts the distance from 0 to max; larger divisors -> more distance
        int n = (int)((delta*SCALE)/minimumScrollMovement);
        if (n > 4*SCALE) n=(int)(4*SCALE);
        return (int)((INTERVALNs/SCALE)*(4*SCALE-n));
    }

    private float prevX = 0.0f;
    private float prevY = 0.0f;

    @SuppressWarnings("SameReturnValue")
    private boolean onMove(MotionEvent ev) {
        final float x = ev.getX();
        final float y = 10000 - ev.getY(); // make it top down
        final long nanoT = System.nanoTime();

        float deltaX = x-startX;
        float deltaY = y-startY;
        long deltaT = nanoT - startT;

        switch (state) {
        case 0:
            if (deltaT > INTERVALNs * 4) {
                // It's been long enough since "down" touch to figure out the initial direction
                if (Math.abs(Math.abs(deltaX) - Math.abs(deltaY)) > minimumScrollMovement) {
                    // movement has a dominant direction
                    startT = nanoT;
                    state = 1;

                    prevX = x;
                    prevY = y;

                    // convert direction to one of 4 ways.
                    if (Math.abs(deltaX) > Math.abs(deltaY)) {
                        // left/right
                        if (deltaX > 0) {
                            direction = Direction.RIGHT;
                        }
                        else {
                            direction = Direction.LEFT;
                        }
                        delayNs = movementToDelay(deltaX);
                    }
                    else {
                        // up/down
                        if (deltaY > 0) {
                            direction = Direction.UP;
                        }
                        else {
                            direction = Direction.DOWN;
                        }
                        delayNs = movementToDelay(deltaY);
                    }
                    counter = 0;

                    // Long delay for the first one (allow the user to stop it)
                    handler.postDelayed(this::onTick, 4 * INTERVAL);
                    listener.onTouchRate(direction, counter);
                }
            }
            break;

        case 1: {
            // Each motion recomputes the delay from when the last tick was sent to the caller until
            // the next one should be sent. The onTick() below sends it when it's time.
            // If the direction is reversed, change it and reset the counter.
            switch (direction) {
            case UP:
            case DOWN: {
                if (Math.abs(prevY - y) < minimumScrollMovement / 3) {
                    // Ignore small motions
                    break;
                }
                if (y < prevY && direction == Direction.UP) {
                    // The user reversed
                    direction = Direction.DOWN;
                    listener.onTouchRate(direction, REVERSE);
                    counter = -1;
                    startY = y;
                    deltaY = 0.0f;
                }
                else if (y > prevY && direction == Direction.DOWN) {
                    // The user reversed
                    direction = Direction.UP;
                    listener.onTouchRate(direction, REVERSE);
                    counter = -1;
                    startY = y;
                    deltaY = 0.0f;
                }

                delayNs = movementToDelay(deltaY);
                prevY = y;
                break;
            }
            case LEFT:
            case RIGHT: {
                if (Math.abs(prevX - x) < minimumScrollMovement / 3) {
                    // Ignore small motions
                    break;
                }
                if (x < prevX && direction == Direction.RIGHT) {
                    // The user reversed
                    direction = Direction.LEFT;
                    listener.onTouchRate(direction, REVERSE);
                    counter = -1;
                    startX = x;
                    deltaX = 0.0f;
                }
                else if (x > prevX && direction == Direction.LEFT) {
                    // The user reversed
                    direction = Direction.RIGHT;
                    listener.onTouchRate(direction, REVERSE);
                    counter = -1;
                    startX = x;
                    deltaX = 0.0f;
                }
                delayNs = movementToDelay(deltaX);
                prevX = x;
                break;
            }}
            break;
        }}

        return false; // allow key up to detect a tap
    }

    private void onTick() {
        // Short (potential) intervals
        handler.postDelayed(this::onTick, (int)(INTERVAL/SCALE)/2);

        final long nanoT = System.nanoTime();
        final long deltaT = nanoT-startT;

        if (deltaT > delayNs) {
            counter++;
            startT = nanoT;
            listener.onTouchRate(direction, counter);
        }
    }
}
