package it.gabry25.flatwatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 * <p>
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
public class FlatWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. Defaults to one second
     * because the watch face needs to update seconds in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<FlatWatchFace.Engine> mWeakReference;

        public EngineHandler(FlatWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            FlatWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private static final float STROKE_WIDTH = 6f;
        private static final float MINUTES_STROKE_WIDTH = 13f;
        private static final float DIGITAL_TIME_SIZE = 55;
        private static final float DIGITAL_DATE_SIZE = 25;

        private boolean mRegisteredTimeZoneReceiver = false;
        private float mDateYOffset;
        private float mTimeYOffset=-15;
        private Paint mBackgroundPaint;
        private Paint mHandHourPaint;
        private Paint mMinuteHandPaint;
        private Paint mCirclePaint;
        private Bitmap mBackgroundImage;
        private Paint mDatePaint;
        private Paint mTimePaint;

        private float mHourHandLength;
        private float mHourHandOffset = 50;
        private float mMinuteHandOffset = 40;
        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;
        private float mScale = 1;

        private DateFormat mDateFormat;
        private DateFormat mTimeFormat;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        private boolean mLowBitAmbient;
        //private boolean mBurnInProtection;
        //private boolean mAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(FlatWatchFace.this)
                    .setAcceptsTapEvents(true)
                    .build());

            mCalendar = Calendar.getInstance();

            // Initializes background.
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(
                    ContextCompat.getColor(getApplicationContext(), R.color.background));

            mBackgroundImage = BitmapFactory.decodeResource(getResources(),R.drawable.background);

            // Initializes Analog Watch Face.
            mMinuteHandPaint = new Paint();
            mMinuteHandPaint.setStyle(Paint.Style.STROKE);
            mMinuteHandPaint.setColor(Color.CYAN);
            mMinuteHandPaint.setStrokeWidth(MINUTES_STROKE_WIDTH);
            mMinuteHandPaint.setAntiAlias(true);
            mMinuteHandPaint.setStrokeCap(Paint.Cap.ROUND);
            mCirclePaint = new Paint();
            mCirclePaint.setStyle(Paint.Style.STROKE);
            mCirclePaint.setColor(Color.BLACK);
            mCirclePaint.setStrokeWidth(MINUTES_STROKE_WIDTH+5);
            mCirclePaint.setAntiAlias(true);
            mCirclePaint.setStrokeCap(Paint.Cap.ROUND);
            mHandHourPaint = new Paint();
            mHandHourPaint.setColor(Color.MAGENTA);
            mHandHourPaint.setStrokeWidth(STROKE_WIDTH);
            mHandHourPaint.setAntiAlias(true);
            mHandHourPaint.setStrokeCap(Paint.Cap.SQUARE);

            //Initializes the format
            //mDateFormat = SimpleDateFormat.getDateInstance(DateFormat.FULL);
            mDateFormat = new SimpleDateFormat("EE, dd MMM",Locale.getDefault());
            mTimeFormat = SimpleDateFormat.getTimeInstance(DateFormat.SHORT,Locale.ITALIAN);

            // Initializes Digital Watch Face.
            mTimePaint = new Paint();
            mTimePaint.setTypeface(NORMAL_TYPEFACE);
            mTimePaint.setAntiAlias(true);
            mTimePaint.setColor(
                    ContextCompat.getColor(getApplicationContext(), R.color.digital_time));
            mTimePaint.setTextSize(DIGITAL_TIME_SIZE);
            mTimePaint.setTextAlign(Paint.Align.CENTER);
            mDatePaint = new Paint();
            mDatePaint.setTypeface(NORMAL_TYPEFACE);
            mDatePaint.setAntiAlias(true);
            mDatePaint.setColor(
                    ContextCompat.getColor(getApplicationContext(), R.color.digital_date));
            mDatePaint.setTextSize(DIGITAL_DATE_SIZE);
            mDatePaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            FlatWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            FlatWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            //mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            //mAmbient = inAmbientMode;
            if (mLowBitAmbient) {
                mTimePaint.setAntiAlias(!inAmbientMode);
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mWidth = width;
            mHeight = height;

            mScale = ((float) width) / (float) mBackgroundImage.getWidth();
            mBackgroundImage = Bitmap.createScaledBitmap
                    (mBackgroundImage, (int)(mBackgroundImage.getWidth() * mScale),
                            (int)(mBackgroundImage.getHeight() * mScale), true);

            /*
             * Find the coordinates of the center point on the screen.
             * Ignore the window insets so that, on round watches
             * with a "chin", the watch face is centered on the entire screen,
             * not just the usable portion.
             */
            mCenterX = mWidth / 2f;
            mCenterY = mHeight / 2f;
            /*
             * Calculate the lengths of the watch hands and store them in member variables.
             */
            mHourHandLength = mCenterX - mHourHandOffset - 20;
            mDateYOffset = mCenterY / 4f;
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawBitmap(mBackgroundImage, 0, 0, mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            //String time = String.format(Locale.getDefault(),"%d:%02d",
                    //mCalendar.get(Calendar.HOUR),mCalendar.get(Calendar.MINUTE));
            String time = mTimeFormat.format(mCalendar.getTime());
            String date = mDateFormat.format(mCalendar.getTime());
            canvas.drawText(time, mCenterX, mCenterY - mTimeYOffset, mTimePaint);
            canvas.drawText(date, mCenterX , mCenterY - mDateYOffset, mDatePaint);

            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */

            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;

            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;

            // save the canvas state before we begin to rotate it
            canvas.save();

            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(mCenterX, mCenterY - mHourHandLength, mCenterX, mHourHandOffset, mHandHourPaint);
            RectF innerCircle = new RectF(mMinuteHandOffset,mMinuteHandOffset,
                    mWidth-mMinuteHandOffset,mHeight-mMinuteHandOffset);
            canvas.drawOval(innerCircle,mCirclePaint);
            canvas.drawArc(innerCircle,-90-hoursRotation,minutesRotation,
                    false,mMinuteHandPaint);
            // restore the canvas' original orientation.
            canvas.restore();
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
