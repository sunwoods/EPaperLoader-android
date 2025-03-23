package com.example.epaperuploader;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.canhub.cropper.CropImageView;
import com.example.epaperuploader.image_processing.EPaperDisplay;

import java.io.File;
import java.io.IOException;

public class CropImageActivity extends AppCompatActivity {
    private Uri sourceUri;
    private Uri saveUri;
    private CropImageView vCropImageView;
    private final ActivityResultLauncher<String> activityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> vCropImageView.setImageUriAsync(uri));

    private Button btnRotation;
    private Button btnCrop;

    private int width = EPaperDisplay.getDisplays()[EPaperDisplay.epdInd].width;
    private int height = EPaperDisplay.getDisplays()[EPaperDisplay.epdInd].height;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop_image);

        vCropImageView = findViewById(R.id.cropImageView);
        vCropImageView.setOnSetImageUriCompleteListener(new CropImageView.OnSetImageUriCompleteListener() {
            @Override
            public void onSetImageUriComplete(@NonNull CropImageView cropImageView, @NonNull Uri uri, @Nullable Exception e) {
                sourceUri = uri;
                vCropImageView.setAspectRatio(width, height);
            }
        });

        vCropImageView.setOnCropImageCompleteListener(new CropImageView.OnCropImageCompleteListener() {
            @Override
            public void onCropImageComplete(@NonNull CropImageView cropImageView, @NonNull CropImageView.CropResult cropResult) {
                Intent intent = new Intent();
                intent.putExtra("FILE_NAME", saveUri);
                setResult(RESULT_OK, intent);
                finish();
            }
        });

        btnRotation = findViewById(R.id.btnRotation);
        btnRotation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vCropImageView.rotateImage(90);
                vCropImageView.setAspectRatio(width, height);
            }
        });

        btnCrop = findViewById(R.id.btnCrop);
        btnCrop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    saveUri = Uri.fromFile(File.createTempFile("esp32_image_crop", "png"));
                    vCropImageView.croppedImageAsync(
                            Bitmap.CompressFormat.PNG,
                            30,
                            width,
                            height,
                            CropImageView.RequestSizeOptions.NONE,
                            saveUri
                    );
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        activityResultLauncher.launch("image/*");
    }
}