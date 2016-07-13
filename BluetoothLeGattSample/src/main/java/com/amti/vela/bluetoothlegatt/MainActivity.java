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
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
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
import android.widget.CheckBox;
import android.widget.CompoundButton;
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

    public static final String EXTRAS_DEVICE = "DEVICE";
    public static final String ANALOG_OUT_UUID = "866ad1ee-05c4-4f4e-9ef4-548790668ad1";
    public static final String VBAT_UUID = "982754c4-fbde-4d57-a01b-6c81f2f0499e";

    public static final int SEND_COLOR_VALUES = 0;
    public static final int SEND_DEP_VALUE = 1;

    private String mDeviceAddress = "Unknown Address";
    String mDeviceName = "Unknown Device";
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    public BluetoothGattCharacteristic colorCharacteristic;
    public BluetoothGattCharacteristic vbatCharacteristic;

    ProgressDialog connectingDialog;

    private TabLayout tabLayout;
    private ViewPager viewPager;
    private ViewPagerAdapter viewPagerAdapter;
    DeviceFragment deviceFragment;
    ColorPickerFragment colorPickerFragment;
    boolean initColors = true;

    int[] mVbatArray = new int[6];

    AlertDialog.Builder saveAutoConnectDialog;
    boolean mNeverAskChecked;

    boolean mAutoConnecting;
    boolean mNeverAsking;

    DialogInterface.OnClickListener saveAutoConnectListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                    prefs.edit().putBoolean(Preferences.PREFS_AUTO_CONNECT_KEY, true).apply();
                    prefs.edit().putString(Preferences.PREFS_DEVICE_KEY, mDeviceName + "\n" + mDeviceAddress).apply();
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    prefs.edit().putBoolean(Preferences.PREFS_NEVER_ASK_KEY, mNeverAskChecked).apply();
                    break;
            }
        }
    };

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(final Message msg) {
            // TODO Auto-generated method stub
            super.handleMessage(msg);
            switch (msg.what) {
                case SEND_COLOR_VALUES:
                    mBluetoothLeService.writeCharacteristic(colorCharacteristic, colorPickerFragment.getColorString());
                    Log.v(TAG, "Wrote rgb values");
                    break;
                case SEND_DEP_VALUE:
                    sendDEPValue((String)msg.obj);
                    Log.v(TAG, "Wrote" + (String)msg.obj + "value");
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
                mBluetoothLeService.writeCharacteristic(colorCharacteristic,"V" );
                Thread.sleep(500);
                mBluetoothLeService.writeCharacteristic(colorCharacteristic,"v" );
                Thread.sleep(500);
                mBluetoothLeService.writeCharacteristic(colorCharacteristic,"V" );
                Thread.sleep(500);
                mBluetoothLeService.writeCharacteristic(colorCharacteristic,"v" );
                Thread.sleep(500);
                /*
                mBluetoothLeService.writeCharacteristic(colorCharacteristic,"255000000" );
                Thread.sleep(500);
                mBluetoothLeService.writeCharacteristic(colorCharacteristic,"000000000");
                Thread.sleep(500);
                mBluetoothLeService.writeCharacteristic(colorCharacteristic,"255000000");
                Thread.sleep(500);
                mBluetoothLeService.writeCharacteristic(colorCharacteristic,"000000000" );
                Thread.sleep(500);
                */
            } catch (InterruptedException e) {
                Log.d(TAG, "Caught InterruptedException while attempting to start notification listener thread");
            }
        }
    };

    Runnable connectRunnable = new Runnable() {

        @Override
        public void run() {
            Toast.makeText(getApplicationContext(), "Device connect timed out", Toast.LENGTH_SHORT).show();
            stopService(new Intent(MainActivity.this, NotificationService.class));
            finish();
        }
    };

    Runnable readVbatRunnable = new Runnable() {

        @Override
        public void run() {
            vbatCharacteristic = fetchCharacteristic(VBAT_UUID);
            mBluetoothLeService.readCharacteristic(vbatCharacteristic);

            mHandler.postDelayed(readVbatRunnable, 10000);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        overridePendingTransition(R.anim.slidein, R.anim.fadeout);

        final Intent intent = getIntent();
        String deviceString = intent.getStringExtra(EXTRAS_DEVICE);
        if(deviceString.split("\n").length == 2)
        {
            mDeviceName = deviceString.split("\n")[0];
            mDeviceAddress = deviceString.split("\n")[1];
        }

        initGui();

        //get bt le going
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, new IntentFilter("Msg"));

        connectingDialog = new ProgressDialog(this);
        connectingDialog.setMessage("Please wait while connecting to " + mDeviceName + "...");
        connectingDialog.setOnCancelListener(this);
        connectingDialog.setCancelable(true);
        connectingDialog.show();
        mHandler.postDelayed(connectRunnable, 10000);
    }

    BluetoothGattCharacteristic fetchCharacteristic(String uuid)
    {
        BluetoothGattCharacteristic my_characteristic = null;
        List<BluetoothGattService> gattServices = mBluetoothLeService.getSupportedGattServices();
        for (BluetoothGattService service : gattServices) {
            List<BluetoothGattCharacteristic> gattCharacteristics = service.getCharacteristics();
            for (BluetoothGattCharacteristic characteristic : gattCharacteristics) {
                if (characteristic.getUuid().toString().equals(uuid)) {
                    my_characteristic = characteristic;
                }
            }
        }

        return my_characteristic;
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
        actionBarToolBar.setTitleTextColor(ContextCompat.getColor(this, R.color.action_bar_text_gray));

        //status bar color
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.action_bar_dark_blue));
        }

        //set up fragment_device and fragments
        viewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(viewPagerAdapter);

        final TabLayout.Tab color = tabLayout.newTab();
        final TabLayout.Tab device = tabLayout.newTab();
        final TabLayout.Tab dev = tabLayout.newTab();

        View colorView = getLayoutInflater().inflate(R.layout.tab_view, null);
        ImageView iconColor = (ImageView) colorView.findViewById(R.id.imageView);
        iconColor.setImageResource(R.mipmap.ic_color_palette_white);

        View editView = getLayoutInflater().inflate(R.layout.tab_view, null);
        ImageView iconEdit = (ImageView) editView.findViewById(R.id.imageView);
        iconEdit.setImageResource(R.mipmap.ic_battery_std_white);

        View devView = getLayoutInflater().inflate(R.layout.tab_view, null);
        ImageView iconDev = (ImageView) devView.findViewById(R.id.imageView);
        iconDev.setImageResource(R.mipmap.ic_developer_white);

        color.setCustomView(iconColor);
        device.setCustomView(iconEdit);
        dev.setCustomView(iconDev);

        tabLayout.addTab(color, 0);
        tabLayout.addTab(device, 1);
        tabLayout.addTab(dev, 2);

        tabLayout.setSelectedTabIndicatorColor(ContextCompat.getColor(this, R.color.tab_indicator));
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
        deviceFragment = viewPagerAdapter.getDeviceFragment();

        saveAutoConnectDialog = new AlertDialog.Builder(this);
        saveAutoConnectDialog.setMessage("Would you like to auto connect to " + mDeviceName + " from now on?")
                .setPositiveButton("Yes", saveAutoConnectListener).setNegativeButton("No", saveAutoConnectListener).setCancelable(false);
        View checkBoxView = View.inflate(this, R.layout.alert_dialog_checkbox, null);
        CheckBox checkBox = (CheckBox) checkBoxView.findViewById(R.id.checkbox);
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mNeverAskChecked = isChecked;
            }
        });
        checkBox.setText("Never ask again");
        saveAutoConnectDialog.setView(checkBoxView);
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                stopService(new Intent(MainActivity.this, NotificationService.class));
                mHandler.removeCallbacksAndMessages(connectRunnable);
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
                mHandler.removeCallbacks(connectRunnable);
                invalidateOptionsMenu();
                connectingDialog.dismiss();

                deviceFragment.setDevice(mDeviceName, mDeviceAddress);

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                mNeverAsking = prefs.getBoolean(Preferences.PREFS_NEVER_ASK_KEY, false);
                mAutoConnecting = prefs.getBoolean(Preferences.PREFS_AUTO_CONNECT_KEY, false);
                if(!mNeverAsking && !mAutoConnecting)
                    saveAutoConnectDialog.show();
            }

            else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                invalidateOptionsMenu();
                Toast.makeText(getApplicationContext(), "You have lost connection to the device", Toast.LENGTH_SHORT).show();
                if(!connectingDialog.isShowing())
                {
                    connectingDialog.setMessage("Please wait while reconnecting to " + mDeviceName + "...");
                    connectingDialog.setCancelable(false);
                    connectingDialog.show();
                    mHandler.postDelayed(connectRunnable, 10000);
                }
            }

            else if (BluetoothLeService.ACTION_GATT_CONNECTING.equals(action)) {
                mConnected = false;
                invalidateOptionsMenu();
            }

            else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                //read initial rgb color
                colorCharacteristic = fetchCharacteristic(ANALOG_OUT_UUID);
                mBluetoothLeService.readCharacteristic(colorCharacteristic);

                //kick off reading battery
                mHandler.postDelayed(readVbatRunnable, 10000);
            }

            else if(BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action))
            {
                String rxString = intent.getStringExtra(BluetoothLeService.EXTRA_DATA).split("\n")[0];
                if(rxString.isEmpty())
                    return;
                if(rxString.length() == 9 && initColors)    //we're reading rgb color values
                    processColorData(rxString);
                else if (rxString.length() < 9)             //we're reading vbat
                    processVbatData(rxString);
            }
        }
    };

    void processColorData(String rxString)
    {
        try {
            String colorString = rxString;
            int r = Integer.parseInt(colorString.substring(0, 3));
            int g = Integer.parseInt(colorString.substring(3, 6));
            int b = Integer.parseInt(colorString.substring(6, 9));
            int colorValue = b;
            colorValue += (g << 8);
            colorValue += (r << 16);
            colorValue &= 0xFF;
            colorPickerFragment.initColors(colorValue);

            initColors = false;
        } catch (NumberFormatException e) {
            Log.d(TAG, "Caught NumberFormatException while parsing rgb values");

        }
        //read first vbat value here so we don't need to wait 10 seconds
        vbatCharacteristic = fetchCharacteristic(VBAT_UUID);
        mBluetoothLeService.readCharacteristic(vbatCharacteristic);
    }

    void processVbatData(String rxString)
    {
        String vbatString = rxString;
        //shift elements in array to right
        for (int i = (mVbatArray.length - 1); i > 0; i--) {
            mVbatArray[i] = mVbatArray[i-1];
        }
        //parse vbat
        char vbatChar = vbatString.charAt(0);
        mVbatArray[0] = (int)vbatChar;
        int mAvgVbat = 0;
        //calculate avg
        int divideBy = 0;
        for(int i = 0; i < mVbatArray.length - 1; i++)
        {
            if(!(mVbatArray[0] > mVbatArray[i] + 5) && !(mVbatArray[0] < mVbatArray[i]))
            {
                mAvgVbat += mVbatArray[i];
                divideBy++;
            }
        }
        mAvgVbat /= divideBy;

        deviceFragment.updateBattery(mAvgVbat);
    }


    void sendDEPValue(String val)
    {
        mBluetoothLeService.writeCharacteristic(colorCharacteristic, val);
    }


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

                            colorCharacteristic = characteristic;
                            Log.w(TAG, colorCharacteristic.toString());
                            //mBluetoothLeService.writeCharacteristic(colorCharacteristic, newValueText);
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

        overridePendingTransition(R.anim.fadein, R.anim.fade_and_scale_out);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
        mHandler.removeCallbacks(readVbatRunnable);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_activity_main, menu);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            menu.findItem(R.id.menu_bt).getIcon().setTint(ContextCompat.getColor(this, R.color.action_button_dark_blue));
        }
        if (mConnected) {
            menu.findItem(R.id.menu_bt).setTitle("Disconnect");
            menu.findItem(R.id.menu_bt).setIcon(R.mipmap.ic_bluetooth_connected_white);
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
                    mHandler.removeCallbacksAndMessages(connectRunnable);
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
            case R.id.menu_settings:
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
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
        mHandler.removeCallbacksAndMessages(connectRunnable);
        finish();
    }

}