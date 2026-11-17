package com.example.examproctor;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText usernameInput, passwordInput;
    private TextView errorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        errorText = findViewById(R.id.errorText);
        Button loginBtn = findViewById(R.id.loginBtn);

        loginBtn.setOnClickListener(v -> login());
    }

    @Override
    protected void onResume() {
        super.onResume();
        passwordInput.setText("");
    }

    private void login() {
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        errorText.setText("");

        if (UserStore.isAdmin(username, password)) {
            startActivity(new Intent(this, AdminActivity.class));
            return;
        }

        String fullName = UserStore.checkStudent(this, username, password);
        if (fullName == null) {
            errorText.setText("Wrong username or password");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Camera monitoring")
                .setMessage("Your front camera will be used to check your face during the exam. "
                        + "Video is not recorded or uploaded; only alerts are logged. Do you agree?")
                .setPositiveButton("I agree", (d, w) -> {
                    EventStore.studentName = fullName;
                    startActivity(new Intent(this, ExamActivity.class));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
