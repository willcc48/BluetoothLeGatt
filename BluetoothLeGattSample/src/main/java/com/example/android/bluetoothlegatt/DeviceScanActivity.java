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

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends Activity {
    private final static String TAG = DeviceScanActivity.class.getSimpleName();

    private BluetoothAdapter mBluetoothAdapter;
    BluetoothLeScanner mScanner;
    private boolean mScanning;
    private Handler mHandler;

    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    private ArrayList<BluetoothDevice> mLeDevicesList;
    private CustomListAdapter devicesAdapter;
    ListView deviceListView;
    SwipeRefreshLayout swipeContainer;

    boolean disableActionButton = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.listitem_device);
        mHandler = new Handler();

        swipeContainer = (SwipeRefreshLayout) findViewById(R.id.swipeContainer);
        mLeDevicesList = new ArrayList<BluetoothDevice>();
        devicesAdapter = new CustomListAdapter(this, R.layout.custom_list_item);



        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        ActionBar bar = getActionBar();
        bar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.light_blue)));
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(getResources().getColor(R.color.dark_blue));

        deviceListView = (ListView)findViewById(R.id.listView);
        deviceListView.setAdapter(devicesAdapter);
        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> adapter, View v, int position,
                                    long arg3)
            {
                BluetoothDevice device = mLeDevicesList.get(position);
                if (device == null) return;
                final Intent intent = new Intent(DeviceScanActivity.this, DeviceControlActivity.class);
                intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
                if (mScanning) {
                    scanLeDevice(false);
                    mScanning = false;
                }
                startActivity(intent);
            }
        });

        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // Your code to refresh the list here.
                // Make sure you call swipeContainer.setRefreshing(false)
                // once the bt request has completed successfully
                mLeDevicesList.clear();
                devicesAdapter.clear();
                devicesAdapter.notifyDataSetChanged();
                scanLeDevice(true);
            }
        });

        // Configure the refreshing colors
        swipeContainer.setColorSchemeResources(R.color.light_blue,
                R.color.green,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);



        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        mScanner = mBluetoothAdapter.getBluetoothLeScanner();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if(!NotificationService.notificationsBound)
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("This app wants to enable notification access in the settings.").setPositiveButton("OK", dialogClickListener).setNegativeButton("Cancel", dialogClickListener).show();
        }
/*
        if(Build.VERSION.SDK_INT >= 23)
        {
            if (ContextCompat.checkSelfPermission(DeviceScanActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(DeviceScanActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            {
                ActivityCompat.requestPermissions(DeviceScanActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);

            }
            else
            {
                scanLeDevice(true);
                new Handler().postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        swipeContainer.setRefreshing(true);
                    }
                }, 500);
            }
        }
        else
        {
            scanLeDevice(true);
            new Handler().postDelayed(new Runnable() {

                @Override
                public void run() {
                    swipeContainer.setRefreshing(true);
                }
            }, 500);
        }*/

        SwipeRefreshHintLayout swipeHint = (SwipeRefreshHintLayout) findViewById(R.id.swipe_hint);
        swipeHint.setSwipeLayoutTarget(swipeContainer);

        swipeContainer.post(new Runnable() {
            @Override
            public void run() {
                swipeContainer.setRefreshing(true);
            }
        });
        scanLeDevice(true);
    }


    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                    startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    Toast.makeText(DeviceScanActivity.this, "Some parts of this app will be disabled", Toast.LENGTH_LONG).show();
                    break;
            }
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if(!mScanning)
        {
            swipeContainer.setRefreshing(false);
        }
        if(disableActionButton)
            menu.findItem(R.id.menu_search).setEnabled(false);
        else
            menu.findItem(R.id.menu_search).setEnabled(true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_search:
                mLeDevicesList.clear();
                devicesAdapter.clear();
                scanLeDevice(true);
                return true;
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
        //scanLeDevice(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        mLeDevicesList.clear();
        devicesAdapter.clear();
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    disableActionButton = false;
                    swipeContainer.post(new Runnable() {
                        @Override
                        public void run() {
                            swipeContainer.setRefreshing(false);
                        }
                    });
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    //mScanner.stopScan(scanCallback);
                    Log.e(TAG, "Stopped scan");
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            disableActionButton = true;
            swipeContainer.post(new Runnable() {
                @Override
                public void run() {
                    swipeContainer.setRefreshing(true);
                }
            });
            mBluetoothAdapter.startLeScan(mLeScanCallback);
            //mScanner.startScan(scanCallback);
            Log.e(TAG, "Started scan");

        } else {
            mScanning = false;
            disableActionButton = false;
            swipeContainer.post(new Runnable() {
                @Override
                public void run() {
                    swipeContainer.setRefreshing(false);
                }
            });
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            //mScanner.stopScan(scanCallback);
            Log.e(TAG, "Stopped scan");
        }
        invalidateOptionsMenu();
    }



    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
        new BluetoothAdapter.LeScanCallback() {

            @Override
            public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        boolean exists = false;
                        for (BluetoothDevice btDevice : mLeDevicesList)
                        {
                            if(device.getAddress().equals(btDevice.getAddress()))
                            {
                                exists = true;
                                break;
                            }
                        }
                        if(!exists)
                        {
                            mLeDevicesList.add(device);
                            if(device.getName() != null)
                                devicesAdapter.add(new ClipData.Item(device.getName()+"\n"+device.getAddress()));
                            else
                                devicesAdapter.add(new ClipData.Item("Unknown Device\n"+device.getAddress()));
                            devicesAdapter.notifyDataSetChanged();
                        }
                    }

                });

            }

        };

}