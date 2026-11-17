package com.example.examproctor;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExamActivity extends AppCompatActivity {

    private static final float HEAD_TURN_DEGREES = 20f;   // left/right limit
    private static final float HEAD_DOWN_DEGREES = 15f;   // looking-down limit
    private static final long HOLD_MS = 1500;             // must last this long to count

    private final String[] questions = {
            "Question 1: What is the capital of Nigeria?",
            "Question 2: What is 12 x 12?",
            "Question 3: Name the process plants use to make food from sunlight."
    };
    private int questionIndex = 0;

    private PreviewView previewView;
    private TextView statusText, questionText;
    private FaceDetector detector;
    private ExecutorService cameraExecutor;

    private String currentCondition = "OK";
    private String lastRecorded = "OK";
    private long conditionSince = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exam);

        previewView = findViewById(R.id.previewView);
        statusText = findViewById(R.id.statusText);
        questionText = findViewById(R.id.questionText);
        Button nextBtn = findViewById(R.id.nextBtn);
        Button finishBtn = findViewById(R.id.finishBtn);

        questionText.setText(questions[0]);
        nextBtn.setOnClickListener(v -> {
            questionIndex = (questionIndex + 1) % questions.length;
            questionText.setText(questions[questionIndex]);
        });
        finishBtn.setOnClickListener(v -> finish());

        detector = FaceDetection.getClient(new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build());
        cameraExecutor = Executors.newSingleThreadExecutor();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 10);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == 10 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            EventStore.add("ALERT", EventStore.studentName + ": camera permission denied");
            Toast.makeText(this, "Camera permission is required for this exam", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    if (imageProxy.getImage() == null) {
                        imageProxy.close();
                        return;
                    }
                    InputImage image = InputImage.fromMediaImage(
                            imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
                    detector.process(image)
                            .addOnSuccessListener(this::evaluate)
                            .addOnCompleteListener(t -> imageProxy.close());
                });

                provider.unbindAll();
                androidx.camera.core.Camera camera = provider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis);

                // Camera turned off or taken by another app
                camera.getCameraInfo().getCameraState().observe(this, state -> {
                    if (state.getError() != null) {
                        EventStore.cameraOn = false;
                        EventStore.add("ALERT", EventStore.studentName + ": camera disabled");
                        statusText.setText("Camera problem. Your teacher has been alerted.");
                    }
                });

                EventStore.examRunning = true;
                EventStore.cameraOn = true;
                EventStore.add("INFO", EventStore.studentName + " started the exam");
            } catch (Exception e) {
                Toast.makeText(this, "Could not start camera", Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /** Decide what the student is doing from the detected faces. */
    private void evaluate(List<Face> faces) {
        String condition = "OK";
        boolean isAlert = false;

        if (faces.isEmpty()) {
            condition = "Face disappeared";
            isAlert = true;
        } else if (faces.size() > 1) {
            condition = "Multiple faces detected";
            isAlert = true;
        } else {
            float turn = faces.get(0).getHeadEulerAngleY();   // + right, - left
            float tilt = faces.get(0).getHeadEulerAngleX();   // + up, - down
            if (turn > HEAD_TURN_DEGREES) condition = "Looked right";
            else if (turn < -HEAD_TURN_DEGREES) condition = "Looked left";
            else if (tilt < -HEAD_DOWN_DEGREES) condition = "Looked down";
        }

        long now = System.currentTimeMillis();
        if (!condition.equals(currentCondition)) {
            currentCondition = condition;
            conditionSince = now;
        }

        if (condition.equals("OK")) {
            lastRecorded = "OK";
        } else if (now - conditionSince >= HOLD_MS && !condition.equals(lastRecorded)) {
            lastRecorded = condition;
            EventStore.add(isAlert ? "ALERT" : "EVENT", EventStore.studentName + ": " + condition);
        }

        final String shown = condition;
        runOnUiThread(() -> statusText.setText(shown.equals("OK")
                ? "Monitoring: face visible" : "Monitoring: " + shown));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (EventStore.examRunning) {
            EventStore.add("EVENT", EventStore.studentName + ": left the exam screen");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (EventStore.examRunning) {
            EventStore.add("INFO", EventStore.studentName + " finished the exam");
        }
        EventStore.examRunning = false;
        EventStore.cameraOn = false;
        cameraExecutor.shutdown();
        detector.close();
    }
}
