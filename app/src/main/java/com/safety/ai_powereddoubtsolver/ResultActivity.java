package com.safety.ai_powereddoubtsolver;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.nitish.typewriterview.TypeWriterView;

import okhttp3.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

public class ResultActivity extends AppCompatActivity {

    private ImageView imageView;
    private EditText editTextInstructions;
    private Button buttonSubmit;
    private TypeWriterView textViewResult;
    private String extractedText = "";
    private Uri imageUri;
    private ProgressBar progressBarLoader;


    private static final String GEMINI_API_KEY = "AIzaSyBpn6XHWbxjSuzZCb2BoEwSw0suePhdJ-s"; // Replace with your API Key
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1/models/gemini-pro:generateContent?key=" + GEMINI_API_KEY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        Toolbar toolbar = findViewById(R.id.toolbar);
        TextView toolbarTitle = findViewById(R.id.toolbar_title);

        if (toolbar != null) {
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        toolbarTitle.setText("FarmaLens");

        imageView = findViewById(R.id.imageView);
        editTextInstructions = findViewById(R.id.editTextInstructions);
        buttonSubmit = findViewById(R.id.buttonSubmit);
        textViewResult = findViewById(R.id.textViewResult);
        progressBarLoader = findViewById(R.id.progressBarLoader); // Initialize ProgressBar

        progressBarLoader.setVisibility(View.GONE); // Initially hide the loader

        requestPermissions();

        String imageUriString = getIntent().getStringExtra("imageUri");
        if (imageUriString != null) {
            imageUri = Uri.parse(imageUriString);
            imageView.setImageURI(imageUri);
            processImageForTextRecognition(imageUri);
        } else {
            textViewResult.animateText("Error: No image provided.");
        }

        buttonSubmit.setOnClickListener(v -> handleUserInput());
    }

    private void processImageForTextRecognition(Uri uri) {
        try {
            InputImage image = InputImage.fromFilePath(getApplicationContext(), uri);
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(image)
                    .addOnSuccessListener(this::handleTextRecognition)
                    .addOnFailureListener(e -> {
                        textViewResult.animateText("Failed to recognize text from the image.");
                        Log.e("OCR Error", "Error recognizing text", e);
                    });
        } catch (IOException e) {
            textViewResult.animateText("Error processing image.");
            Log.e("Image Processing", "Error loading image", e);
        }
    }

    private void handleTextRecognition(Text visionText) {
        extractedText = visionText.getText();
        if (extractedText.isEmpty()) {
            textViewResult.animateText("No text detected! Analyzing the image visually...");
        } else {
            textViewResult.animateText("Analyzing extracted details...");
        }
    }

    private void handleUserInput() {
        if (imageUri == null) {
            textViewResult.animateText("Error: No image selected.");
            return;
        }

        String userInstruction = editTextInstructions.getText().toString().trim();
        String fullPrompt = generatePrompt(userInstruction);

        // Show Loader and Disable Submit Button
        progressBarLoader.setVisibility(View.VISIBLE);
        buttonSubmit.setEnabled(false);
        textViewResult.animateText("Processing... Please wait.");

        callGeminiAPI(fullPrompt);
    }

    private String generatePrompt(String userInstruction) {
        String prompt;
        if (!extractedText.isEmpty()) {
            if (isFertilizer(extractedText)) {
                prompt = "**The image contains a Fertilizer Product Label.** Analyze and provide detailed information on the fertilizer based on the extracted text.";
            } else {
                prompt = "**The image contains plant-related text.** Analyze the text and provide relevant information about the plant, diseases, and treatments.";
            }
        } else {
            prompt = "**No readable text detected.** Analyze the image visually and determine relevant details.";
        }

        if (!userInstruction.isEmpty()) {
            prompt += "\n**User Instruction:** " + userInstruction;
        }
        return prompt;
    }

    private void callGeminiAPI(String userInput) {
        OkHttpClient client = new OkHttpClient();
        JSONObject payload = new JSONObject();
        try {
            JSONArray contentsArray = new JSONArray();
            JSONObject userObject = new JSONObject();
            JSONArray partsArray = new JSONArray();
            JSONObject textPart = new JSONObject();

            textPart.put("text", userInput);
            partsArray.put(textPart);
            userObject.put("role", "user");
            userObject.put("parts", partsArray);
            contentsArray.put(userObject);
            payload.put("contents", contentsArray);
        } catch (Exception e) {
            runOnUiThread(() -> {
                textViewResult.animateText("Error creating JSON request");
                progressBarLoader.setVisibility(View.GONE);
                buttonSubmit.setEnabled(true);
            });
            return;
        }

        RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json"));
        Request request = new Request.Builder().url(GEMINI_API_URL).post(body).addHeader("Content-Type", "application/json").build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    textViewResult.animateText("API Error: " + e.getMessage());
                    progressBarLoader.setVisibility(View.GONE);
                    buttonSubmit.setEnabled(true);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseData = response.body().string();
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> {
                        textViewResult.animateText("API Error: " + response.code() + " " + response.message());
                        progressBarLoader.setVisibility(View.GONE);
                        buttonSubmit.setEnabled(true);
                    });
                    return;
                }

                try {
                    JSONObject jsonResponse = new JSONObject(responseData);
                    JSONArray candidates = jsonResponse.optJSONArray("candidates");
                    if (candidates != null && candidates.length() > 0) {
                        String aiResponse = candidates.getJSONObject(0)
                                .optJSONObject("content")
                                .optJSONArray("parts")
                                .getJSONObject(0)
                                .optString("text", "")
                                .replace("**", "") // Remove asterisks
                                .replace("*", "") // Remove asterisks
                                .trim();

                        runOnUiThread(() -> textViewResult.animateText("AI Response:\n" + aiResponse));

                        runOnUiThread(() -> {
                            textViewResult.animateText("AI Response:\n" + aiResponse);
                            progressBarLoader.setVisibility(View.GONE);
                            buttonSubmit.setEnabled(true);
                        });
                    } else {
                        runOnUiThread(() -> {
                            textViewResult.animateText("AI Response: (Invalid response)");
                            progressBarLoader.setVisibility(View.GONE);
                            buttonSubmit.setEnabled(true);
                        });
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        textViewResult.animateText("Error parsing response: " + e.getMessage());
                        progressBarLoader.setVisibility(View.GONE);
                        buttonSubmit.setEnabled(true);
                    });
                }
            }
        });
    }

    private boolean isFertilizer(String text) {
        return text.toLowerCase().contains("npk") || text.toLowerCase().contains("fertilizer") || text.toLowerCase().contains("nutrients") || text.toLowerCase().contains("compost");
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{
                    Manifest.permission.INTERNET,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            }, 1);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}