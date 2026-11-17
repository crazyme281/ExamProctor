package com.example.examproctor;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Saves student accounts on the phone itself (SharedPreferences). No server, no database. */
public class UserStore {

    // Fixed admin login. Change these two lines to whatever you want.
    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_PASSWORD = "admin123";

    private static final String PREFS = "exam_proctor_users";
    private static final String KEY = "students";

    public static boolean isAdmin(String username, String password) {
        return ADMIN_USERNAME.equals(username.trim().toLowerCase(Locale.ROOT))
                && ADMIN_PASSWORD.equals(password);
    }

    /** Returns the student's full name if the login is correct, otherwise null. */
    public static String checkStudent(Context ctx, String username, String password) {
        try {
            JSONObject all = load(ctx);
            JSONObject s = all.optJSONObject(username.trim().toLowerCase(Locale.ROOT));
            if (s != null && s.getString("hash").equals(hash(password))) {
                return s.getString("name");
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** Returns an error message, or null when the student was created. */
    public static String addStudent(Context ctx, String fullName, String username, String password) {
        String u = username.trim().toLowerCase(Locale.ROOT);
        if (fullName.trim().isEmpty() || u.isEmpty() || password.isEmpty()) return "Fill in all fields";
        if (u.equals(ADMIN_USERNAME)) return "That username is reserved";
        if (password.length() < 4) return "Password must be at least 4 characters";
        try {
            JSONObject all = load(ctx);
            if (all.has(u)) return "That username already exists";
            JSONObject s = new JSONObject();
            s.put("name", fullName.trim());
            s.put("hash", hash(password));
            all.put(u, s);
            save(ctx, all);
            return null;
        } catch (Exception e) {
            return "Could not save student";
        }
    }

    public static void deleteStudent(Context ctx, String username) {
        try {
            JSONObject all = load(ctx);
            all.remove(username);
            save(ctx, all);
        } catch (Exception ignored) { }
    }

    /** Each entry is "username|Full Name". */
    public static List<String> listStudents(Context ctx) {
        List<String> out = new ArrayList<>();
        try {
            JSONObject all = load(ctx);
            Iterator<String> keys = all.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                out.add(k + "|" + all.getJSONObject(k).getString("name"));
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static JSONObject load(Context ctx) throws JSONException {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new JSONObject(p.getString(KEY, "{}"));
    }

    private static void save(Context ctx, JSONObject all) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, all.toString()).apply();
    }

    private static String hash(String text) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
