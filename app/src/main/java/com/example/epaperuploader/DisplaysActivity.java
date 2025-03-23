package com.example.epaperuploader;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.epaperuploader.image_processing.EPaperDisplay;

public class DisplaysActivity extends AppCompatActivity implements AdapterView.OnItemClickListener
{
    // View of displays list
    //---------------------------------------------------------
    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_displays);
        getSupportActionBar().setTitle(R.string.disp);

        // ListView, its adapter and listener
        //--------------------------------------
        listView = findViewById(R.id.displays_list);
        listView.setAdapter(new DisplayListAdapter(this, EPaperDisplay.getDisplays()));
        listView.setOnItemClickListener(this);
    }

    public void onCancel(View view) {
        onBackPressed();
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED, new Intent());
        finish();
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        EPaperDisplay.epdInd = i;
        setResult(RESULT_OK, new Intent());
        finish();
    }


    //---------------------------------------------------------
    //  File list adapter
    //---------------------------------------------------------
    private class DisplayListAdapter extends ArrayAdapter<EPaperDisplay> {
        public DisplayListAdapter(Context context, EPaperDisplay[] array) {
            // Standard one-line item layout
            //-------------------------------------------------
            super(context, android.R.layout.simple_list_item_1, array);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view = (TextView) super.getView(position, convertView, parent);
            if (view != null) view.setText(EPaperDisplay.getDisplays()[position].title);
            return view;
        }
    }
}