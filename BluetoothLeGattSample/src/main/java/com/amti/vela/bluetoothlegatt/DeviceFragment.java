package com.amti.vela.bluetoothlegatt;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class DeviceFragment extends android.support.v4.app.Fragment {
    public static final int LOW_BATTERY_THRESHOLD = 20;

    RelativeLayout relativeLayout;
    ImageView batteryFiller;
    ImageView criticalFiller;
    TextView batteryText;
    TextView deviceNameText;
    TextView deviceAddressText;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        RelativeLayout relativeLayout = initGui(inflater, container);
        return relativeLayout;
    }

    RelativeLayout initGui(LayoutInflater inflater, ViewGroup container)
    {
        relativeLayout = (RelativeLayout)inflater.inflate(R.layout.fragment_device, container, false);
        batteryFiller = (ImageView) relativeLayout.findViewById(R.id.battery_filler);
        criticalFiller = (ImageView) relativeLayout.findViewById(R.id.critical_filler);
        batteryText = (TextView) relativeLayout.findViewById(R.id.battery_text);
        deviceNameText = (TextView) relativeLayout.findViewById(R.id.device_name);
        deviceAddressText = (TextView) relativeLayout.findViewById(R.id.device_address);

        updateBattery(0);

        return relativeLayout;
    }

    public void updateBattery(int batteryLevel)
    {
        int px;
        if(batteryLevel <= LOW_BATTERY_THRESHOLD)
        {
            float batteryFactor = batteryLevel / 100.0f;
            int defaultWidth = (int) getResources().getDimension(R.dimen.battery_filler_default_width);
            px = (int) (batteryFactor * defaultWidth + 0.5f);
            criticalFiller.getLayoutParams().width = px;
            criticalFiller.requestLayout();

            batteryFiller.setVisibility(View.GONE);
            criticalFiller.setVisibility(View.VISIBLE);
        }
        else
        {
            float batteryFactor = batteryLevel / 100.0f;
            int defaultWidth = (int) getResources().getDimension(R.dimen.battery_filler_default_width);
            px = (int) (batteryFactor * defaultWidth + 0.5f);
            batteryFiller.getLayoutParams().width = px;
            batteryFiller.requestLayout();

            batteryFiller.setVisibility(View.VISIBLE);
            criticalFiller.setVisibility(View.GONE);
        }

        batteryText.setText(Integer.toString(batteryLevel) + "%");
    }

    public void setDevice(String name, String addr)
    {
        deviceNameText.setText(name);
        deviceAddressText.setText(addr);
    }
}
