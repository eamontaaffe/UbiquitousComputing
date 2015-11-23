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

package au.com.taaffe.wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
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

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        boolean mRegisteredTimeZoneReceiver = false;

        Paint mBackgroundPaint;
        Paint mTextPaintHour;
        Paint mTextPaintMin;
        Paint mTextPaintColon;
        Paint mTextPaintDate;

        Paint mTextPaintHigh;
        Paint mTextPaintLow;
        Paint mLinePaint;


        boolean mAmbient;

        Time mTime;
        Date mDate;

        float mXCenter;
        float mYOffset;
        float mYOffsetDate;
        float mYOffsetTemp;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mYOffsetDate = resources.getDimension(R.dimen.digital_date_y_offset);
            mYOffsetTemp = resources.getDimension(R.dimen.digital_temp_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.digital_background));

            mTextPaintHour = new Paint();
            mTextPaintHour = createTextPaint(resources.getColor(R.color.digital_text));

            mTextPaintColon = new Paint();
            mTextPaintColon = createTextPaint(resources.getColor(R.color.digital_text));

            mTextPaintMin = new Paint();
            mTextPaintMin = createTextPaint(resources.getColor(R.color.digital_text));
            mTextPaintMin.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));

            mTextPaintDate = new Paint();
            mTextPaintDate = createTextPaint(resources.getColor(R.color.digital_text));
            mTextPaintDate.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));

            mTextPaintHigh = new Paint();
            mTextPaintHigh = createTextPaint(resources.getColor(R.color.digital_text));
//            mTextPaintHigh.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));

            mTextPaintLow = new Paint();
            mTextPaintLow = createTextPaint(resources.getColor(R.color.digital_text));
            mTextPaintLow.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));

            mLinePaint = new Paint();
            mLinePaint.setColor(resources.getColor(R.color.digital_text));


            mTime = new Time();
            mDate = new Date();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
                mDate.get
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
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float textSizeDate = resources.getDimension(isRound
                    ? R.dimen.digital_date_text_size_round : R.dimen.digital_date_text_size);
            float textSizeTemp= resources.getDimension(isRound
                    ? R.dimen.digital_temp_text_size_round : R.dimen.digital_temp_text_size);

            mTextPaintHour.setTextAlign(Paint.Align.RIGHT);
            mTextPaintMin.setTextAlign(Paint.Align.LEFT);
            mTextPaintColon.setTextAlign(Paint.Align.CENTER);
            mTextPaintDate.setTextAlign(Paint.Align.CENTER);
            mTextPaintHigh.setTextAlign(Paint.Align.CENTER);
            mTextPaintHigh.setTextAlign(Paint.Align.LEFT);


            mTextPaintHour.setTextSize(textSize);
            mTextPaintMin.setTextSize(textSize);
            mTextPaintColon.setTextSize(textSize);
            mTextPaintDate.setTextSize(textSizeDate);
            mTextPaintHigh.setTextSize(textSizeTemp);
            mTextPaintLow.setTextSize(textSizeTemp);
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
                    mTextPaintHour.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String textHour = String.format("%d", mTime.hour);
            String textMin = String.format("%02d", mTime.minute);
            String textColon = ":";
            String textDate = "FRI, JUL 14 2015";
            String textHigh = "25\u00B0";
            String textLow = "16\u00B0";

            mXCenter = canvas.getWidth()/2;

            //((textPaint.descent() + textPaint.ascent()) / 2) is the distance from the baseline to the center.

            float gap = getResources().getDimension(R.dimen.digital_character_gap);
            float lowTempOffset = getResources().getDimension(R.dimen.digital_low_temp_offset);
            float highTempOffset = getResources().getDimension(R.dimen.digital_high_temp_offset);

            float lineLength = getResources().getDimension(R.dimen.line_length);
            float lineYOffset = getResources().getDimension(R.dimen.line_y_offset);
            float lineStartX = mXCenter + lineLength/2;
            float lineStartY = lineYOffset;
            float lineStopX =  mXCenter - lineLength/2;
            float lineStopY = lineYOffset;

            canvas.drawText(textHour, mXCenter - gap , mYOffset, mTextPaintHour);
            canvas.drawText(textMin, mXCenter + gap, mYOffset, mTextPaintMin);
            canvas.drawText(textColon, mXCenter, mYOffset, mTextPaintColon);
            canvas.drawText(textDate, mXCenter, mYOffsetDate, mTextPaintDate);
            canvas.drawText(textHigh, mXCenter - highTempOffset, mYOffsetTemp, mTextPaintHigh);
            canvas.drawText(textLow, mXCenter + lowTempOffset, mYOffsetTemp, mTextPaintLow);
            canvas.drawLine(lineStartX, lineStartY, lineStopX, lineStopY, mLinePaint);
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

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
}
