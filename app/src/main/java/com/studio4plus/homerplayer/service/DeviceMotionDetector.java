package com.studio4plus.homerplayer.service;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.annotation.NonNull;

import static android.content.Context.SENSOR_SERVICE;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;

/**
 * Detect whether the device is being shaken or still.
 */
@Singleton // and in use in only one place at a time
public class DeviceMotionDetector implements SensorEventListener {

    public interface Listener {
        void onSignificantMotion();
        void onFaceDownStill();
        void onFaceUpStill();
    }

    // These values seem to work pretty well, but further actual practice tuning
    // may be in order.
    private static final float MAX_STILL_TOLERANCE = 1f;
    private static final float MAX_FACEDOWN_DEVIATION = 2f;
    private static final float MIN_SIGNIFICANT_MOTION = 0.5f;
    private static final long MIN_TIME_WINDOW = TimeUnit.MILLISECONDS.toNanos(500);
    private static final long AVG_SMOOTH_TIME_NANOS = TimeUnit.MILLISECONDS.toNanos(500);

    private final float[] avgAcceleration = new float[3];

    private long previousTimestamp = 0;
    private final float[] previousValues = new float[3];
    private SamplesQueue queue;

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private Listener listener;

    static private DeviceMotionDetector deviceMotionDetector;

    static private boolean enabled = false;

    // Remember the last FACE UP/DOWN we sent and don't repeat. (Send only changes in that.)
    static private MotionType priorType = MotionType.OTHER;

    protected enum MotionType {
        FACE_DOWN,
        FACE_UP,
        ACCELERATING,
        OTHER
    }

    @NonNull
    public static DeviceMotionDetector getDeviceMotionDetector(Context ctx, @NonNull Listener listener) {
        if (deviceMotionDetector == null) {
            deviceMotionDetector = new DeviceMotionDetector(ctx);
        }
        deviceMotionDetector.listener = listener;
        return deviceMotionDetector;
    }

    // Singleton private constructor
    private DeviceMotionDetector(Context context) {
        // Get the sensor. If it isn't there null things so that callers can simply
        // make the call and not have to think about whether it's there or not.
        SensorManager tempSm = (SensorManager) context.getSystemService(SENSOR_SERVICE);
        if (tempSm == null) {
            accelerometer = null;
            sensorManager = null;
            return;
        }
        // Ideally TYPE_LINEAR_ACCELERATION but it doesn't always work, so we use the
        // raw one and compensate for g ourselves.
        accelerometer = tempSm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer == null) {
            sensorManager = null;
            return;
        }
        sensorManager = tempSm;
        queue = new SamplesQueue();
    }

    public void enable() {
        enabled = true;
        // Zero out history... we might have moved a long way while we weren't looking.
        previousTimestamp = 0;
        if (sensorManager == null) {
            return;
        }
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
    }

    public void disable() {
        enabled = false;
        if (sensorManager == null) {
            return;
        }
        sensorManager.unregisterListener(this, accelerometer);
        queue = new SamplesQueue();
    }

    @SuppressWarnings("unused")
    public boolean hasSensors() {
        return sensorManager != null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        final float x = event.values[0];
        final float y = event.values[1];
        final float z = event.values[2];

        if (previousTimestamp == 0) {
            // capture and then ignore the first sample so we get at least a little history
            previousTimestamp = event.timestamp;
            for (int i = 0; i < avgAcceleration.length; ++i) {
                previousValues[i] = avgAcceleration[i] = event.values[i];
            }
            return;
        }

        // alpha is (for me) around 0.13, which means that new values don't contribute much each.
        // A smaller smoothing interval would increase the sensitivity at a reduction in accuracy.
        // avgAcceleration[0-2] is the result of a low-pass filter giving us vector g, more or less
        long deltaTimeNano = event.timestamp - previousTimestamp;
        float alpha = ((float) deltaTimeNano) / AVG_SMOOTH_TIME_NANOS;
        float sampleDeltaSum = 0f;
        float accDeltaSum = 0f;
        for (int i = 0; i < avgAcceleration.length; ++i) {
            avgAcceleration[i] = alpha * event.values[i] + (1f - alpha) * avgAcceleration[i];
            final float a = Math.abs(event.values[i]);
            accDeltaSum += Math.abs(a - Math.abs(avgAcceleration[i]));
            sampleDeltaSum += Math.abs(a - Math.abs(previousValues[i]));
            previousValues[i] = event.values[i];
        }

        // accDeltaSum is the sum of the absolute deviations from g (still == 0)
        // sampleDeltaSum is the sum of the changes from the last sample (still == 0)
        // These are almost never exactly zero due to mechanical and electrical jitter

        System.arraycopy(event.values, 0, previousValues, 0, event.values.length);

        final float acceleration = (float) Math.sqrt(x * x + y * y + z * z);
        previousTimestamp = event.timestamp;

        boolean isAccelerating = accDeltaSum > MIN_SIGNIFICANT_MOTION;
        boolean isStill = sampleDeltaSum < MAX_STILL_TOLERANCE;
        isStill &= Math.abs(acceleration - Math.abs(z)) < MAX_FACEDOWN_DEVIATION;

        MotionType sampleType =
                isStill ? (z < 0 ? MotionType.FACE_DOWN : MotionType.FACE_UP)
                : isAccelerating ? MotionType.ACCELERATING
                : MotionType.OTHER;

        // Add this sample, and from that determine the overall motion type if a majority
        // has been accumulated yet.
        queue.add(event.timestamp, sampleType);
        MotionType detectedType = queue.getMotionType();

        // Purge the queue of entries older than the time window
        queue.purgeOld(event.timestamp - MIN_TIME_WINDOW);

        // Purge the queue completely if we resolved a motion type.
        if (detectedType != MotionType.OTHER) {
            queue = new SamplesQueue();
        }

        // events could potentially come in after disable, so  don't pass them on
        if (enabled) {
            switch (detectedType) {
                case FACE_DOWN:
                    if (priorType == MotionType.FACE_DOWN)
                        break;
                    priorType = MotionType.FACE_DOWN;
                    listener.onFaceDownStill();
                    break;
                case FACE_UP:
                    if (priorType == MotionType.FACE_UP)
                        break;
                    priorType = MotionType.FACE_UP;
                    listener.onFaceUpStill();
                    break;
                case ACCELERATING:
                    listener.onSignificantMotion();
                    break;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }


    // Inspired by Square's seismic ShakeDetector.
    private static class SamplesQueue {
        private static final int MIN_LENGTH = 4;

        private static class SampleEntry {
            MotionType motionType;
            private long timestamp;

            SampleEntry next = null;
        }

        private SampleEntry newest = null;
        private SampleEntry oldest = null;
        private int count = 0;
        private int acceleratingCount = 0;
        private int faceDownCount = 0;
        private int faceUpCount = 0;

        private SampleEntry unused = null;

        // Insert an entry in the linked list, incrementing the counter for its type
        void add(long timestamp, MotionType type) {
            SampleEntry newSample;
            if (unused != null) {
                newSample = unused;
                unused = newSample.next;
            } else {
                newSample = new SampleEntry();
            }
            newSample.timestamp = timestamp;
            newSample.motionType = type;
            newSample.next = null;

            if (newest != null)
              newest.next = newSample;
            newest = newSample;
            if (oldest == null)
                oldest = newest;
            switch (type) {
                case ACCELERATING:
                    ++acceleratingCount;
                    break;
                case FACE_DOWN:
                    ++faceDownCount;
                    break;
                case FACE_UP:
                    ++faceUpCount;
                    break;
            }
            ++count;
        }

        // Purge old entries (prior to timestamp), retaining a minimum list length
        void purgeOld(long timestamp) {
            while(oldest != null && count > MIN_LENGTH && oldest.timestamp < timestamp) {
                SampleEntry removed = oldest;
                oldest = removed.next;
                remove(removed);
            }
        }

        // For the entries in the list, which one (if any) constitutes 75% of the reports
        // (We're caching counts.)
        MotionType getMotionType() {
            if (newest.timestamp - oldest.timestamp > MIN_TIME_WINDOW) {
                int threshold = (count >> 1) + (count >> 2);  // count * 0.75
                if (faceDownCount >= threshold)
                    return MotionType.FACE_DOWN;
                if (faceUpCount >= threshold)
                    return MotionType.FACE_UP;
                if (acceleratingCount >= threshold)
                    return MotionType.ACCELERATING;
            }

            return MotionType.OTHER;
        }

        // Remove an entry, decrementing the count of its type
        private void remove(SampleEntry sample) {
            --count;
            switch (sample.motionType) {
                case ACCELERATING:
                    --acceleratingCount;
                    break;
                case FACE_DOWN:
                    --faceDownCount;
                    break;
                case FACE_UP:
                    --faceUpCount;
                    break;
            }
            sample.next = unused;
            unused = sample;
        }
    }

}