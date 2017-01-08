/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ca.menushka.leaguewatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.DisplayMetrics;
import android.view.SurfaceHolder;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.TimeZone;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class LeagueWatchFace extends CanvasWatchFaceService {
    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = 1000;

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<LeagueWatchFace.Engine> mWeakReference;

        public EngineHandler(LeagueWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            LeagueWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.ConnectionCallbacks, DataApi.DataListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;

        String[] weekDays = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        String[] monthes = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};

        Paint mHandPaint;
        Paint mSecondsHandPaint;
        Paint mMinutesHandPaint;
        Paint mHoursHandPaint;

        GoogleApiClient mGoogleApiClient;
        String savedText;
        Handler updateTimer;

        Bitmap savedBitmap;
        int transparency = 70;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .build();

            mGoogleApiClient.connect();

            setWatchFaceStyle(new WatchFaceStyle.Builder(LeagueWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            Resources resources = LeagueWatchFace.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mHandPaint = new Paint();
            mHandPaint.setColor(resources.getColor(R.color.analog_hands));
            mHandPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.ROUND);

            mSecondsHandPaint = new Paint();
            mSecondsHandPaint.setColor(resources.getColor(R.color.league_red));
            mSecondsHandPaint.setStrokeWidth(resources.getDimension(R.dimen.league_seconds_stroke));
            mSecondsHandPaint.setAntiAlias(true);
            mSecondsHandPaint.setStrokeCap(Paint.Cap.ROUND);

            mMinutesHandPaint = new Paint();
            mMinutesHandPaint.setColor(resources.getColor(R.color.league_white));
            mMinutesHandPaint.setStrokeWidth(resources.getDimension(R.dimen.league_minutes_stroke));
            mMinutesHandPaint.setAntiAlias(true);
            mMinutesHandPaint.setStrokeCap(Paint.Cap.ROUND);

            mHoursHandPaint = new Paint();
            mHoursHandPaint.setColor(resources.getColor(R.color.league_white));
            mHoursHandPaint.setStrokeWidth(resources.getDimension(R.dimen.league_hours_stroke));
            mHoursHandPaint.setAntiAlias(true);
            mHoursHandPaint.setStrokeCap(Paint.Cap.ROUND);

            mTime = new Time();

            updateTimer = new Handler();
            updateTimer.post(new Runnable() {
                @Override
                public void run() {
                    if (mGoogleApiClient.isConnected() || mGoogleApiClient.isConnecting()) {
                        mGoogleApiClient.disconnect();
                    }

                    mGoogleApiClient.connect();

                    updateTimer.postDelayed(this, 1000 * 60 * 10);
                }
            });
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mHandPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
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
            Resources resources = LeagueWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture

                    if (mGoogleApiClient.isConnected() || mGoogleApiClient.isConnecting()) {
                        mGoogleApiClient.disconnect();
                    }

                    mGoogleApiClient.connect();

                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
//                canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);
                Bitmap bitmap;
                if (savedBitmap == null) {
                    Drawable d = getResources().getDrawable(R.drawable.background);
                    bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.background);
                } else {
                    bitmap = savedBitmap;
                }

                //Background
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, (int) (bitmap.getWidth() * 1f / bitmap.getHeight() * bounds.height()), bounds.height(), false);
                canvas.drawBitmap(scaledBitmap, bounds.centerX() - (int) (bitmap.getWidth() * 1f / bitmap.getHeight() * bounds.height()) / 2, 0, null);

                //Overlay
                Paint semi_black = new Paint();
                semi_black.setARGB(255 * transparency / 100, 0, 0, 0);


                canvas.drawRect(bounds, semi_black);

                //Text
                Typeface bold = Typeface.createFromAsset(getAssets(), "beaufortforlol-bold.ttf");

                Paint textPaint = new Paint();
                textPaint.setTypeface(bold);
                textPaint.setColor(getResources().getColor(R.color.league_gold));
                textPaint.setShadowLayer(4f, 0, 2, Color.BLACK);

                String usernameText = savedText == null ? "" : savedText;
                String dayText = weekDays[mTime.weekDay];
                String dateText = String.format("%s %d, %d", monthes[mTime.month], mTime.monthDay, mTime.year);
                String connectingText = "Connecting...";

                Rect textBounds = new Rect();
                textPaint.setTextSize(24);
                textPaint.getTextBounds(usernameText, 0, usernameText.length(), textBounds);
                canvas.drawText(usernameText, bounds.centerX() - textBounds.width() / 2, bounds.centerY() - bounds.height() / 4 + textBounds.height() / 2, textPaint);

//                if (mGoogleApiClient.isConnecting()) {
//                    int tempY = bounds.centerY() - bounds.height() / 4 + textBounds.height() / 2;
//                    textPaint.setTextSize(20);
//                    textPaint.getTextBounds(connectingText, 0, connectingText.length(), textBounds);
//                    canvas.drawText(connectingText, bounds.centerX() - textBounds.width() / 2, tempY + textBounds.height(), textPaint);
//                }

                textPaint.setTextSize(20);
                textPaint.getTextBounds(dateText, 0, dateText.length(), textBounds);
                canvas.drawText(dateText, bounds.centerX() - textBounds.width() / 2, bounds.centerY() + bounds.height() / 4, textPaint);

                textPaint.setTextSize(22);
                textPaint.getTextBounds(dayText, 0, dayText.length(), textBounds);
                canvas.drawText(dayText, bounds.centerX() - textBounds.width() / 2, bounds.centerY() + bounds.height() / 4 + textBounds.height(), textPaint);
            }

            // Find the center. Ignore the window insets so that, on round watches with a
            // "chin", the watch face is centered on the entire screen, not just the usable
            // portion.


            float centerX = bounds.width() / 2f;
            float centerY = bounds.height() / 2f;

            float secRot = mTime.second / 30f * (float) Math.PI;
            int minutes = mTime.minute;
            float minRot = minutes / 30f * (float) Math.PI;
            float hrRot = ((mTime.hour + (minutes / 60f)) / 6f) * (float) Math.PI;

            float secLength = centerX - 20;
            float minLength = centerX - 40;
            float hrLength = centerX - 80;

            // Draw watch ticks
            for (int rot = 0; rot < 360; rot += 30) {
                Paint tick_color = new Paint();
                tick_color.setStrokeWidth(getResources().getDimension(R.dimen.league_hours_tick));
                tick_color.setAntiAlias(true);
                if (rot % 90 == 0) {
                    tick_color.setColor(getResources().getColor(R.color.league_white));
                    canvas.drawLine(centerX + (int) (Math.cos(rot / 180f * Math.PI) * 140),
                                    centerY + (int) (Math.sin(rot / 180f * Math.PI) * 140),
                                    centerX + (int) (Math.cos(rot / 180f * Math.PI) * 150),
                                    centerY + (int) (Math.sin(rot / 180f * Math.PI) * 150), tick_color);
                } else {
                    tick_color.setColor(getResources().getColor(R.color.league_gray));
                    canvas.drawLine(centerX + (int) (Math.cos(rot / 180f * Math.PI) * 160),
                                    centerY + (int) (Math.sin(rot / 180f * Math.PI) * 160),
                                    centerX + (int) (Math.cos(rot / 180f * Math.PI) * 170),
                                    centerY + (int) (Math.sin(rot / 180f * Math.PI) * 170), tick_color);
                }
            }

            if (!mAmbient) {
                float secX = (float) Math.sin(secRot) * secLength;
                float secY = (float) -Math.cos(secRot) * secLength;
                canvas.drawLine(centerX, centerY, centerX + secX, centerY + secY, mSecondsHandPaint);
            }

            float minX = (float) Math.sin(minRot) * minLength;
            float minY = (float) -Math.cos(minRot) * minLength;
            canvas.drawLine(centerX, centerY, centerX + minX, centerY + minY, mMinutesHandPaint);

            float hrX = (float) Math.sin(hrRot) * hrLength;
            float hrY = (float) -Math.cos(hrRot) * hrLength;
            canvas.drawLine(centerX, centerY, centerX + hrX, centerY + hrY, mHoursHandPaint);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
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
            LeagueWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            LeagueWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
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

        @Override
        public void onConnected(Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, this);

            PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/league");
            putDataMapReq.getDataMap().putLong("time", System.currentTimeMillis());
            PutDataRequest request = putDataMapReq.asPutDataRequest();
            Wearable.DataApi.putDataItem(mGoogleApiClient, request);
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            for (DataEvent event : dataEvents) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem item = event.getDataItem();

                    if (item.getUri().getPath().equals("/league_back")) {
                        DataMapItem dataMapItem = DataMapItem.fromDataItem(item);
                        Asset profileAsset = dataMapItem.getDataMap().getAsset("image");

                        savedText = dataMapItem.getDataMap().getString("text");
                        new ImageDecode(mGoogleApiClient, profileAsset, new ImageCallback() {
                            @Override
                            public void onResult(Bitmap bitmap) {
                                savedBitmap = bitmap;
                            }
                        }).execute();
                    }
                }
            }
        }
    }
}

class ImageDecode extends AsyncTask<Void, Void, Void> {

    GoogleApiClient mGoogleApiClient;
    Asset asset;
    ImageCallback callback;
    Bitmap bitmap;

    ImageDecode(GoogleApiClient mGoogleApiClient, Asset asset, ImageCallback callback) {
        this.mGoogleApiClient = mGoogleApiClient;
        this.asset = asset;
        this.callback = callback;
    }

    @Override
    protected Void doInBackground(Void... params) {
        if (asset == null) {
            throw new IllegalArgumentException("Asse t must be non-null");
        }
        ConnectionResult result = mGoogleApiClient.blockingConnect();

        if (!result.isSuccess()) {
            return null;
        }
        // convert asset into a file descriptor and block until it's ready
        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                mGoogleApiClient, asset).await().getInputStream();
        mGoogleApiClient.disconnect();

        if (assetInputStream == null) {
            return null;
        }

        bitmap = BitmapFactory.decodeStream(assetInputStream);
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        callback.onResult(bitmap);
    }
}

interface ImageCallback {
    void onResult(Bitmap bitmap);
}
