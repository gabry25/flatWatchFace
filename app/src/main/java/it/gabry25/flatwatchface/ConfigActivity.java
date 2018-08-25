package it.gabry25.flatwatchface;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.wearable.complications.ComplicationHelperActivity;
import android.support.wearable.complications.ComplicationProviderInfo;
import android.support.wearable.complications.ProviderChooserIntent;
import android.support.wearable.complications.ProviderInfoRetriever;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import java.util.concurrent.Executors;

public class ConfigActivity extends Activity implements View.OnClickListener {

    static final int COMPLICATION_CONFIG_REQUEST_CODE = 1234;

    /**
     * Used by associated watch face ({@link FlatWatchFace}) to let this
     * configuration Activity know which complication locations are supported, their ids, and
     * supported complication data types.
     */

    private int mLeftComplicationId = FlatWatchFace.getLeftComplicationId();
    private int mCenterComplicationId = FlatWatchFace.getCenterComplicationId();
    private int mRightComplicationId = FlatWatchFace.getRightComplicationId();

    // Selected complication id by user.
    private int mSelectedComplicationId = -1;

    // ComponentName used to identify a specific service that renders the watch face.
    private ComponentName mWatchFaceComponentName;

    // Required to retrieve complication data from watch face for preview.
    private ProviderInfoRetriever mProviderInfoRetriever;

    private ImageView mLeftComplicationBackground;
    private ImageView mCenterComplicationBackground;
    private ImageView mRightComplicationBackground;

    private ImageButton mLeftComplication;
    private ImageButton mCenterComplication;
    private ImageButton mRightComplication;

    private Drawable mDefaultAddComplicationDrawable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_config);

        mDefaultAddComplicationDrawable = getDrawable(R.drawable.add_complication);

        mWatchFaceComponentName = new ComponentName(getApplicationContext(), FlatWatchFace.class);

        // Sets up left complication preview.
        mLeftComplicationBackground = findViewById(R.id.left_complication_background);
        mLeftComplication = findViewById(R.id.left_complication);
        mLeftComplication.setOnClickListener(this);
        // Sets default as "Add Complication" icon.
        mLeftComplication.setImageDrawable(mDefaultAddComplicationDrawable);
        mLeftComplicationBackground.setVisibility(View.INVISIBLE);

        // Sets up center complication preview.
        mCenterComplicationBackground = findViewById(R.id.center_complication_background);
        mCenterComplication = findViewById(R.id.center_complication);
        mCenterComplication.setOnClickListener(this);
        // Sets default as "Add Complication" icon.
        mCenterComplication.setImageDrawable(mDefaultAddComplicationDrawable);
        mCenterComplicationBackground.setVisibility(View.INVISIBLE);

        // Sets up right complication preview.
        mRightComplicationBackground = findViewById(R.id.right_complication_background);
        mRightComplication = findViewById(R.id.right_complication);
        mRightComplication.setOnClickListener(this);
        // Sets default as "Add Complication" icon.
        mRightComplication.setImageDrawable(mDefaultAddComplicationDrawable);
        mRightComplicationBackground.setVisibility(View.INVISIBLE);

        mProviderInfoRetriever = new ProviderInfoRetriever(getApplicationContext(),
                Executors.newCachedThreadPool());
        mProviderInfoRetriever.init();
        retrieveInitialComplicationsData();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mProviderInfoRetriever.release();
    }

    public void retrieveInitialComplicationsData() {
        final int[] complicationIds = FlatWatchFace.getComplicationIds();
        mProviderInfoRetriever.retrieveProviderInfo(
                new ProviderInfoRetriever.OnProviderInfoReceivedCallback() {
                    @Override
                    public void onProviderInfoReceived(int watchFaceComplicationId,
                            @Nullable ComplicationProviderInfo complicationProviderInfo) {
                        updateComplicationViews(watchFaceComplicationId, complicationProviderInfo);
                    }
                },
                mWatchFaceComponentName,complicationIds);
    }

    @Override
    public void onClick(View view) {
        if (view.equals(mLeftComplication))
            launchComplicationHelperActivity(mLeftComplicationId);
        else if (view.equals(mCenterComplication))
            launchComplicationHelperActivity(mCenterComplicationId);
        else if (view.equals(mRightComplication))
            launchComplicationHelperActivity(mRightComplicationId);
    }

    // Verifies the watch face supports the complication location, then launches the helper
    // class, so user can choose their complication data provider.
    private void launchComplicationHelperActivity(int complicationLocation) {
        mSelectedComplicationId = complicationLocation;
        if (mSelectedComplicationId >= 0) {
            int[] supportedTypes = FlatWatchFace.getComplicationSupportedTypes();
            startActivityForResult(
                    ComplicationHelperActivity.createProviderChooserHelperIntent(
                            getApplicationContext(),
                            mWatchFaceComponentName,
                            mSelectedComplicationId,
                            supportedTypes),
                    COMPLICATION_CONFIG_REQUEST_CODE);

        }
    }

    public void updateComplicationViews(int watchFaceComplicationId,
                                        ComplicationProviderInfo complicationProviderInfo) {

        if (watchFaceComplicationId == mLeftComplicationId) {
            if (complicationProviderInfo != null) {
                mLeftComplication.setImageIcon(complicationProviderInfo.providerIcon);
                mLeftComplicationBackground.setVisibility(View.VISIBLE);

            } else {
                mLeftComplication.setImageDrawable(mDefaultAddComplicationDrawable);
                mLeftComplicationBackground.setVisibility(View.INVISIBLE);
            }
        } else if (watchFaceComplicationId == mCenterComplicationId) {
            if (complicationProviderInfo != null) {
                mCenterComplication.setImageIcon(complicationProviderInfo.providerIcon);
                mCenterComplicationBackground.setVisibility(View.VISIBLE);

            } else {
                mCenterComplication.setImageDrawable(mDefaultAddComplicationDrawable);
                mCenterComplicationBackground.setVisibility(View.INVISIBLE);
            }
        } else if (watchFaceComplicationId == mRightComplicationId) {
            if (complicationProviderInfo != null) {
                mRightComplication.setImageIcon(complicationProviderInfo.providerIcon);
                mRightComplicationBackground.setVisibility(View.VISIBLE);

            } else {
                mRightComplication.setImageDrawable(mDefaultAddComplicationDrawable);
                mRightComplicationBackground.setVisibility(View.INVISIBLE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == COMPLICATION_CONFIG_REQUEST_CODE && resultCode == RESULT_OK) {
            // Retrieves information for selected Complication provider.
            ComplicationProviderInfo complicationProviderInfo =
                    data.getParcelableExtra(ProviderChooserIntent.EXTRA_PROVIDER_INFO);
            if (mSelectedComplicationId >= 0)
                updateComplicationViews(mSelectedComplicationId, complicationProviderInfo);
        }
    }
}
