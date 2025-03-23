package com.example.epaperuploader;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.RadioButton;
import android.widget.TextView;

import java.util.ArrayList;

//---------------------------------------------------------
//  File list adapter
//---------------------------------------------------------
public class SpiListAdapter extends BaseAdapter
{
    private ArrayList<SpiPinConfigInfo> mConfigInfoList;
    private LayoutInflater mInflator;

    public SpiListAdapter(Context context)
    {
        super();
        mConfigInfoList = new ArrayList<>();
        mInflator = LayoutInflater.from(context);
    }

    public void add(SpiPinConfigInfo configInfo) {
        mConfigInfoList.add(configInfo);
    }

    public void del(int i) {
        mConfigInfoList.remove(i);
    }

    public void clear() {
        mConfigInfoList.clear();
    }

    @Override
    public int getCount() {
        return mConfigInfoList.size();
    }

    @Override
    public SpiPinConfigInfo getItem(int i) {
        return mConfigInfoList.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    public ArrayList<SpiPinConfigInfo> getList() {
        return mConfigInfoList;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder viewHolder;
        // General ListView optimization code.
        if (view == null) {
            view = mInflator.inflate(R.layout.spi_list_item, viewGroup, false);
            viewHolder = new ViewHolder();
            viewHolder.config_name = view.findViewById(R.id.config_name);
            viewHolder.config_verbose = view.findViewById(R.id.config_verbose);
            viewHolder.config_select = view.findViewById(R.id.config_select);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        SpiPinConfigInfo configInfo = mConfigInfoList.get(i);
        viewHolder.config_name.setText(configInfo.getConfigName());
        viewHolder.config_verbose.setText(configInfo.getConfigVerbose());
        viewHolder.config_select.setChecked(configInfo.getSelectStatus());

        return view;
    }

    private class ViewHolder {
        TextView config_name;
        TextView config_verbose;
        RadioButton config_select;
    }
}