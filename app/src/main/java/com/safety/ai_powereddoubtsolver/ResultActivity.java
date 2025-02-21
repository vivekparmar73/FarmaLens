package com.safety.ai_powereddoubtsolver;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import okhttp3.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

public class ResultActivity extends AppCompatActivity {

    private ImageView imageView;
    private EditText editTextInstructions;
    private Button buttonSubmit;
    private TextView textViewResult;
    private String extractedText = "";
    private Uri imageUri;

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
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // back button
            getSupportActionBar().setDisplayShowTitleEnabled(false); // Hide default title
            toolbar.setNavigationOnClickListener(v -> onBackPressed()); // Handle back button click

        }

        toolbarTitle.setText("FarmaLens"); // Set custom title

        imageView = findViewById(R.id.imageView);
        editTextInstructions = findViewById(R.id.editTextInstructions);
        buttonSubmit = findViewById(R.id.buttonSubmit);
        textViewResult = findViewById(R.id.textViewResult);

        requestPermissions();

        String imageUriString = getIntent().getStringExtra("imageUri");
        if (imageUriString != null) {
            imageUri = Uri.parse(imageUriString);
            imageView.setImageURI(imageUri);
            processImageForTextRecognition(imageUri);
        } else {
            textViewResult.setText("Error: No image provided.");
        }

        buttonSubmit.setOnClickListener(v -> {
            if (imageUri == null) {
                textViewResult.setText("Error: No image selected.");
                return;
            }

            String userInstruction = editTextInstructions.getText().toString().trim();
            String fullPrompt;

            if (isFertilizer(extractedText)) {
                // Fertilizer-specific prompt
                fullPrompt = "Analyze the provided image and extract fertilizer-related details. Provide the following:\n" +
                        "1. Fertilizer Name\n" +
                        "2. Composition (NPK Ratio, Micronutrients, Organic/Inorganic)\n" +
                        "3. Usage Instructions (How and when to apply)\n" +
                        "4. Benefits (Which plants/crops it is best for)\n" +
                        "5. Precautions and Safety Guidelines\n\n";

                if (!extractedText.isEmpty()) {
                    fullPrompt += "Extracted Text from Fertilizer Image:\n" + extractedText + "\n\n";
                }
            } else {
                // Plant disease analysis
                fullPrompt = "Analyze the provided image of a plant and detect:\n" +
                        "1. Plant Name\n" +
                        "2. Any detected disease, symptoms, and causes\n" +
                        "3. Recommended treatments, fertilizers, and pesticides\n" +
                        "4. Preventive measures to maintain plant health\n\n";

                if (!extractedText.isEmpty()) {
                    fullPrompt += "Extracted Text from Plant Image:\n" + extractedText + "\n\n";
                }
            }

            if (!userInstruction.isEmpty()) {
                fullPrompt += "User Instruction: " + userInstruction;
            }

            callGeminiAPI(fullPrompt);
        });
    }

    private void processImageForTextRecognition(Uri uri) {
        try {
            InputImage image = InputImage.fromFilePath(getApplicationContext(), uri);
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(image)
                    .addOnSuccessListener(this::handleTextRecognition)
                    .addOnFailureListener(e -> {
                        textViewResult.setText("Failed to recognize text from the image.");
                        Log.e("OCR Error", "Error recognizing text", e);
                    });
        } catch (IOException e) {
            textViewResult.setText("Error processing image.");
            Log.e("Image Processing", "Error loading image", e);
        }
    }

    private void handleTextRecognition(Text visionText) {
        extractedText = visionText.getText();
        if (extractedText.isEmpty()) {
            textViewResult.setText("No detected! Analyzing visual content...");
        } else {
            textViewResult.setText("Processing details...");
        }
    }

    private void callGeminiAPI(String userInput) {
        OkHttpClient client = new OkHttpClient();

        JSONObject payload = new JSONObject();
        JSONArray contentsArray = new JSONArray();
        JSONObject userObject = new JSONObject();
        JSONArray partsArray = new JSONArray();
        JSONObject textPart = new JSONObject();

        try {
            textPart.put("text", userInput);
            partsArray.put(textPart);
            userObject.put("role", "user");
            userObject.put("parts", partsArray);
            contentsArray.put(userObject);
            payload.put("contents", contentsArray);
        } catch (Exception e) {
            runOnUiThread(() -> textViewResult.setText("Error creating JSON request"));
            Log.e("GeminiAPI", "JSON Error", e);
            return;
        }

        RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(GEMINI_API_URL)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> textViewResult.setText("API Error: " + e.getMessage()));
                Log.e("GeminiAPI", "API Call Failed", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseData = response.body().string();
                Log.d("GeminiAPI", "Full Response: " + responseData);

                if (!response.isSuccessful()) {
                    runOnUiThread(() -> textViewResult.setText("API Error: " + response.code() + " " + response.message()));
                    return;
                }

                try {
                    JSONObject jsonResponse = new JSONObject(responseData);
                    JSONArray candidates = jsonResponse.optJSONArray("candidates");

                    if (candidates != null && candidates.length() > 0) {
                        JSONObject firstCandidate = candidates.getJSONObject(0);
                        JSONObject content = firstCandidate.optJSONObject("content");

                        if (content != null) {
                            JSONArray parts = content.optJSONArray("parts");

                            if (parts != null && parts.length() > 0) {
                                String aiResponse = parts.getJSONObject(0).optString("text", "").trim();
                                runOnUiThread(() -> textViewResult.setText("AI Response:\n" + aiResponse));
                                return;
                            }
                        }
                    }
                    runOnUiThread(() -> textViewResult.setText("AI Response: (Invalid response)"));

                } catch (Exception e) {
                    runOnUiThread(() -> textViewResult.setText("Error parsing response: " + e.getMessage()));
                    Log.e("GeminiAPI", "Parsing error", e);
                }
            }
        });
    }

    private boolean isFertilizer(String text) {
        return text.toLowerCase().contains("npk") || text.toLowerCase().contains("fertilizer") ||
                text.toLowerCase().contains("nutrients") || text.toLowerCase().contains("compost");
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
