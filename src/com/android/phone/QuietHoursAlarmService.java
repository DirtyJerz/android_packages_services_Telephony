/*
 * Copyright (C) 2013 Android Open Kang Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.phone;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnSeekCompleteListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

public class QuietHoursAlarmService extends Service {
    private final static String TAG = "QuietHoursAlarmService";
    private static final int NOTI_ID = 286;

    private NotificationManager mManager;
    private MediaPlayer mMediaPlayer;
    private AudioManager mAudioManager;

    private int mAlarmVolumeSetting;
    private boolean mPlaying = false;

    @Override
    public void onCreate() {
        super.onCreate();
        mAudioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        mManager = (NotificationManager)
                this.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public void onDestroy() {
        stopAlarm();
        if (mManager != null) {
            mManager.cancelAsUser(null, NOTI_ID, UserHandle.ALL);
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            startAlarmSound();
        } catch (Exception e) {
            // Do nothing
        }

        Bundle extras = intent.getExtras();
        String names = extras.getString("number");
        String title = getResources().getString(
                R.string.quiet_hours_alarm_dialog_title);
        Bitmap bm = BitmapFactory.decodeResource(getResources(), R.drawable.ic_quiethours);

        Notification.Builder builder = new Notification.Builder(this)
                .setTicker(title)
                .setContentTitle(title)
                .setContentText(names)
                .setAutoCancel(false)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_quiethours)
                .setLargeIcon(bm)
                .setStyle(new Notification.BigTextStyle()
                        .bigText(names + getResources().getString(
                                R.string.quiet_hours_alarm_message)));

        Intent alarmDialog = new Intent();
        alarmDialog.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        alarmDialog.setClass(this, QuietHoursBypassAlarm.class);
        alarmDialog.putExtra("number", names);
        alarmDialog.putExtra("norun", true);

        PendingIntent result = PendingIntent.getActivity(
                this, 0, alarmDialog, PendingIntent.FLAG_CANCEL_CURRENT);

        builder.setContentIntent(result);
        mManager.notifyAsUser(null, NOTI_ID, builder.build(),
                    UserHandle.ALL);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void startAlarmSound()
            throws java.io.IOException, IllegalArgumentException, IllegalStateException {

        Uri alertSound = returnUserRingtone();

        if (mPlaying) {
            stopAlarm();
        } else {
            mAlarmVolumeSetting = mAudioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        }

        if (mMediaPlayer == null) {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setOnErrorListener(new OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    mp.stop();
                    mp.release();
                    mMediaPlayer = null;
                    return true;
                }
            });
        }

        mAudioManager.setStreamVolume(AudioManager.STREAM_ALARM,
                mAudioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);

        mMediaPlayer.setDataSource(this, alertSound);
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);

        boolean loop = Settings.System.getIntForUser(this.getContentResolver(),
                Settings.System.QUIET_HOURS_ALARM_LOOP, 0, UserHandle.USER_CURRENT) != 0;

        if (loop) {
            mMediaPlayer.setLooping(true);
        } else {
            mMediaPlayer.setLooping(false);
            mMediaPlayer.setOnSeekCompleteListener(stopSelf);
        }
        mMediaPlayer.prepare();
        mMediaPlayer.start();
        mPlaying = true;
    }

    public void stopAlarm() {
        if (mPlaying) {

            if (mMediaPlayer != null) {
                mMediaPlayer.stop();
                mMediaPlayer.release();
                mMediaPlayer = null;
                mAudioManager.setStreamVolume(AudioManager.STREAM_ALARM,
                        mAlarmVolumeSetting, 0);
            }

            mPlaying = false;
        }
    }

    final OnSeekCompleteListener stopSelf = new OnSeekCompleteListener() {
        public void onSeekComplete(MediaPlayer mp) {
            stopAlarm();
        }
    };

    private Uri returnUserRingtone() {
        String ringtoneString = Settings.System.getStringForUser(this.getContentResolver(), Settings.System.QUIET_HOURS_ALARM_TONE, UserHandle.USER_CURRENT);
        if (ringtoneString == null) {
            // Value not set, defaults to Default Ringtone
            Uri alertSoundUri = RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_RINGTONE);
            return alertSoundUri;
        } else {
            Uri ringtoneUri = Uri.parse(ringtoneString);
            return ringtoneUri;
        }
    }
}
