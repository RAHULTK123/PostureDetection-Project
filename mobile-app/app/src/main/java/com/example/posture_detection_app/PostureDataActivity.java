package com.example.posture_detection_app;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
// Removed WebView imports
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast; // Added for potential messages

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

// MPAndroidChart Imports
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;


import com.example.posture_detection_app.authentication.Login;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query; // Import Query for limitToLast
import com.google.firebase.database.ValueEventListener;
// Removed Gson, LinkedList, TimeUnit, Map, List, HashMap imports

// Keep date/time imports if needed elsewhere, remove if not
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;


public class PostureDataActivity extends AppCompatActivity {

    private FirebaseAuth firebaseAuth;
    private TextView statusTextView;
    // --- Chart Variable ---
    private PieChart pieChart; // Keep PieChart reference
    // --- End Chart Variable ---
    private Button goodPostureButton;

    private String liveStatusText = "";
    private String guidanceMessageText = "";

    public static final String CHANNEL_ID = "PostureAlertChannel";
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1001;

    // --- Timer Logic Variables (Keep for notifications) ---
    private Handler notificationHandler;
    private Runnable notificationRunnable;
    private boolean isBadPostureTimerRunning = false;
    private static final long BAD_POSTURE_NOTIFICATION_DELAY = 20000; // 20 seconds
    private String currentPostureStatus = ""; // Tracks the latest status from live data
    // --- End Timer Logic Variables ---

    // --- Removed Pie Chart Data Variables for recent window ---
    // private LinkedList<PostureEntry> recentPostureData;
    // private static final long RECENT_DATA_WINDOW_MINUTES = 30;
    // private static final long RECENT_DATA_WINDOW_MS = TimeUnit.MINUTES.toMillis(RECENT_DATA_WINDOW_MINUTES);
    // private SimpleDateFormat firebaseTimestampFormat;
    // private static class PostureEntry { ... }
    // --- End Removed Variables ---


    @SuppressLint("SetJavaScriptEnabled")
    private void updateStatusTextView() {
        String combinedText = liveStatusText;
        if (guidanceMessageText != null && !guidanceMessageText.isEmpty() && !"null".equalsIgnoreCase(guidanceMessageText)) {
            combinedText += "\nGuidance: " + guidanceMessageText;
        }
        statusTextView.setText(combinedText);

        // UI updates based on LIVE status, not history chart
        if ("Good".equals(currentPostureStatus)) {
            statusTextView.setTextColor(Color.parseColor("#2E7D32")); // Green
            goodPostureButton.setVisibility(View.GONE);
        } else if ("Bad".equals(currentPostureStatus)) {
            statusTextView.setTextColor(Color.parseColor("#D32F2F")); // Red
            goodPostureButton.setVisibility(View.VISIBLE);
        } else {
            statusTextView.setTextColor(Color.BLACK);
            goodPostureButton.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_posture_data);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        firebaseAuth = FirebaseAuth.getInstance();
        statusTextView = findViewById(R.id.statusTextView);
        pieChart = findViewById(R.id.pieChart); // Get PieChart reference
        goodPostureButton = findViewById(R.id.goodPostureButton);

        // Removed PieChart real-time data structure initialization
        // firebaseTimestampFormat = ... (remove if only used for real-time chart)

        notificationHandler = new Handler(Looper.getMainLooper());
        setupNotificationRunnable();

        setupPieChart(); // Keep basic PieChart appearance setup

        createNotificationChannel();

        if (firebaseAuth.getCurrentUser() != null) {
            String currentUserId = firebaseAuth.getCurrentUser().getUid();

            // Monitor LIVE data for status text and notifications ONLY
            monitorLiveData(currentUserId);

            // Monitor notification tag (Optional: keep for logging)
            monitorNotificationTag(currentUserId);

            // Monitor guidance message
            monitorGuidanceMessage(currentUserId);

            // Load HISTORY data for the Pie Chart ONCE
            loadHistoryForPieChart(currentUserId);

        } else {
            Log.e("AuthError", "User not logged in.");
            Intent intent = new Intent(PostureDataActivity.this, Login.class);
            startActivity(intent);
            finish();
            return;
        }

        goodPostureButton.setOnClickListener(v -> {
            Intent intent = new Intent(PostureDataActivity.this, GoodPostureImagesActivity.class);
            startActivity(intent);
        });
    }

    // Basic PieChart Appearance Setup (Keep or Adjust)
    private void setupPieChart() {
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.setExtraOffsets(5, 10, 5, 5);
        pieChart.setDragDecelerationFrictionCoef(0.95f);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.WHITE);
        pieChart.setTransparentCircleRadius(61f);
        pieChart.setHoleRadius(58f);
        pieChart.setDrawCenterText(true);
        pieChart.setCenterText("Last 30 Records"); // Updated Center Text
        pieChart.setCenterTextSize(12f);
        pieChart.setRotationAngle(0);
        pieChart.setRotationEnabled(true);
        pieChart.setHighlightPerTapEnabled(true);
        pieChart.getLegend().setEnabled(false);
        pieChart.setEntryLabelColor(Color.BLACK);
        pieChart.setEntryLabelTextSize(12f);
        // Set initial empty state text
        updatePieChartWithCounts(0, 0); // Show "No Data" initially
    }

    // --- Timer/Notification Logic (Keep as is) ---
    private void setupNotificationRunnable() {
        notificationRunnable = new Runnable() {
            @Override
            public void run() {
                isBadPostureTimerRunning = false;
                if ("Bad".equals(currentPostureStatus)) {
                    Log.d("PostureTimer", "Timer finished, posture still Bad. Showing notification.");
                    showBadPosturePopup();
                    showNotificationBar();
                    startBadPostureTimer();
                } else {
                    Log.d("PostureTimer", "Timer finished, posture now ("+ currentPostureStatus +"). Notification cancelled.");
                    stopBadPostureTimer();
                }
            }
        };
    }
    private void startBadPostureTimer() {
        if (!isBadPostureTimerRunning && "Bad".equals(currentPostureStatus)) {
            Log.d("PostureTimer", "Starting bad posture timer (" + BAD_POSTURE_NOTIFICATION_DELAY + "ms)");
            notificationHandler.postDelayed(notificationRunnable, BAD_POSTURE_NOTIFICATION_DELAY);
            isBadPostureTimerRunning = true;
        } else if (isBadPostureTimerRunning) {
            Log.d("PostureTimer", "Timer already running.");
        } else if (!"Bad".equals(currentPostureStatus)){
            Log.d("PostureTimer", "Posture is not Bad, timer not started.");
        }
    }
    private void stopBadPostureTimer() {
        if (isBadPostureTimerRunning) {
            Log.d("PostureTimer", "Stopping bad posture timer.");
            notificationHandler.removeCallbacks(notificationRunnable);
            isBadPostureTimerRunning = false;
        }
    }
    // --- End Timer/Notification Logic ---


    // --- Load History Data for Pie Chart ---
    private void loadHistoryForPieChart(String userId) {
        DatabaseReference historyRef = FirebaseDatabase.getInstance("https://posture-detection-e30cf-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("posture_logs")
                .child(userId)
                .child("history");

        // Query to get only the last 30 records
        Query last30Query = historyRef.limitToLast(30);

        last30Query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.w("HistoryData", "No history data found for user: " + userId);
                    updatePieChartWithCounts(0, 0); // Update chart to show no data
                    return;
                }

                int goodCount = 0;
                int badCount = 0;

                Log.d("HistoryData", "Processing " + snapshot.getChildrenCount() + " history records.");

                for (DataSnapshot recordSnapshot : snapshot.getChildren()) {
                    String status = recordSnapshot.child("status").getValue(String.class);
                    if ("Good".equals(status)) {
                        goodCount++;
                    } else if ("Bad".equals(status)) {
                        badCount++;
                    }
                    // Optional: Log status for debugging
                    // Log.d("HistoryData", "Record Status: " + status);
                }

                Log.i("HistoryData", "History Counts - Good: " + goodCount + ", Bad: " + badCount);
                updatePieChartWithCounts(goodCount, badCount); // Update chart with history counts
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("HistoryDataError", "Error loading history data: " + error.getMessage());
                updatePieChartWithCounts(0, 0); // Show no data on error
            }
        });
    }
    // --- End History Loading ---


    // --- Update Pie Chart based on Counts ---
    // Modified to accept counts directly
    private void updatePieChartWithCounts(int goodCount, int badCount) {
        if (pieChart == null) return;

        ArrayList<PieEntry> entries = new ArrayList<>();
        if (goodCount > 0) {
            entries.add(new PieEntry(goodCount, "Good"));
        }
        if (badCount > 0) {
            entries.add(new PieEntry(badCount, "Bad"));
        }

        if (entries.isEmpty()) {
            pieChart.setData(null); // Clear previous data
            pieChart.setCenterText("No History Data"); // Update center text
            pieChart.invalidate(); // Refresh
            return;
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(5f);

        ArrayList<Integer> colors = new ArrayList<>();
        for (PieEntry entry : entries) {
            if ("Good".equals(entry.getLabel())) {
                colors.add(Color.parseColor("#99FF99")); // Green
            } else if ("Bad".equals(entry.getLabel())) {
                colors.add(Color.parseColor("#FF6666")); // Red
            } else {
                colors.add(ColorTemplate.getHoloBlue());
            }
        }
        dataSet.setColors(colors);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter(pieChart)); // Show percentages
        data.setValueTextSize(11f);
        data.setValueTextColor(Color.BLACK);

        pieChart.setData(data);
        pieChart.setCenterText("Last 30 Records"); // Set appropriate center text
        pieChart.highlightValues(null);
        pieChart.invalidate(); // Refresh the chart
        pieChart.animateY(600); // Optional animation
        Log.d("PieChartUpdate", "PieChart updated with History. Good: " + goodCount + ", Bad: " + badCount);
    }
    // --- End Pie Chart Update ---


    // --- Monitor Live Data (Only for Status TextView and Timer) ---
    private void monitorLiveData(String userId) {
        DatabaseReference liveRef = FirebaseDatabase.getInstance("https://posture-detection-e30cf-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("posture_logs")
                .child(userId)
                .child("live");

        liveRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String status = snapshot.child("status").getValue(String.class);
                    String timeStr = snapshot.child("time").getValue(String.class);

                    if (status != null && timeStr != null) {
                        // Update local status for UI and Timer
                        currentPostureStatus = status;

                        // Update UI text view
                        String[] dateTimeParts = timeStr.split(" ");
                        String timeOnly = (dateTimeParts.length > 1) ? dateTimeParts[1] : timeStr;
                        liveStatusText = "Your Recent Body Posture: \n" + status + "\nat Time: " + timeOnly;
                        updateStatusTextView(); // Updates text, color, button

                        // --- Timer Logic ---
                        if ("Bad".equals(currentPostureStatus)) {
                            startBadPostureTimer();
                        } else {
                            stopBadPostureTimer();
                        }
                        // --- End Timer Logic ---

                        // --- REMOVED Pie chart update logic from here ---
                        // long entryTimestamp = parseTimestamp(timeStr);
                        // if (entryTimestamp > 0) {
                        //    PostureEntry newEntry = new PostureEntry(entryTimestamp, status);
                        //    addRecentPostureData(newEntry);
                        //    updatePieChartData();
                        // }
                        // --- End Removed ---

                    } else {
                        Log.w("LiveDataWarning", "Live data node exists but status or time is null.");
                        currentPostureStatus = "";
                        stopBadPostureTimer();
                        liveStatusText = "Waiting for valid live data...";
                        updateStatusTextView();
                    }
                } else {
                    Log.w("LiveDataWarning", "Live data node does not exist.");
                    currentPostureStatus = "";
                    stopBadPostureTimer();
                    liveStatusText = "Waiting for live data...";
                    updateStatusTextView();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("LiveDataError", "Error: " + error.getMessage());
                currentPostureStatus = "";
                stopBadPostureTimer();
                liveStatusText = "Error loading live data.";
                updateStatusTextView();
            }
        });
    }
    // --- End Monitor Live Data ---


    // --- Other methods (Guidance, Notification Tag, Notifications, OptionsMenu, Lifecycle) ---
    // Keep these methods as they were in the previous version, they don't need
    // changes for switching the PieChart data source from live to history.
    // ... (monitorGuidanceMessage, monitorNotificationTag, createNotificationChannel, ...)
    // ... (showNotificationBar, showBadPosturePopup, onRequestPermissionsResult, ...)
    // ... (onPause, onResume, onCreateOptionsMenu, onOptionsItemSelected, onDestroy) ...
    // --- Make sure all those methods are still present below ---

    // --- Paste the remaining methods from the previous version here ---

    private void monitorGuidanceMessage(String userId) {
        DatabaseReference guidanceRef = FirebaseDatabase.getInstance("https://posture-detection-e30cf-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("posture_logs")
                .child(userId)
                .child("Guidance_message");

        guidanceRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                guidanceMessageText = snapshot.getValue(String.class);
                updateStatusTextView(); // Update UI including guidance
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GuidanceMessageError", "Error: " + error.getMessage());
                guidanceMessageText = ""; // Clear guidance on error
                updateStatusTextView(); // Update UI
            }
        });
    }

    private void monitorNotificationTag(String userId) {
        DatabaseReference notificationRef = FirebaseDatabase.getInstance("https://posture-detection-e30cf-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("posture_logs")
                .child(userId)
                .child("notification");

        notificationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean notificationFlag = snapshot.getValue(Boolean.class);
                Log.d("NotificationFlag", "Firebase notification flag changed to: " + notificationFlag);
                // No direct action taken here anymore
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("NotificationError", "Error reading notification flag: " + error.getMessage());
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                CharSequence name = getString(R.string.notification_channel_name);
                String description = getString(R.string.notification_channel_description);
                int importance = NotificationManager.IMPORTANCE_HIGH;
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
                channel.setDescription(description);

                NotificationManager notificationManager = getSystemService(NotificationManager.class);
                if (notificationManager != null) {
                    notificationManager.createNotificationChannel(channel);
                    Log.i("NotificationChannel", "Notification channel created successfully.");
                } else {
                    Log.e("NotificationChannel", "NotificationManager is null.");
                }
            } catch (Exception e) {
                Log.e("NotificationChannel", "Error creating notification channel", e);
            }
        }
    }

    private void showNotificationBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w("NotificationPermission", "Notification permission not granted. Requesting...");
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST_CODE);
                return;
            }
        }
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_bad_posture)
                    .setContentTitle(getString(R.string.bad_posture_alert_title))
                    .setContentText(getString(R.string.bad_posture_alert_text))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                    .setDefaults(NotificationCompat.DEFAULT_VIBRATE);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            int notificationId = 1; // Use a specific ID
            notificationManager.notify(notificationId, builder.build());
            Log.i("Notification", "Bad posture notification shown.");

        } catch (Exception e) {
            Log.e("NotificationError", "Failed to show notification bar", e);
        }
    }

    private void showBadPosturePopup() {
        if (isFinishing() || isDestroyed()) {
            Log.w("PopupWarning", "Activity finishing, skipping bad posture popup.");
            return;
        }
        try {
            new android.app.AlertDialog.Builder(PostureDataActivity.this)
                    .setTitle(getString(R.string.bad_posture_alert_title))
                    .setMessage(getString(R.string.bad_posture_alert_text))
                    .setPositiveButton(android.R.string.ok, null)
                    .setIcon(R.drawable.ic_bad_posture)
                    .show();
            Log.i("Popup", "Bad posture popup shown.");
        } catch (Exception e) {
            Log.e("PopupError", "Failed to show bad posture popup", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i("NotificationPermission", "Notification permission granted by user.");
            } else {
                Log.w("NotificationPermission", "Notification permission denied by user.");
                Toast.makeText(this, "Notifications disabled. Alerts requires this permission.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("PostureTimer", "Activity paused, stopping timer.");
        stopBadPostureTimer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("PostureTimer", "Activity resumed.");
        if ("Bad".equals(currentPostureStatus)) {
            startBadPostureTimer();
        } else {
            stopBadPostureTimer();
        }
        // Consider reloading history data on resume if it might change often?
        // loadHistoryForPieChart(firebaseAuth.getCurrentUser().getUid()); // Uncomment if needed
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_view_history) {
            Intent intent = new Intent(PostureDataActivity.this, HistoryRecordsActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.menu_logout) {
            stopBadPostureTimer();
            firebaseAuth.signOut();
            Intent intent = new Intent(PostureDataActivity.this, Login.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notificationHandler != null && notificationRunnable != null) {
            notificationHandler.removeCallbacks(notificationRunnable);
            Log.d("PostureTimer", "Activity destroyed, removed handler callbacks.");
        }
        // Detach Firebase listeners if needed, though usually handled okay
    }


} // End of PostureDataActivity class