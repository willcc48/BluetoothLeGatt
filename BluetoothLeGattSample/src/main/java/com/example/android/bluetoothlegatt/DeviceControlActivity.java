/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.SaturationBar;
import com.larswerkman.holocolorpicker.ValueBar;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity implements ColorPicker.OnColorChangedListener, DialogInterface.OnCancelListener {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String ANALOG_ANALOG_OUT_UUID = "866ad1ee-05c4-4f4e-9ef4-548790668ad1";

    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    public BluetoothGattCharacteristic myCharacteristic;

    private final static int kFirstTimeColor = 0x0000ff;
    private ColorPicker mColorPicker;
    private TextView mRgbTextView;
    private int mSelectedColor;
    int r, g, b;

    ProgressDialog alertDialog;

    private BroadcastReceiver onNotice = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (mConnected)
            {
                notificationThread.start();
            }
        }
    };

    Thread notificationThread = new Thread() {
        @Override
        public void run() {
            try
            {
                mBluetoothLeService.writeCharacteristic(myCharacteristic,"255000000" );
                Thread.sleep(500);
                mBluetoothLeService.writeCharacteristic(myCharacteristic,"000000000");
                Thread.sleep(500);
                mBluetoothLeService.writeCharacteristic(myCharacteristic,"255000000");
                Thread.sleep(500);
                mBluetoothLeService.writeCharacteristic(myCharacteristic,"000000000" );
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };


    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };
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
                mConnected = true;
                alertDialog.dismiss();
                startService(new Intent(DeviceControlActivity.this, NotificationService.class));
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                Toast.makeText(getApplicationContext(), "You have lost connection to the device", Toast.LENGTH_SHORT).show();
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_CONNECTING.equals(action)) {
                mConnected = false;
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.

                List<BluetoothGattService> gattServices = mBluetoothLeService.getSupportedGattServices();
                for(BluetoothGattService service : gattServices)
                {
                    List<BluetoothGattCharacteristic> gattCharacteristics = service.getCharacteristics();
                    for(BluetoothGattCharacteristic characteristic : gattCharacteristics)
                    {
                        if (characteristic.getUuid().toString().equals(ANALOG_ANALOG_OUT_UUID))
                        {
                            myCharacteristic = characteristic;
                        }
                    }
                }
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

            }
        }
    };



    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    public final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();

                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {

                            myCharacteristic = characteristic;
                            Log.w(TAG, myCharacteristic.toString());
                            //mBluetoothLeService.writeCharacteristic(myCharacteristic, newValueText);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            try{
                                Thread.sleep(100);
                            }catch(InterruptedException e){
                                Log.w(TAG, "I got interrupted!");
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        return true;
                    }
                    return false;
                }
            };


    @Override
    public void onColorChanged(int color) {
        // Save selected color
        mSelectedColor = color;

        r = (color >> 16) & 0xFF;
        g = (color >> 8) & 0xFF;
        b = (color >> 0) & 0xFF;
        String text = String.format("R:%1$s G:%2$s B:%3$s", r, g, b);
        mRgbTextView.setText(text);
    }



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        ActionBar bar = getActionBar();
        bar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.light_blue)));
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(getResources().getColor(R.color.dark_blue));

        mRgbTextView = (TextView) findViewById(R.id.rgbTextView);

        SaturationBar mSaturationBar = (SaturationBar) findViewById(R.id.saturationbar);
        ValueBar mValueBar = (ValueBar) findViewById(R.id.valuebar);
        mColorPicker = (ColorPicker) findViewById(R.id.colorPicker);
        if (mColorPicker != null) {
            mColorPicker.addSaturationBar(mSaturationBar);
            mColorPicker.addValueBar(mValueBar);
            mColorPicker.setOnColorChangedListener(this);
        }

        mSelectedColor = kFirstTimeColor;

        mColorPicker.setOldCenterColor(mSelectedColor);
        mColorPicker.setColor(mSelectedColor);
        onColorChanged(mSelectedColor);

        final Intent intent = getIntent();
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        Button sendButton = (Button) findViewById(R.id.sendValue);

        sendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Set the old color
                mColorPicker.setOldCenterColor(mSelectedColor);

                String redString = "";
                String greenString = "";
                String blueString = "";

                if (r < 100) { redString += "0"; }
                if (r < 10) { redString += "0"; }

                if (g < 100) { greenString += "0"; }
                if (g < 10) { greenString += "0"; }

                if (b < 100) { blueString += "0"; }
                if (b < 10) { blueString += "0"; }

                redString += Integer.toString(r);
                greenString += Integer.toString(g);
                blueString += Integer.toString(b);

                mBluetoothLeService.writeCharacteristic(myCharacteristic, redString + greenString + blueString);

                Log.v(TAG, "I clicked");
            }
        });

        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, new IntentFilter("Msg"));

        alertDialog = new ProgressDialog(this);
        alertDialog.setMessage("Please wait while connecting to the device.");
        alertDialog.setOnCancelListener(this);
        alertDialog.setCancelable(true);
        alertDialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_bt).setTitle("Disconnect");
            menu.findItem(R.id.menu_bt).setIcon(R.mipmap.ic_action_bluetooth_connected);
            menu.findItem(R.id.menu_bt).setVisible(true);
            menu.findItem(R.id.menu_searching).setVisible(false);
        } else {
            menu.findItem(R.id.menu_bt).setTitle("Connecting");
            menu.findItem(R.id.menu_bt).setIcon(R.mipmap.ic_action_bluetooth);
            menu.findItem(R.id.menu_bt).setVisible(false);
            menu.findItem(R.id.menu_searching).setActionView(R.layout.actionbar_indeterminate_progress);
            menu.findItem(R.id.menu_searching).setActionView(R.layout.actionbar_indeterminate_progress);
        }

        return true;
    }


    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                    mBluetoothLeService.disconnect();
                    stopService(new Intent(DeviceControlActivity.this, NotificationService.class));
                    finish();
                    break;

                case DialogInterface.BUTTON_NEGATIVE:

                    break;
            }
        }
    };



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_bt:
                if(!mConnected)
                    mBluetoothLeService.connect(mDeviceAddress);
                else
                {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage("Are you sure you want to disconnect from the device?").setPositiveButton("Yes", dialogClickListener).setNegativeButton("Cancel", dialogClickListener).show();
                }
                return true;
            case android.R.id.home:
                stopService(new Intent(DeviceControlActivity.this, NotificationService.class));
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        stopService(new Intent(DeviceControlActivity.this, NotificationService.class));
        finish();
    }
}