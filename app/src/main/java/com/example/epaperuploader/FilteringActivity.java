package com.example.epaperuploader;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.epaperuploader.image_processing.EPaperDisplay;
import com.example.epaperuploader.image_processing.EPaperPicture;


/**
 * <h1>Filtering activity</h1>
 * The activity offers to select one of available image filters,
 * which converts the loaded image for better pixel format
 * converting required for selected display.
 *
 * @author  Waveshare team
 * @version 1.0
 * @since   8/18/2018
 */


public class FilteringActivity extends AppCompatActivity
{
    // View
    //------------------------------------------
    private Button    button;
    private TextView  textView;
    private ImageView imageView;

    private Button btnLevelMono;
    private Button btnLevelColor;
    private Button btnDitherMono;
    private Button btnDitherMono2;
    private Button btnDitherColor;
    private Button btnDitherColor2;

    private ImageView imgLevelMono;
    private ImageView imgLevelColor;
    private ImageView imgDitherMono;
    private ImageView imgDitherMono2;
    private ImageView imgDitherColor;
    private ImageView imgDitherColor2;

    private final static String TAG = FilteringActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_filtering);
        getSupportActionBar().setTitle(R.string.filt);

        // View
        //------------------------------------------
        textView  = findViewById(R.id.txt_indexed);
        imageView = findViewById(R.id.img_indexed);

        btnLevelMono = findViewById(R.id.btn_wb_l);
        btnLevelColor = findViewById(R.id.btn_wbrl);
        btnDitherMono = findViewById(R.id.btn_wb_d);
        btnDitherMono2 = findViewById(R.id.btn_wb_d2);
        btnDitherColor = findViewById(R.id.btn_wbrd);
        btnDitherColor2 = findViewById(R.id.btn_wbrd2);

        imgLevelMono = findViewById(R.id.img_level_mono);
        imgLevelColor = findViewById(R.id.img_level_color);
        imgDitherMono = findViewById(R.id.img_dither_mono);
        imgDitherMono2 = findViewById(R.id.img_dither_mono2);
        imgDitherColor = findViewById(R.id.img_dither_color);
        imgDitherColor2 = findViewById(R.id.img_dither_color2);

        // Disable unavailable palettes
        //------------------------------------------
        boolean redIsEnabled = (EPaperDisplay.getDisplays()[EPaperDisplay.epdInd].index & 1) != 0;

        btnLevelColor.setEnabled(redIsEnabled);
        btnDitherColor.setEnabled(redIsEnabled);
        btnDitherColor2.setEnabled(redIsEnabled);

        // Disable unavailable palettes just for 5.65f e-Paper
        //------------------------------------------
        if(EPaperDisplay.epdInd == 25 || EPaperDisplay.epdInd == 37 || EPaperDisplay.epdInd == 43 || EPaperDisplay.epdInd == 47)
        {
            redIsEnabled = false;
            btnLevelMono.setEnabled(redIsEnabled);
            btnDitherMono.setEnabled(redIsEnabled);
            btnDitherMono2.setEnabled(redIsEnabled);
        }

        // 通过模拟点击，来实现预览
        if (btnLevelMono.isEnabled()) {
            btnLevelMono.callOnClick();
        }
        if (btnLevelColor.isEnabled()) {
            btnLevelColor.callOnClick();
        }
        if (btnDitherMono.isEnabled()) {
            btnDitherMono.callOnClick();
        }
        if (btnDitherMono2.isEnabled()) {
            btnDitherMono2.callOnClick();
        }
        if (btnDitherColor.isEnabled()) {
            btnDitherColor.callOnClick();
        }

        if (btnDitherColor2.isEnabled()) {
            btnDitherColor2.callOnClick();
        }
    }

    public void onImgClick(View view) {
        int img_id = view.getId();
        String name = "";
        Button btn;
        if (img_id == R.id.img_level_mono) {
            btn = btnLevelMono;
        } else if (img_id == R.id.img_level_color) {
            btn = btnLevelColor;
        } else if (img_id == R.id.img_dither_mono) {
            btn = btnDitherMono;
        } else if (img_id == R.id.img_dither_mono2) {
            btn = btnDitherMono2;
        } else if (img_id == R.id.img_dither_color) {
            btn = btnDitherColor;
        } else if (img_id == R.id.img_dither_color2) {
            btn = btnDitherColor2;
        } else {
            return;
        }

        name = btn.getText().toString();

        Log.e("TAG", "Image id: " + img_id + ", Name: " + name);
        Intent intent = new Intent();
        intent.putExtra("NAME", name);

        btn.callOnClick();

        setResult(RESULT_OK, intent);
        finish();
    }

    // Accept the selected
    public void onOk(View view)
    {
        // If palette is not selected, then exit
        //-----------------------------------------------------
        if (button == null) return;

        // Close palette activity and return palette's name
        //-----------------------------------------------------
        Intent intent = new Intent();
        intent.putExtra("NAME", button.getText().toString());

        setResult(RESULT_OK, intent);
        finish();
    }

    public void onCancel(View view)
    {
        onBackPressed();
    }

    @Override
    public void onBackPressed()
    {
        setResult(RESULT_CANCELED);
        finish();
    }

    public void onButtonClick(View view) {
        int btnId = view.getId();
        boolean isLvl;
        boolean isRed;
        boolean isEnhance = false;
        if (btnId == R.id.btn_wb_l) {
            isLvl = true;
            isRed = false;
        } else if (btnId == R.id.btn_wbrl) {
            isLvl = true;
            isRed = true;
        } else if (btnId == R.id.btn_wb_d) {
            isLvl = false;
            isRed = false;
        } else if (btnId == R.id.btn_wb_d2) {
            isLvl = false;
            isRed = false;
            isEnhance = true;
        } else if (btnId == R.id.btn_wbrd) {
            isLvl = false;
            isRed = true;
        } else if (btnId == R.id.btn_wbrd2) {
            isLvl = false;
            isRed = true;
            isEnhance = true;
        } else {
            Log.e(TAG, "Error id: " + btnId + ", name: " + view.getTransitionName());
            return;
        }
        button = (Button)view;
        run(isLvl, isRed, isEnhance);
    }

    public void run(boolean isLvl, boolean isRed, boolean isEnhance)
    {
        // Image processing
        //-----------------------------------------------------
        MainActivity.indTableImage = EPaperPicture.createIndexedImage(isLvl, isRed, isEnhance);

        // Image view size calculation
        //-----------------------------------------------------
        int size = textView.getWidth();

        imageView.setMaxHeight(Math.min(300, size));
        imageView.setMinimumHeight(Math.min(200, size / 2));
        imageView.setImageBitmap(MainActivity.indTableImage);

        ImageView sampleImageView;
        if (isLvl) {
            if (isRed) {
                sampleImageView = imgLevelColor;
            } else {
                sampleImageView = imgLevelMono;
            }
        } else {
            if (isRed) {
                if (isEnhance) {
                    sampleImageView = imgDitherColor2;
                } else {
                    sampleImageView = imgDitherColor;
                }
            } else {
                if (isEnhance) {
                    sampleImageView = imgDitherMono2;
                } else {
                    sampleImageView = imgDitherMono;
                }
            }
        }

        sampleImageView.setMaxHeight(Math.min(300, size));
        sampleImageView.setMinimumHeight(Math.min(200, size / 2));
        sampleImageView.setImageBitmap(MainActivity.indTableImage);

        // Show selected image filter
        //-----------------------------------------------------
        textView.setText(button.getText());
    }
}
