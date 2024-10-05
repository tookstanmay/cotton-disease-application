package com.example.cottondiseaseapplication;

import android.Manifest;
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

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.text.DecimalFormat;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_IMAGE = 1;
    private static final int CAPTURE_IMAGE = 2;
    private static final int STORAGE_PERMISSION_CODE = 100;

    private ImageView imageView;
    private TextView textResult;
    private TFLiteModel tfliteModel;
    private Bitmap selectedBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.image_view);
        textResult = findViewById(R.id.text_result);
        Button buttonCaptureImage = findViewById(R.id.button_capture_image);
        Button buttonSelectImage = findViewById(R.id.button_select_image);
        Button buttonCheckResult = findViewById(R.id.button_check_result);

        // Check and request permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
        }

        buttonCaptureImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                textResult.setText("");
                openCamera();
            }
        });

        buttonSelectImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                textResult.setText("Results:\n");
                openGallery();
            }
        });

        buttonCheckResult.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedBitmap != null) {
                    classifyImage(selectedBitmap);
                } else {
                    Toast.makeText(MainActivity.this, "Please select or capture an image first.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        try {
            tfliteModel = new TFLiteModel(this);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Model loading failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, CAPTURE_IMAGE);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            try {
                if (requestCode == PICK_IMAGE && data != null) {
                    Uri imageUri = data.getData();
                    selectedBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                } else if (requestCode == CAPTURE_IMAGE && data != null) {
                    selectedBitmap = (Bitmap) data.getExtras().get("data");
                }

                imageView.setImageBitmap(selectedBitmap); // Display the selected/captured image
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void classifyImage(Bitmap bitmap) {
        try {
            // Resize only for model input
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, TFLiteModel.INPUT_WIDTH, TFLiteModel.INPUT_HEIGHT, true);
            float[][][][] input = tfliteModel.preprocessImage(resizedBitmap);
            float[][] output = tfliteModel.classify(input);
            textResult.setText("Results:\n" + getResultClass(output[0]));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error during classification: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getResultClass(float[] output) {
        String[] classes = {"Aphids", "Army Worm", "Bacterial Blight", "Healthy", "Powdery Mildew", "Target Spot"};

        StringBuilder result = new StringBuilder();
        float firstMax = output[0], secondMax = output[0];
        int firstIndex = 0, secondIndex = 0;
        for (int i = 0; i < output.length; i++) {
            if (output[i] > firstMax) {
                secondMax = firstMax;
                firstMax = output[i];
                secondIndex = firstIndex;
                firstIndex = i;
            } else if (output[i] > secondMax && output[i] != firstMax) {
                secondMax = output[i];
                secondIndex = i;
            }
        }

        DecimalFormat df = new DecimalFormat("0.4");
        result.append(classes[firstIndex]).append(": ").append(df.format(firstMax)).append("\n")
                .append(classes[secondIndex]).append(": ").append(df.format(secondMax));

        return result.toString();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission denied to read your external storage", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
