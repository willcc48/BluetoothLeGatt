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

package com.amti.vela.bluetoothlegatt;

import android.app.AlertDialog;
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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.design.widget.TabLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import com.amti.vela.bluetoothlegatt.bluetooth.BluetoothLeService;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class MainActivity extends AppCompatActivity implements DialogInterface.OnCancelListener {
    private final static String TAG = MainActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String ANALOG_ANALOG_OUT_UUID = "866ad1ee-05c4-4f4e-9ef4-548790668ad1";

    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    public BluetoothGattCharacteristic myCharacteristic;

    ProgressDialog connectingDialog;

    private TabLayout tabLayout;
    private ViewPager viewPager;
    private ViewPagerAdapter viewPagerAdapter;

    ColorPickerFragment colorPickerFragment;

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(final Message msg) {
            // TODO Auto-generated method stub
            super.handleMessage(msg);
            switch (msg.what) {
                case ColorPickerFragment.SEND_COLOR_VALUES:
                    mBluetoothLeService.writeCharacteristic(myCharacteristic, colorPickerFragment.getColorString());
                    Log.v(TAG, "Wrote rgb values");
                    break;
            }
        }
    };

    public Handler getBtHandler()
    {
        return mHandler;
    }

    private BroadcastReceiver onNotice = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (mConnected)
            {
                try{
                    if(!notificationThread.isAlive())
                        notificationThread.start();
                } catch (IllegalThreadStateException e) {};
            }
        }
    };

    Thread notificationThread = new Thread() {
        @Override
        public void run() {
            try
            {
                mBluetoothLeService.writeCharacteristic(myCharacteristic,"V" );
                Thread.sleep(500);
                mBluetoothLeService.writeCharacteristic(myCharacteristic,"v" );
                Thread.sleep(500);
                mBluetoothLeService.writeCharacteristic(myCharacteristic,"V" );
                Thread.sleep(500);
                mBluetoothLeService.writeCharacteristic(myCharacteristic,"v" );
                Thread.sleep(500);
                /*
                mBluetoothLeService.writeCharacteristic(myCharacteristic,"255000000" );
                Thread.sleep(500);
                mBluetoothLeService.writeCharacteristic(myCharacteristic,"000000000");
                Thread.sleep(500);
                mBluetoothLeService.writeCharacteristic(myCharacteristic,"255000000");
                Thread.sleep(500);
                mBluetoothLeService.writeCharacteristic(myCharacteristic,"000000000" );
                Thread.sleep(500);
                */
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };


    Handler reconnectHandler = new Handler();
    Runnable reconnectRunnable = new Runnable() {

        @Override
        public void run() {
            Toast.makeText(getApplicationContext(), "Device reconnect timed out", Toast.LENGTH_SHORT).show();
            finish();
        }
     };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initGui();

        final Intent intent = getIntent();
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        //get bt le going
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, new IntentFilter("Msg"));

        connectingDialog = new ProgressDialog(this);
        connectingDialog.setMessage("Please wait while connecting to the device...");
        connectingDialog.setOnCancelListener(this);
        connectingDialog.setCancelable(true);
        connectingDialog.show();
    }

    void initGui()
    {
        tabLayout = (TabLayout) findViewById(R.id.tabs);
        viewPager = (ViewPager) findViewById(R.id.viewpager);

        //action bar
        final Toolbar actionBarToolBar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(actionBarToolBar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        //status bar color
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(getResources().getColor(R.color.action_bar_dark_blue));
        }

        //set up fragment_example and fragments
        viewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(viewPagerAdapter);

        final TabLayout.Tab color = tabLayout.newTab();
        final TabLayout.Tab frag = tabLayout.newTab();

        View HomeView = getLayoutInflater().inflate(R.layout.custom_view,null);
        ImageView iconHome = (ImageView) HomeView.findViewById(R.id.imageView);
        iconHome.setImageResource(R.drawable.send_button);

        View InboxView = getLayoutInflater().inflate(R.layout.custom_view,null);
        ImageView iconIn = (ImageView) InboxView.findViewById(R.id.imageView);
        iconIn.setImageResource(R.drawable.send_button);

        color.setCustomView(HomeView);
        frag.setCustomView(InboxView);

        tabLayout.addTab(color, 0);
        tabLayout.addTab(frag, 1);

        tabLayout.setSelectedTabIndicatorColor(ContextCompat.getColor(this, R.color.action_button_dark_blue));
        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                actionBarToolBar.setTitle(viewPagerAdapter.getPageTitle(tabLayout.getSelectedTabPosition()));
                viewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

        colorPickerFragment = viewPagerAdapter.getColorPickerFragment();
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
                invalidateOptionsMenu();
                connectingDialog.dismiss();
                //start listening to notifications
                startService(new Intent(MainActivity.this, NotificationService.class));

                //TODO: SET INITIAL COLORS ON CONNECT

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                invalidateOptionsMenu();
                Toast.makeText(getApplicationContext(), "You have lost connection to the device", Toast.LENGTH_SHORT).show();
                if(!connectingDialog.isShowing())
                {
                    connectingDialog.setMessage("Please wait while reconnecting to the device...");
                    connectingDialog.setCancelable(false);
                    connectingDialog.show();
                    reconnectHandler.postDelayed(reconnectRunnable, 10000);
                }

            } else if (BluetoothLeService.ACTION_GATT_CONNECTING.equals(action)) {
                mConnected = false;
                invalidateOptionsMenu();

            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                //manually set the characteristic we will be sending

                List<BluetoothGattService> gattServices = mBluetoothLeService.getSupportedGattServices();
                for (BluetoothGattService service : gattServices) {
                    List<BluetoothGattCharacteristic> gattCharacteristics = service.getCharacteristics();
                    for (BluetoothGattCharacteristic characteristic : gattCharacteristics) {
                        if (characteristic.getUuid().toString().equals(ANALOG_ANALOG_OUT_UUID)) {
                            myCharacteristic = characteristic;
                        }
                    }
                }
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
        getMenuInflater().inflate(R.menu.menu_activity_main, menu);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            menu.findItem(R.id.menu_bt).getIcon().setTint(getResources().getColor(R.color.action_button_dark_blue));
        }
        if (mConnected) {
            menu.findItem(R.id.menu_bt).setTitle("Disconnect");
            menu.findItem(R.id.menu_bt).setIcon(R.mipmap.ic_bluetooth_connected);
            menu.findItem(R.id.menu_bt).setVisible(true);
        } else {
            menu.findItem(R.id.menu_bt).setVisible(false);
        }

        return true;
    }


    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                    mBluetoothLeService.disconnect();
                    stopService(new Intent(MainActivity.this, NotificationService.class));
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
                stopService(new Intent(MainActivity.this, NotificationService.class));
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

    //called when cancelling the reconnecting dialog
    @Override
    public void onCancel(DialogInterface dialog) {
        stopService(new Intent(MainActivity.this, NotificationService.class));
        finish();
    }

}