package com.example.examproctor;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class AdminActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> eventRows = new ArrayList<>();
    private final List<String> studentRows = new ArrayList<>();
    private final List<String> studentKeys = new ArrayList<>();
    private ArrayAdapter<String> eventAdapter, studentAdapter;
    private TextView summaryText;
    private EditText nameInput, userInput, passInput;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            String who = EventStore.studentName.isEmpty() ? "No student yet" : EventStore.studentName;
            summaryText.setText("Student: " + who
                    + "\nExam: " + (EventStore.examRunning ? "Writing now" : "Not running")
                    + "\nCamera: " + (EventStore.cameraOn ? "On" : "Off")
                    + "\nAlerts: " + EventStore.count("ALERT")
                    + "   Events: " + EventStore.count("EVENT"));
            eventRows.clear();
            eventRows.addAll(EventStore.all());
            eventAdapter.notifyDataSetChanged();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        summaryText = findViewById(R.id.summaryText);
        nameInput = findViewById(R.id.newNameInput);
        userInput = findViewById(R.id.newUserInput);
        passInput = findViewById(R.id.newPassInput);
        Button createBtn = findViewById(R.id.createBtn);
        Button clearBtn = findViewById(R.id.clearBtn);
        Button logoutBtn = findViewById(R.id.logoutBtn);
        ListView studentList = findViewById(R.id.studentList);
        ListView eventList = findViewById(R.id.eventList);

        studentAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, studentRows);
        studentList.setAdapter(studentAdapter);
        eventAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, eventRows);
        eventList.setAdapter(eventAdapter);

        createBtn.setOnClickListener(v -> {
            String error = UserStore.addStudent(this,
                    nameInput.getText().toString(),
                    userInput.getText().toString(),
                    passInput.getText().toString());
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "Student created", Toast.LENGTH_SHORT).show();
            nameInput.setText("");
            userInput.setText("");
            passInput.setText("");
            loadStudents();
        });

        studentList.setOnItemClickListener((parent, view, position, id) -> {
            String key = studentKeys.get(position);
            new AlertDialog.Builder(this)
                    .setTitle("Delete student?")
                    .setMessage("Remove " + key + " from this device?")
                    .setPositiveButton("Delete", (d, w) -> {
                        UserStore.deleteStudent(this, key);
                        loadStudents();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        clearBtn.setOnClickListener(v -> EventStore.clear());
        logoutBtn.setOnClickListener(v -> finish());

        loadStudents();
    }

    private void loadStudents() {
        studentRows.clear();
        studentKeys.clear();
        for (String entry : UserStore.listStudents(this)) {
            String[] parts = entry.split("\\|", 2);
            studentKeys.add(parts[0]);
            studentRows.add(parts[1] + "  (" + parts[0] + ")");
        }
        studentAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresh);
    }
}
