package com.example.epaperuploader;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class SpiPinConfigActivity extends AppCompatActivity {
    // View of displays list
    //---------------------------------------------------------
    private static ListView mSpiList;

    private static SpiListAdapter mSpiListAdapter = null;

    public static final int SPI_PIN_EDIT           = 2;

    static SharedPreferences sharedPreferences = null;
    static SharedPreferences.Editor config_editor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spi_pin_config);

        getSupportActionBar().setTitle("管脚配置选择 & 添加");

        sharedPreferences = getSharedPreferences("config", Context.MODE_PRIVATE);
        config_editor = sharedPreferences.edit();

        mSpiListAdapter = new SpiListAdapter(this);
        mSpiList = findViewById(R.id.spi_list);
        mSpiList.setAdapter(mSpiListAdapter);

        mSpiList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                set_config_select(i);
            }
        });

        recover_spi_list();

        registerForContextMenu(mSpiList);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        getMenuInflater().inflate(R.menu.config_menu_sub, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo menuInfo;
        menuInfo = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        if (item.getItemId() == R.id.config_menu_delete) {
            int item_id = menuInfo.position;
            if (item_id == 0) {
                Toast.makeText(this, "失败：默认配置不可删除", Toast.LENGTH_SHORT).show();
            } else {
                del_spi_config(item_id);
            }
        }
        return super.onContextItemSelected(item);
    }

    private void recover_spi_list() {
        String config_list_str = sharedPreferences.getString("config_list", "");
        // 如果没有配置过SPI
        if (config_list_str.isEmpty()) {
            // 则下发默认配置
            // sck=13, din=14, cs=15, busy=25, rst=26, dc=27
            SpiPinConfigInfo configInfo = new SpiPinConfigInfo("默认配置",
                    14, 13, 15, 27, 26, 25, false);
            add_spi_config(configInfo);
            update_config_list(true);
        } else {
            try {
                JSONArray jsonArray = new JSONArray(config_list_str);
                JSONObject jsonObject;
                for (int i = 0; i < jsonArray.length(); i++) {
                    jsonObject = jsonArray.getJSONObject(i);
                    String config_name = jsonObject.optString("config_name");
                    int din = jsonObject.getInt("din");
                    int sck = jsonObject.getInt("sck");
                    int cs = jsonObject.getInt("cs");
                    int dc = jsonObject.getInt("dc");
                    int rst = jsonObject.getInt("rst");
                    int busy = jsonObject.getInt("busy");
                    boolean is_select = jsonObject.getBoolean("is_select");
                    System.out.println(config_name + ":" + "din=" + din + ", sck=" + sck + ", cs="
                            + cs + ", dc=" + dc + ", rst=" + rst + ", busy=" + busy + ", is_select=" + is_select);
                    add_spi_config(new SpiPinConfigInfo(config_name, din, sck, cs, dc, rst, busy, is_select));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public static ArrayList<SpiPinConfigInfo> get_spi_config_list() {
        return mSpiListAdapter.getList();
    }

    public void add_spi_config(SpiPinConfigInfo configInfo) {
        mSpiListAdapter.add(configInfo);
        update_config_list(true);
    }

    public void del_spi_config(int item_id) {
        mSpiListAdapter.del(item_id);
        boolean has_select = false;
        // 此处做保护性检查
        for (int i = 0; i < mSpiListAdapter.getCount(); i ++) {
            if (mSpiListAdapter.getItem(i).getSelectStatus()) {
                has_select = true;
                break;
            }
        }
        if (has_select != true) {
            mSpiListAdapter.getItem(0).setSelectStatus(true);
            setResult(RESULT_OK, new Intent());
        }
        update_config_list(true);
    }

    private void set_config_select(int select_id) {
        for (int i = 0; i < mSpiListAdapter.getCount(); i ++) {
            if (i == select_id) {
                mSpiListAdapter.getItem(i).setSelectStatus(true);
            } else {
                mSpiListAdapter.getItem(i).setSelectStatus(false);
            }
        }

        update_config_list(true);

        setResult(RESULT_OK, new Intent());
        finish();
    }

    private void update_config_list(boolean has_change) {
        // 更新表格
        mSpiListAdapter.notifyDataSetChanged();

        // 保存列表
        if (has_change) {
            Gson gson = new Gson();
            String config_list = gson.toJson(mSpiListAdapter.getList());

            config_editor.putString("config_list", config_list);
            config_editor.commit();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SPI_PIN_EDIT) {
            if (resultCode == RESULT_OK) {
                String new_config = data.getStringExtra("NEW_CONFIG");
                System.out.println("NEW:" + new_config);
                try {
                    JSONObject jsonObject = new JSONObject(new_config);

                    String config_name = jsonObject.optString("config_name");
                    int din = jsonObject.getInt("din");
                    int sck = jsonObject.getInt("sck");
                    int cs = jsonObject.getInt("cs");
                    int dc = jsonObject.getInt("dc");
                    int rst = jsonObject.getInt("rst");
                    int busy = jsonObject.getInt("busy");
                    boolean is_select = jsonObject.getBoolean("is_select");
                    System.out.println(config_name + ":" + "din=" + din + ", sck=" + sck + ", cs="
                            + cs + ", dc=" + dc + ", rst=" + rst + ", busy=" + busy + ", is_select=" + is_select);
                    add_spi_config(new SpiPinConfigInfo(config_name, din, sck, cs, dc, rst, busy, is_select));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void addSpiPinConfig(View view) {
        startActivityForResult(
                new Intent(this, SpiPinEditActivity.class),
                SPI_PIN_EDIT);
    }

    public static SpiPinConfigInfo getSpiConfig() {
        SpiPinConfigInfo configInfo;
        for (int i = 0; i < mSpiListAdapter.getCount(); i ++) {
            configInfo = mSpiListAdapter.getItem(i);
            if (configInfo.getSelectStatus()) {
                return configInfo;
            }
        }

        return null;
    }
}