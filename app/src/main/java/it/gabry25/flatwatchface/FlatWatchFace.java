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
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.SystemProviders;
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
        private static final float MINUTES_STROKE_WIDTH = 13f;
        private static final float DIGITAL_TIME_SIZE = 65;
        private static final float DIGITAL_DATE_SIZE = 25;

        private boolean mRegisteredTimeZoneReceiver = false;
        private float mDateYOffset;
        private float mTimeYOffset=15;
        private int mCenterYOffset=20;
        private Paint mBackgroundPaint;
        private Paint mHandHourPaint;
        private Paint mMinuteHandPaint;
        private Paint mCirclePaint;
        private Bitmap mBackgroundImage;
        private Paint mDatePaint;
        private Paint mTimePaint;
        private float mHourHandLength = 20;
        private float mHourHandOffset = 50;
        private float mMinuteHandOffset = 40;
        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;
        private float mScale = 1;
        RectF innerCircle;
        private DateFormat mDateFormat;
        private DateFormat mTimeFormat;
        private ComplicationDrawable[] mComplicationDrawables;
        //private SparseArray<ComplicationDrawable> mComplicationDrawables;
        private ComplicationData[] mComplicationDatas;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;
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
            mTimeFormat = SimpleDateFormat.getTimeInstance(DateFormat.SHORT,Locale.getDefault());
            // Initializes Digital Watch Face.
            mTimePaint = new Paint();
            mTimePaint.setTypeface(NORMAL_TYPEFACE);
            mTimePaint.setAntiAlias(true);
            mTimePaint.setColor(ContextCompat.getColor(getApplicationContext(),
                    R.color.digital_time));
            mTimePaint.setTextSize(DIGITAL_TIME_SIZE);
            mTimePaint.setTextAlign(Paint.Align.CENTER);
            mDatePaint = new Paint();
            mDatePaint.setTypeface(NORMAL_TYPEFACE);
            mDatePaint.setAntiAlias(true);
            mDatePaint.setColor(ContextCompat.getColor(getApplicationContext(),
                    R.color.digital_date));
            mDatePaint.setTextSize(DIGITAL_DATE_SIZE);
            mDatePaint.setTextAlign(Paint.Align.CENTER);

            // Initializes Complications
            mComplicationDrawables = new ComplicationDrawable[COMPLICATION_IDS.length];
            mComplicationDatas = new ComplicationData[COMPLICATION_IDS.length];
            //mComplicationDrawables = new SparseArray<>(COMPLICATION_IDS.length);
            for(int i=0;i<COMPLICATION_IDS.length;++i) {
                //mComplicationDrawables[i] = (ComplicationDrawable) getDrawable(R.drawable.custom_complication_styles);
                //mComplicationDrawables[i].setContext(getApplicationContext());
                mComplicationDrawables[i] = new ComplicationDrawable(getApplicationContext());
                mComplicationDrawables[i].setTextColorActive(Color.WHITE);
                mComplicationDrawables[i].setIconColorActive(Color.WHITE);
                mComplicationDrawables[i].setBorderStyleActive(ComplicationDrawable
                        .BORDER_STYLE_NONE);
            }

            setDefaultSystemComplicationProvider(LEFT_COMPLICATION_ID,
                    SystemProviders.WATCH_BATTERY,ComplicationData.TYPE_RANGED_VALUE);
            setDefaultSystemComplicationProvider(CENTER_COMPLICATION_ID,
                    SystemProviders.NEXT_EVENT,ComplicationData.TYPE_ICON);
            setDefaultSystemComplicationProvider(RIGHT_COMPLICATION_ID,
                    SystemProviders.MOST_RECENT_APP,ComplicationData.TYPE_SMALL_IMAGE);
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
            //mAmbient = inAmbientMode;
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
            innerCircle = new RectF(mMinuteHandOffset,mMinuteHandOffset,
                    mWidth-mMinuteHandOffset,mHeight-mMinuteHandOffset);
            mScale = ((float) width) / (float) mBackgroundImage.getWidth();
            mBackgroundImage = Bitmap.createScaledBitmap
                    (mBackgroundImage, (int)(mBackgroundImage.getWidth() * mScale),
                            (int)(mBackgroundImage.getHeight() * mScale), true);
            // Set complication size
            mCenterX = mWidth / 2f;
            mCenterY = mHeight / 2f;
            //mHourHandLength = mCenterX - mHourHandOffset - 20;
            mDateYOffset = mCenterY / 2.5f;

            Rect rect;
            int complicationSize = width/4;
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
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawBitmap(mBackgroundImage, 0, 0, mBackgroundPaint);
            }
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            String time = mTimeFormat.format(mCalendar.getTime());
            String date = mDateFormat.format(mCalendar.getTime());
            canvas.drawText(time, mCenterX, mCenterY - mTimeYOffset, mTimePaint);
            canvas.drawText(date, mCenterX , mCenterY - mDateYOffset, mDatePaint);
            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) +
                    mCalendar.get(Calendar.MINUTE) / 2f;
            // save the canvas state before we begin to rotate it
            canvas.save();
            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(mCenterX, mHourHandOffset,mCenterX,
                    mHourHandOffset + mHourHandLength + hoursRotation/15, mHandHourPaint);
            canvas.drawOval(innerCircle,mCirclePaint);
            canvas.drawArc(innerCircle,-90-hoursRotation,minutesRotation,
                    false,mMinuteHandPaint);
            // restore the canvas' original orientation.
            canvas.restore();
            for(ComplicationDrawable cd : mComplicationDrawables)
                cd.draw(canvas,now);
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