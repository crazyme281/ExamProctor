package com.example.examproctor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** In-memory storage. No database: everything is lost when the app process closes. */
public class EventStore {
    public static String studentName = "";
    public static boolean examRunning = false;
    public static boolean cameraOn = false;
    private static final List<String> events = new ArrayList<>();
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public static synchronized void add(String level, String message) {
        events.add(0, TIME.format(new Date()) + "  [" + level + "]  " + message);
    }

    public static synchronized List<String> all() {
        return new ArrayList<>(events);
    }

    public static synchronized int count(String level) {
        int n = 0;
        for (String e : events) if (e.contains("[" + level + "]")) n++;
        return n;
    }

    public static synchronized void clear() {
        events.clear();
    }
}
