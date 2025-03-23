package com.example.epaperuploader;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

import java.util.ArrayList;

public class SpiPinEditActivity extends AppCompatActivity {

    private EditText mConfigName;
    private EditText mCsPin;
    private EditText mDcPin;
    private EditText mDinPin;
    private EditText mSckPin;
    private EditText mRstPin;
    private EditText mBusyPin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spi_pin_edit);

        getSupportActionBar().setTitle("配置编辑");

        mConfigName = findViewById(R.id.edit_config_name);
        mCsPin = findViewById(R.id.edit_cs_pin);
        mDcPin = findViewById(R.id.edit_dc_pin);
        mDinPin = findViewById(R.id.edit_din_pin);
        mSckPin = findViewById(R.id.edit_sck_pin);
        mRstPin = findViewById(R.id.edit_rst_pin);
        mBusyPin = findViewById(R.id.edit_busy_pin);
    }

    SpiPinConfigInfo checkEditConfig() {
        String config_name = mConfigName.getText().toString().trim();
        String din_pin = mDinPin.getText().toString().trim();
        String sck_pin = mSckPin.getText().toString().trim();
        String cs_pin = mCsPin.getText().toString().trim();
        String dc_pin = mDcPin.getText().toString().trim();
        String rst_pin = mRstPin.getText().toString().trim();
        String busy_pin = mBusyPin.getText().toString().trim();
        if (config_name.isEmpty()
                || din_pin.isEmpty()
                || sck_pin.isEmpty()
                || cs_pin.isEmpty()
                || dc_pin.isEmpty()
                || rst_pin.isEmpty()
                || busy_pin.isEmpty()) {
            Toast.makeText(this, "错误：以上填写字段不允许为空", Toast.LENGTH_SHORT).show();
            return null;
        } else {
            ArrayList<SpiPinConfigInfo> spi_config_list = SpiPinConfigActivity.get_spi_config_list();
            for (int i = 0; i < spi_config_list.size(); i++) {
                if (config_name.equals(spi_config_list.get(i).getConfigName())) {
                    Toast.makeText(this, "错误：与已保存的配置名称冲突", Toast.LENGTH_SHORT).show();
                    return null;
                }
            }
            SpiPinConfigInfo configInfo = new SpiPinConfigInfo(config_name,
                    Integer.valueOf(din_pin), Integer.valueOf(sck_pin), Integer.valueOf(cs_pin),
                    Integer.valueOf(dc_pin), Integer.valueOf(rst_pin), Integer.valueOf(busy_pin),
                    false
            );
            return configInfo;
        }
    }

    public void saveEditConfig(View view) {
        SpiPinConfigInfo configInfo = checkEditConfig();
        if (configInfo == null) {
            return;
        }
        Toast.makeText(this, "配置检查通过", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent();
        Gson gson = new Gson();
        intent.putExtra("NEW_CONFIG", gson.toJson(configInfo));
        setResult(RESULT_OK, intent);
        finish();
    }
}