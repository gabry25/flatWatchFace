package it.gabry25.flatwatchface;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.VectorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.rendering.ComplicationDrawable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;
import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
/**
 * Digital and analog watch face with date and complications. On devices
 * with low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class FlatWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    /**
     * Update rate in milliseconds for interactive mode. Defaults to one second
     * because the watch face needs to update seconds in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.MINUTES.toMillis(1);

    private static final int LEFT_COMPLICATION_ID = 0;
    private static final int CENTER_COMPLICATION_ID = 1;
    private static final int RIGHT_COMPLICATION_ID = 2;
    private static final int[] COMPLICATION_IDS = {LEFT_COMPLICATION_ID,
            CENTER_COMPLICATION_ID,RIGHT_COMPLICATION_ID};

    private static final int[] COMPLICATION_SUPPORTED_TYPES = {
            ComplicationData.TYPE_RANGED_VALUE,
            ComplicationData.TYPE_ICON,
            ComplicationData.TYPE_SHORT_TEXT,
            ComplicationData.TYPE_SMALL_IMAGE
    };

    public static int getLeftComplicationId() {
        return LEFT_COMPLICATION_ID;
    }

    public static int getCenterComplicationId() {
        return CENTER_COMPLICATION_ID;
    }

    public static int getRightComplicationId() {
        return RIGHT_COMPLICATION_ID;
    }

    public static int[] getComplicationIds() {
        return COMPLICATION_IDS;
    }

    public static int[] getComplicationSupportedTypes(){
        return COMPLICATION_SUPPORTED_TYPES;
    }

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
        private EngineHandler(FlatWatchFace.Engine reference) {
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

        private float mDigitalTimeSize;
        private float mDigitalDateSize;
        private float mMinutesStrokeWidth;
        private boolean mRegisteredTimeZoneReceiver = false;
        private float mDateYOffset;
        private float mTimeYOffset;
        private int mCenterYOffset;
        private Paint mHandHourPaint;
        private Paint mMinuteHandPaint;
        private Paint mCirclePaint;
        private VectorDrawable mBackgroundImage;
        private Paint mDatePaint;
        private Paint mTimePaint;
        private float mMinuteCircleOffset;
        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;
        RectF innerCircle;
        private DateFormat mDateFormat;
        private DateFormat mTimeFormat;
        private ComplicationDrawable[] mComplicationDrawables;
        private ComplicationData[] mComplicationDatas;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;
        private boolean mAmbient;

        private void initVariables(Resources res){
            mMinutesStrokeWidth = res.getDimension(R.dimen.minutes_stroke_width);
            mMinuteCircleOffset = res.getDimension(R.dimen.minute_circle_offset);
            mTimeYOffset = res.getDimension(R.dimen.time_vertical_offset);
            mDateYOffset = res.getDimension(R.dimen.date_vertical_offset);
            mCenterYOffset = (int)res.getDimension(R.dimen.center_vertical_offset);
            mDigitalTimeSize = res.getDimension(R.dimen.time_text_size);
            mDigitalDateSize = res.getDimension(R.dimen.date_text_size);
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setWatchFaceStyle(new WatchFaceStyle.Builder(FlatWatchFace.this)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources res = getResources();
            initVariables(res);
            mCalendar = Calendar.getInstance();
            // Initializes background.
            mBackgroundImage = (VectorDrawable) getDrawable(R.drawable.background);
            // Initializes Analog Watch Face.
            mMinuteHandPaint = new Paint();
            mMinuteHandPaint.setStyle(Paint.Style.STROKE);
            mMinuteHandPaint.setColor(ContextCompat.getColor(getApplicationContext(),
                    R.color.analog_minutes));
            mMinuteHandPaint.setStrokeWidth(mMinutesStrokeWidth);
            mMinuteHandPaint.setAntiAlias(true);
            mMinuteHandPaint.setStrokeCap(Paint.Cap.ROUND);
            mCirclePaint = new Paint();
            mCirclePaint.setStyle(Paint.Style.STROKE);
            mCirclePaint.setColor(ContextCompat.getColor(getApplicationContext(),
                    R.color.default_dark));
            mCirclePaint.setStrokeWidth(mMinutesStrokeWidth +5);
            mCirclePaint.setAntiAlias(true);
            mCirclePaint.setStrokeCap(Paint.Cap.ROUND);
            mHandHourPaint = new Paint();
            mHandHourPaint.setColor(ContextCompat.getColor(getApplicationContext(),
                    R.color.analog_hours));
            mHandHourPaint.setStrokeWidth(STROKE_WIDTH);
            mHandHourPaint.setAntiAlias(true);
            mHandHourPaint.setStrokeCap(Paint.Cap.SQUARE);
            //Initializes the format
            //mDateFormat = SimpleDateFormat.getDateInstance(DateFormat.FULL);
            mDateFormat = new SimpleDateFormat("EE, dd MMM",Locale.getDefault());
            mTimeFormat = SimpleDateFormat.getTimeInstance(DateFormat.SHORT,Locale.getDefault());
            // Initializes Digital Watch Face.
            mTimePaint = new Paint();
            mTimePaint.setTypeface(NORMAL_TYPEFACE);
            mTimePaint.setAntiAlias(true);
            mTimePaint.setColor(ContextCompat.getColor(getApplicationContext(),
                    R.color.default_bright));
            mTimePaint.setTextSize(mDigitalTimeSize);
            mTimePaint.setTextAlign(Paint.Align.CENTER);
            mDatePaint = new Paint();
            mDatePaint.setTypeface(NORMAL_TYPEFACE);
            mDatePaint.setAntiAlias(true);
            mDatePaint.setColor(ContextCompat.getColor(getApplicationContext(),
                    R.color.default_middle));
            mDatePaint.setTextSize(mDigitalDateSize);
            mDatePaint.setTextAlign(Paint.Align.CENTER);

            // Initializes Complications
            mComplicationDrawables = new ComplicationDrawable[COMPLICATION_IDS.length];
            mComplicationDatas = new ComplicationData[COMPLICATION_IDS.length];
            for(int i=0;i<COMPLICATION_IDS.length;++i) {
                mComplicationDrawables[i] = (ComplicationDrawable) getDrawable(R.drawable.complication_styles);
                mComplicationDrawables[i].setContext(getApplicationContext());
            }
            setActiveComplications(COMPLICATION_IDS);
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
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            for(ComplicationDrawable cd : mComplicationDrawables){
                cd.setLowBitAmbient(mLowBitAmbient);
                cd.setBurnInProtection(mBurnInProtection);
            }
        }
        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }
        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            mAmbient = inAmbientMode;
            if (mLowBitAmbient) {
                mTimePaint.setAntiAlias(!inAmbientMode);
            }
            for(ComplicationDrawable cd : mComplicationDrawables)
                cd.setInAmbientMode(inAmbientMode);
            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /*
         * Called when there is updated data for a complication id.
         */
        @Override
        public void onComplicationDataUpdate(int complicationId,
                                             ComplicationData complicationData) {
            // Adds/updates active complication data in the array.
            mComplicationDatas[complicationId] = complicationData;
            // Updates correct ComplicationDrawable with updated data.
            mComplicationDrawables[complicationId].setComplicationData(complicationData);
            invalidate();
        }
        /**
         * Captures tap event (and tap type) and toggles the background color if the
         * user finishes a tap.
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
                    for(ComplicationDrawable cd : mComplicationDrawables)
                        if(cd.onTap(x, y))
                            return;
                    break;
            }
            invalidate();
        }
        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mWidth = width;
            mHeight = height;
            innerCircle = new RectF(mMinuteCircleOffset,mMinuteCircleOffset,
                    mWidth-mMinuteCircleOffset,mHeight-mMinuteCircleOffset);
            mBackgroundImage.setBounds(0,0,width,height);
            // Set complication size
            mCenterX = mWidth / 2f;
            mCenterY = mHeight / 2f;
            //mHourHandLength = mCenterX - mHourHandOffset - 20;

            Rect rect;
            int complicationSize = width/5;
            rect = new Rect(width/6,height/2,
                    width/6+complicationSize,height/2+complicationSize);
            mComplicationDrawables[0].setBounds(rect);
            rect = new Rect(width/2-complicationSize/2,height/2+mCenterYOffset,
                    width/2+complicationSize/2,height/2+mCenterYOffset+complicationSize);
            mComplicationDrawables[1].setBounds(rect);
            rect = new Rect(5*width/6-complicationSize,height/2,
                    5*width/6,height/2+complicationSize);
            mComplicationDrawables[2].setBounds(rect);
        }
        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            // Draw the background.
            if (mAmbient)
                canvas.drawColor(Color.BLACK);
            else
                mBackgroundImage.draw(canvas);
            // Draw complications
            for(ComplicationDrawable cd : mComplicationDrawables)
                cd.draw(canvas,now);
            // Draw digital part
            String time = mTimeFormat.format(mCalendar.getTime());
            String date = mDateFormat.format(mCalendar.getTime());
            canvas.drawText(time, mCenterX, mCenterY - mTimeYOffset, mTimePaint);
            canvas.drawText(date, mCenterX , mCenterY - mDateYOffset, mDatePaint);
            // avoid drawing the analog part in ambient
            if(!mAmbient) {
                final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;
                final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) +
                        mCalendar.get(Calendar.MINUTE) / 2f;
                // save the canvas state before we begin to rotate it
                canvas.save();
                canvas.rotate(hoursRotation, mCenterX, mCenterY);
                canvas.drawLine(mCenterX, 0, mCenterX, mMinuteCircleOffset, mHandHourPaint);
                canvas.drawOval(innerCircle, mCirclePaint);
                canvas.drawArc(innerCircle, -90 - hoursRotation, minutesRotation,
                        false, mMinuteHandPaint);
                // restore the canvas' original orientation.
                canvas.restore();
            }
        }
        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't
         * currently or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() { //FIXME is it needed?
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }
        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running.
         * The timer should only run when we're visible and in interactive mode.
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