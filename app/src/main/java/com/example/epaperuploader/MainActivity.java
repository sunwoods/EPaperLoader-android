package com.example.epaperuploader;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.epaperuploader.communication.PermissionHelper;
import com.example.epaperuploader.image_processing.EPaperDisplay;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    // Request codes
    //-----------------------------
    public static final int REQ_BLUETOOTH_CONNECTION = 2;
    public static final int REQ_OPEN_FILE            = 3;
    public static final int REQ_DISPLAY_SELECTION    = 4;
    public static final int REQ_PALETTE_SELECTION    = 5;
    public static final int REQ_UPLOADING            = 6;
    public static final int SPI_PIN_CONFIG           = 7;

    private final static String TAG = MainActivity.class.getSimpleName();

    // Image file name and path
    //-----------------------------
    public static String fileName;
    public static String filePath;

    // Data
    //-----------------------------
    public static Bitmap originalImage; // Loaded image with original pixel format
    public static Bitmap indTableImage; // Filtered image with indexed colors

    // Views
    //-----------------------------
    public TextView mIndexSpiConfig;
    public TextView textBlue;
    public TextView textLoad;
    public TextView textDisp;
    public TextView textFilt;
    public Button button_file;

    public ImageView pictFile; // View of loaded image
    public ImageView pictFilt; // View of filtered image

    private CheckBox cbIsCompress;
    private static TextView tvSendInfo;
    private static Button btnUploadBt;
    private Button btnUploadWifi;
    private static Button btnLoadDefault;
    private static ProgressBar pbUploadProgress;

    private static TextView tvUploadRatio;
    private TextView tvDevAddress;
    private static TextView tvConnectionState;
    private static TextView tvIpAddress;
    private static TextView tvHostName;

    // Device
    //-----------------------------
    public static BluetoothDevice btDevice = null;

    public static SocketHandlerBt handlerBt = null;
    public SocketHandlerWifi handlerWifi = null;

    // service
    private static BluetoothLeService mBluetoothLeService = null;

    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

    private BluetoothGattCharacteristic mCharacterRead;
    private BluetoothGattCharacteristic mCharacterWriteMsg;
    private static BluetoothGattCharacteristic mCharacterWriteData;

    private static boolean mConnected = false;
    private String mDeviceAddress;

    private String mEsp32IpAddress = null;
    private String mEsp32HostName = null;

    // other
    SharedPreferences sharedPreferences = null;
    SharedPreferences.Editor config_editor;

    public static int din_pin = 14;
    public static int sck_pin = 13;
    public static int cs_pin = 15;
    public static int dc_pin = 27;
    public static int rst_pin = 26;
    public static int busy_pin = 25;

    public static Context context;

    public static int UPDATE_PROGESS = 1;
    public static int UPDATE_SEND_INFO = 2;
    public static int CONNECT_SUCCESS = 3;
    public static int CONNECT_FAILURE = 4;
    public static int DISCONNECT_SUCCESS = 5;
    public static int SEND_SUCCESS = 6;
    public static int SEND_FAILURE = 7;
    public static int RECEIVE_SUCCESS = 8;
    public static int RECEIVE_FAILURE = 9;
    public static int UPDATE_WIFI_PARAMS = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Image file name (null by default)
        //-----------------------------------------------------
        fileName = null;

        // Image file path (external storage root by default)
        //-----------------------------------------------------
        filePath = Environment.getExternalStorageDirectory().getAbsolutePath();

        mIndexSpiConfig = findViewById(R.id.index_spi_config);

        sharedPreferences = getSharedPreferences("config", Context.MODE_PRIVATE);
        config_editor = sharedPreferences.edit();
        reload_config();

        textBlue = findViewById(R.id.text_blue);
        textLoad = findViewById(R.id.text_file);
        textDisp = findViewById(R.id.text_disp);
        textFilt = findViewById(R.id.text_filt);

        pictFile = findViewById(R.id.pict_file);
        pictFilt = findViewById(R.id.pict_filt);
        button_file = findViewById(R.id.Button_file);

        context = this;
        // Data
        //-----------------------------
        originalImage = null;
        indTableImage = null;
        button_file.setEnabled(false);

        // View
        tvDevAddress = findViewById(R.id.device_address);
        // connect state
        tvConnectionState = findViewById(R.id.connect_state);
        // ip address
        tvIpAddress = findViewById(R.id.ip_address);
        // ip address
        tvHostName = findViewById(R.id.host_name);

        cbIsCompress = findViewById(R.id.is_compress);
        tvSendInfo = findViewById(R.id.send_info);
        btnUploadBt = findViewById(R.id.btn_upload_bt);
        btnUploadWifi = findViewById(R.id.btn_upload_wifi);
        btnLoadDefault = findViewById(R.id.btn_load_default);

        pbUploadProgress = findViewById(R.id.upload_progress);

        //--------------------------------------
        tvUploadRatio = findViewById(R.id.upload_text);
        tvUploadRatio.setText("0%");

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }


        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT},
                    0x3);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "ACCESS_FINE_LOCATION");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    0x9);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_ADMIN");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_ADMIN},
                    0x6);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "WRITE_EXTERNAL_STORAGE");
            //申请WRITE_EXTERNAL_STORAGE权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    0x3);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter(), RECEIVER_EXPORTED);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.config_menu, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        if (menu != null) {
            if (menu.getClass().getSimpleName().equalsIgnoreCase("MenuBuilder")) {
                try {
                    Method method = menu.getClass().getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE);
                    method.setAccessible(true);
                    method.invoke(menu, true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return super.onMenuOpened(featureId, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int item_id = item.getItemId();

        if (item_id == R.id.spi_pin_config) {
            startActivityForResult(
                    new Intent(this, SpiPinConfigActivity.class),
                    SPI_PIN_CONFIG);
        } else if (item_id == R.id.network_config) {
            Toast.makeText(this, "偷懒，没做^_^", Toast.LENGTH_SHORT).show();
        } else if (item_id == R.id.save_image) {
            saveImage();
        } else if (item_id == R.id.reboot_device) {
            rebootDevice();
        } else {
            Toast.makeText(this, "unknown option:" + item_id, Toast.LENGTH_SHORT).show();
            return false;
        }

        return super.onOptionsItemSelected(item);
    }

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                btDevice = null;
                mDeviceAddress = null;
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    public void reload_config() {
        String config_list_str = sharedPreferences.getString("config_list", "");
        // if not config spi
        if (config_list_str.isEmpty()) {
            // set to default
            mIndexSpiConfig.setText("首次使用，请先进菜单选择SPI管脚配置 ↗");
        } else {
            System.out.println(config_list_str);
            try {
                JSONArray jsonArray = new JSONArray(config_list_str);
                JSONObject jsonObject;
                for (int i = 0; i < jsonArray.length(); i++) {
                    jsonObject = jsonArray.getJSONObject(i);
                    boolean is_select = jsonObject.getBoolean("is_select");
                    // skip unselected
                    if (!is_select) {
                        continue;
                    }
                    String config_name = jsonObject.optString("config_name");
                    din_pin = jsonObject.getInt("din");
                    sck_pin = jsonObject.getInt("sck");
                    cs_pin = jsonObject.getInt("cs");
                    dc_pin = jsonObject.getInt("dc");
                    rst_pin = jsonObject.getInt("rst");
                    busy_pin = jsonObject.getInt("busy");
                    System.out.println(config_name + ":" + "din=" + din_pin + ", sck=" + sck_pin + ", cs=" + cs_pin
                            + ", dc=" + dc_pin + ", rst=" + rst_pin + ", busy=" + busy_pin + ", is_select=" + is_select);

                    SpiPinConfigInfo configInfo = new SpiPinConfigInfo(config_name, din_pin, sck_pin, cs_pin, dc_pin, rst_pin, busy_pin, is_select);
                    mIndexSpiConfig.setText(configInfo.getConfigVerbose());
                    return;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public void onScan(View view) {
        // Open bluetooth devices scanning activity
        //-----------------------------------------------------
        startActivityForResult(
                new Intent(this, DeviceScanActivity.class),
                REQ_BLUETOOTH_CONNECTION);
    }

    public void onDisplay(View view)
    {
        // Open display selection activity
        //-----------------------------------------------------
        startActivityForResult(
                new Intent(this, DisplaysActivity.class),
                REQ_DISPLAY_SELECTION);
    }

    public void onLoad(View view)
    {
        startActivityForResult(
                new Intent(this, CropImageActivity.class),
                REQ_OPEN_FILE
        );
    }

    // image process
    public void onFilter(View view) {
        // Check if any image is loaded
        //-----------------------------------------------------
        if (originalImage == null) {
            PermissionHelper.note(this, R.string.no_pict);
        }
        // Check if any display is selected
        //-----------------------------------------------------
        else if (EPaperDisplay.epdInd == -1) {
            PermissionHelper.note(this, R.string.no_disp);
        }

        // Open palette selection activity
        //-----------------------------------------------------
        else {
            startActivityForResult(
                    new Intent(this, FilteringActivity.class),
                    REQ_PALETTE_SELECTION);
        }
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            Log.e(TAG, "initialize Bluetooth Le Service");
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private void displayData(String data) {
        Log.e(TAG, "displayData:" + data);
        if (data != null) {
            try {
                JSONObject jsonObject = new JSONObject(data);
                mEsp32IpAddress = jsonObject.optString("ip_address");
                mEsp32HostName = jsonObject.optString("host_name");
                tvIpAddress.setText(mEsp32IpAddress);
                tvHostName.setText(mEsp32HostName);
                btnUploadWifi.setEnabled(true);
                Log.e(TAG, "ip_address:" + mEsp32IpAddress + ", host_name:" + mEsp32HostName);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        String serviceName = "E-Paper";
        String characterReadName = "Read WiFi IP";
        String characterWriteMsgName = "Write msg";
        String characterWriteDataName = "Write data";
        String characterName = "";
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        // iter GATT service
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            // get UUID
            uuid = gattService.getUuid().toString();
            // compare
            if (!SampleGattAttributes.lookup(uuid, unknownServiceString).equals(serviceName)) {
                continue;
            }

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            // get characteristics list
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                // get & compare UUID
                uuid = gattCharacteristic.getUuid().toString();
                characterName = SampleGattAttributes.lookup(uuid, unknownCharaString);
                if (characterName.equals(characterReadName)) {
                    mCharacterRead = gattCharacteristic;
                    mBluetoothLeService.readCharacteristic(gattCharacteristic);
                } else if (characterName.equals(characterWriteMsgName)) {
                    mCharacterWriteMsg = gattCharacteristic;
                } else if (characterName.equals(characterWriteDataName)) {
                    mCharacterWriteData = gattCharacteristic;
                } else {
                    continue;
                }
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }
    }

    public static void sendData(byte[] buffArr) {
        mCharacterWriteData.setValue(buffArr);
        mBluetoothLeService.writeCharacteristic(mCharacterWriteData);
    }

    private static void updateConnectionState(final int resourceId) {
        if (resourceId == R.string.connected) {
            tvConnectionState.setTextColor(Color.GREEN);
            mConnected = true;
            btnUploadBt.setEnabled(true);
            btnLoadDefault.setEnabled(true);
        } else {
            tvConnectionState.setTextColor(Color.RED);
            mConnected = false;
            btnUploadBt.setEnabled(false);
            btnLoadDefault.setEnabled(false);

            // clear wifi params when disconnected
            clearWifiParams();
        }
        tvConnectionState.setText(resourceId);
    }

    private static void clearWifiParams() {
        tvIpAddress.setText("");
        tvHostName.setText("");
    }

    private static void updateWifiParams(String rcvData) {
        try {
            JSONObject jsonObject = new JSONObject(rcvData);
            tvIpAddress.setText(jsonObject.optString("IP_ADDRESS"));
            tvHostName.setText(jsonObject.optString("HOST_NAME"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    public void onUploadBt(View view) {
        int epdInd = EPaperDisplay.epdInd;
        boolean is_compress;
        if (cbIsCompress.isChecked()) {
            is_compress = true;
        } else {
            is_compress = false;
        }
        // keep screen on
//        view.setKeepScreenOn(true);
        handlerBt = new SocketHandlerBt();
        handlerBt.init_image(indTableImage, is_compress, null);
    }

    public void onUploadWifi(View view) {
        int epdInd = EPaperDisplay.epdInd;
        int width = EPaperDisplay.getDisplays()[EPaperDisplay.epdInd].width;
        int height = EPaperDisplay.getDisplays()[EPaperDisplay.epdInd].height;

        boolean is_compress;
        if (cbIsCompress.isChecked()) {
            is_compress = true;
        } else {
            is_compress = false;
        }

        handlerWifi = new SocketHandlerWifi(mEsp32IpAddress, mEsp32HostName);
        handlerWifi.init_image(indTableImage, is_compress);

        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "http write start");
                // EPD: initial
                // LOADA: send phase 1 data
                // LOADB: send phase 2 data
                // SHOW: display image
                String uri = "EPD";
                try {
                    // init params include: epd index and spi pin configuration
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("epdInd", epdInd);
                    jsonObject.put("din", din_pin);
                    jsonObject.put("sck", sck_pin);
                    jsonObject.put("cs", cs_pin);
                    jsonObject.put("dc", dc_pin);
                    jsonObject.put("rst", rst_pin);
                    jsonObject.put("busy", busy_pin);
                    jsonObject.put("width", width);
                    jsonObject.put("height", height);

                    String post_data = jsonObject.toString();

                    handlerWifi.httpWrite(uri, post_data);

                    boolean is_continue = true;
                    while (is_continue) {
                        is_continue = handlerWifi.handleUploadingStage();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.e(TAG, "requestCode:" + requestCode + ", resultCode:" + resultCode);
        //-----------------------------------------------------
        //  Messages form ScanningActivity
        //-----------------------------------------------------
        if (requestCode == REQ_BLUETOOTH_CONNECTION) {
            // Bluetooth device was found and selected
            //-------------------------------------------------
            if (resultCode == RESULT_OK) {
                // Get selected bluetooth device
                //---------------------------------------------
                btDevice = data.getParcelableExtra("DEVICE");

                // Show name and address of the device
                //---------------------------------------------
                textBlue.setText(btDevice.getName() + " (" + btDevice.getAddress() + ")");

                // device address
                mDeviceAddress = btDevice.getAddress();
                tvDevAddress.setText(mDeviceAddress);

                //--------------------------------------
                tvUploadRatio.setText("0%");

                Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
                boolean bindResult = bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
                Log.e(TAG, "mServiceConnection: " + mServiceConnection + " bindResult: " + bindResult);

                if (mBluetoothLeService != null) {
                    final boolean result = mBluetoothLeService.connect(mDeviceAddress);
                    Log.d(TAG, "Connect request result=" + result);
                }
            }
        }

        //-----------------------------------------------------
        //  Message form open file activity
        //-----------------------------------------------------
        else if (requestCode == REQ_OPEN_FILE) {
            if (resultCode == RESULT_OK) {
                // Getting file name and file path
                //---------------------------------------------
                Uri image_uri = data.getParcelableExtra("FILE_NAME");
                textLoad.setText(image_uri.getPath());

                // Loading of the selected file
                //---------------------------------------------
                InputStream is = null;
                try {
                    int width = EPaperDisplay.getDisplays()[EPaperDisplay.epdInd].width;
                    int height = EPaperDisplay.getDisplays()[EPaperDisplay.epdInd].height;

                    originalImage = MediaStore.Images.Media.getBitmap(this.getContentResolver(), image_uri);
                    originalImage = Bitmap.createScaledBitmap(originalImage, width, height, false);
                    int pictWidth = originalImage.getWidth();
                    int pictHeight = originalImage.getHeight();
                    pictFile.setMaxHeight(Math.min(300, pictWidth));
                    pictFile.setMinimumHeight(Math.min(200, pictWidth / 2));
                    pictFile.setImageBitmap(originalImage);
                    textLoad.setText("width:" + pictWidth + ", height:" + pictHeight);
                } catch (FileNotFoundException e) {
                    textFilt.setText(R.string.failed_file);
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    textFilt.setText(R.string.failed_file);
                    throw new RuntimeException(e);
                }
            }
        }

        //-----------------------------------------------------
        //  Message form display selection activity
        //-----------------------------------------------------
        else if (requestCode == REQ_DISPLAY_SELECTION) {
            if (resultCode == RESULT_OK) {
                textDisp.setText(EPaperDisplay.getDisplays()[EPaperDisplay.epdInd].title);
                button_file.setEnabled(true);
            }
        }

        //-----------------------------------------------------
        //  Message form palette selection activity
        //-----------------------------------------------------
        else if (requestCode == REQ_PALETTE_SELECTION) {
            if (resultCode == RESULT_OK) {
                textFilt.setText(data.getStringExtra("NAME"));

                try {
                    int size = pictFile.getHeight();
                    pictFilt.setMaxHeight(Math.min(300, size));
                    pictFilt.setMinimumHeight(Math.min(200, size / 2));
                    pictFilt.setImageBitmap(indTableImage);
                } catch (Exception e) {
                    textFilt.setText(R.string.failed_filt);
                }
            }
        } else if (requestCode == SPI_PIN_CONFIG) {
            if (resultCode == RESULT_OK) {
                reload_config();
            }
        }
    }

    private void clearUI() {
        tvDevAddress.setText("");
        tvIpAddress.setText("");
        tvHostName.setText("");
    }

    @SuppressLint("HandlerLeak")
    public static Handler mUIHandler = new Handler(){
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (msg.what == UPDATE_PROGESS) {
                tvUploadRatio.setText(msg.arg1 + "%");
                pbUploadProgress.setProgress(msg.arg1);
            } else if (msg.what == UPDATE_SEND_INFO) {
                tvSendInfo.setText((String)(msg.obj));
            } else if (msg.what == CONNECT_SUCCESS) {
                updateConnectionState(R.string.connected);
            } else if (msg.what == CONNECT_FAILURE) {
                updateConnectionState(R.string.connect_fail);
            } else if (msg.what == DISCONNECT_SUCCESS) {
                updateConnectionState(R.string.disconnected);
            } else if (msg.what == UPDATE_WIFI_PARAMS) {
                updateWifiParams((String)msg.obj);
            }
        }
    };

    public static void updateProgress(int progress) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Message msg = new Message();
                msg.what = UPDATE_PROGESS;
                msg.arg1 = progress;
                mUIHandler.sendMessage(msg);
            }
        }).start();
    }


    public static void updateSendInfo(String info) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Message msg = new Message();
                msg.what = UPDATE_SEND_INFO;
                msg.obj = info;
                mUIHandler.sendMessage(msg);
            }
        }).start();
    }


    public void saveImage() {
        // Check if any image is loaded
        //-----------------------------------------------------
        if (originalImage == null) {
            PermissionHelper.note(this, R.string.no_pict);
        }
        // Check if any display is selected
        //-----------------------------------------------------
        else if (EPaperDisplay.epdInd == -1) {
            PermissionHelper.note(this, R.string.no_disp);
        }
        else if (indTableImage == null) {
            PermissionHelper.note(this, R.string.no_filt);
        } else {
            int h = indTableImage.getHeight();
            int w = indTableImage.getWidth();
            int[] saved_image_data = new int[w*h];
            int i = 0;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++, i++)
                    saved_image_data[i] = indTableImage.getPixel(x, y);
            Gson gson = new Gson();
            String saved_image_str = gson.toJson(saved_image_data);
            config_editor.putString("saved_image_" + EPaperDisplay.epdInd, saved_image_str);
            config_editor.commit();
            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
        }
    }

    public void loadDefault(View view) {
        if (EPaperDisplay.epdInd == -1) {
            PermissionHelper.note(this, R.string.no_disp);
            return;
        }

        String saved_image_str = sharedPreferences.getString("saved_image_" + EPaperDisplay.epdInd, "");
        if (saved_image_str.isEmpty()) {
            Toast.makeText(this, "没有找到图像，请先保存", Toast.LENGTH_SHORT).show();
            return;
        }
        Gson gson = new Gson();
        int[] saved_image_data = gson.fromJson(saved_image_str, int [].class);
        Toast.makeText(this, "像素数：" + saved_image_data.length, Toast.LENGTH_SHORT).show();
        System.out.println(saved_image_data.length);

        boolean is_compress = cbIsCompress.isChecked();
        // keep screen on
//        view.setKeepScreenOn(true);
        handlerBt = new SocketHandlerBt();
        handlerBt.init_image(MainActivity.indTableImage, is_compress, saved_image_data);
    }

    public void rebootDevice() {
        if (btDevice == null) {
            Toast.makeText(this, "蓝牙设备未连接", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "设备重启中...", Toast.LENGTH_SHORT).show();

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("COMMAND", "reboot_device");
            byte[] send_bytes = jsonObject.toString().getBytes(StandardCharsets.UTF_8);
            sendData(send_bytes);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}