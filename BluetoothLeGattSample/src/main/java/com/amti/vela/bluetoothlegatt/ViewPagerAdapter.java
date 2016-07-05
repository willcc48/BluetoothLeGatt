package com.amti.vela.bluetoothlegatt;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

public class ViewPagerAdapter extends FragmentStatePagerAdapter {

    ColorPickerFragment colorPickerFragment;
    DeviceFragment deviceFragment;

    public ViewPagerAdapter(FragmentManager fm) {
        super(fm);
        colorPickerFragment = new ColorPickerFragment();
        deviceFragment = new DeviceFragment();
    }

    @Override
    public Fragment getItem(int position) {
        switch (position)
        {
            case 0:
                return colorPickerFragment;
            case 1:
                return deviceFragment;
        }
        return new DeviceFragment();
        // Which Fragment should be displayed by the viewpager for the given position
        // In my case we are showing up only one fragment in all the three fragment_device so we are
        // not worrying about the position and just returning the DeviceFragment
    }

    public ColorPickerFragment getColorPickerFragment()
    {
        return colorPickerFragment;
    }

    public DeviceFragment getDeviceFragment()
    {
        return deviceFragment;
    }

    @Override
    public int getCount() {
        return 2;           // As there are only 2 Tabs
    }

    @Override
    public CharSequence getPageTitle(int position) {
        switch (position){
            case 0:
                return "Choose a Color";
            case 1:
                return "Device";
        }
        return "Default Text";
    }
}