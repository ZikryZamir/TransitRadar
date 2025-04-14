package com.example.transitradar;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

public class TimerManager {
    private static final long INTERVAL_MS = 60000; // 60 seconds
    private static final TimerManager INSTANCE = new TimerManager();

    private final Handler handler;
    private final List<Runnable> subscribers;
    private boolean isRunning;
    private final Runnable timerRunnable;

    private TimerManager() {
        handler = new Handler(Looper.getMainLooper());
        subscribers = new ArrayList<>();
        isRunning = false;
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                notifySubscribers();
                handler.postDelayed(this, INTERVAL_MS);
            }
        };
    }

    public static TimerManager getInstance() {
        return INSTANCE;
    }

    public void startTimer() {
        if (!isRunning) {
            handler.post(timerRunnable); // Start immediately
            isRunning = true;
        }
    }

    public void stopTimer() {
        if (isRunning) {
            handler.removeCallbacks(timerRunnable);
            isRunning = false;
        }
    }

    public void subscribe(Runnable callback) {
        if (!subscribers.contains(callback)) {
            subscribers.add(callback);
        }
        startTimer(); // Ensure timer starts when a subscriber is added
    }

    public void unsubscribe(Runnable callback) {
        subscribers.remove(callback);
        if (subscribers.isEmpty()) {
            stopTimer(); // Stop timer if no subscribers remain
        }
    }

    private void notifySubscribers() {
        for (Runnable subscriber : new ArrayList<>(subscribers)) {
            subscriber.run();
        }
    }

    public long getInterval() {
        return INTERVAL_MS;
    }
}