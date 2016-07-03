package com.amti.vela.bluetoothlegatt;

import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

public class ViewPagerAdapter extends FragmentStatePagerAdapter {

    ColorPickerFragment colorPickerFragment;
    TabFragment tabFragment;

    public ViewPagerAdapter(FragmentManager fm) {
        super(fm);
        colorPickerFragment = new ColorPickerFragment();
        tabFragment = new TabFragment();
    }

    @Override
    public Fragment getItem(int position) {
        switch (position)
        {
            case 0:
                return colorPickerFragment;
            case 1:
                return tabFragment;
        }
        return new TabFragment();
        // Which Fragment should be displayed by the viewpager for the given position
        // In my case we are showing up only one fragment in all the three fragment_example so we are
        // not worrying about the position and just returning the TabFragment
    }

    public ColorPickerFragment getColorPickerFragment()
    {
        return colorPickerFragment;
    }

    public TabFragment getTabFragment()
    {
        return tabFragment;
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
                return "Fragment";
        }
        return "Default Text";
    }
}