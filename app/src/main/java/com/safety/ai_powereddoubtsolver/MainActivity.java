package com.safety.ai_powereddoubtsolver;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.airbnb.lottie.LottieAnimationView;
import com.canhub.cropper.CropImage;
import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;

import java.io.ByteArrayOutputStream;

public class MainActivity extends AppCompatActivity {

    private ImageView imageView;
    private ImageView btnCapture, btnChooseFromGallery;
    LottieAnimationView lottieAnimationView;

    private final ActivityResultLauncher<CropImageContractOptions> cropImage = registerForActivityResult(new CropImageContract(), result -> {
        if (result.isSuccessful()) {
            Uri croppedImageUri = result.getUriContent();
            lottieAnimationView.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);
            imageView.setImageURI(croppedImageUri);

            Intent intent = new Intent(MainActivity.this, ResultActivity.class);
            intent.putExtra("imageUri", croppedImageUri.toString());
            startActivity(intent);
        } else {
            Toast.makeText(this, "Cropping failed: " + result.getError().getMessage(), Toast.LENGTH_SHORT).show();
        }
    });

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            Bitmap imageBitmap = (Bitmap) result.getData().getExtras().get("data");
            Uri imageUri = getImageUri(this, imageBitmap);
            cropImage.launch(new CropImageContractOptions(imageUri, new CropImageOptions()));
        }
    });

    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            Uri selectedImageUri = result.getData().getData();
            cropImage.launch(new CropImageContractOptions(selectedImageUri, new CropImageOptions()));
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        TextView toolbarTitle = findViewById(R.id.toolbar_title);

        if (toolbar != null) {
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayShowTitleEnabled(false); // Hide default title
        }

        toolbarTitle.setText("FarmaLens"); // Set custom title

        imageView = findViewById(R.id.imageView);
        btnCapture = findViewById(R.id.btnCapture);
        btnChooseFromGallery = findViewById(R.id.btnChooseFromGallery);
        lottieAnimationView = findViewById(R.id.lottie);

        btnCapture.setOnClickListener(view -> openCamera());
        btnChooseFromGallery.setOnClickListener(view -> openGallery());

        requestPermissions();
    }


    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            cameraLauncher.launch(takePictureIntent);
        }
    }

    private void openGallery() {
        Intent pickPhoto = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(pickPhoto);
    }

    private Uri getImageUri(Context context, Bitmap bitmap) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(context.getContentResolver(), bitmap, "Title", null);
        return Uri.parse(path);
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA
            }, 1);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finishAffinity();
    }
}
