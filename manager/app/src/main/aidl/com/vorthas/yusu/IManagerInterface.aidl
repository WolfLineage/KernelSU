// IManagerInterface.aidl
package com.vorthas.yusu;

import android.content.pm.PackageInfo;
import rikka.parcelablelist.ParcelableListSlice;

interface IManagerInterface {
    ParcelableListSlice<PackageInfo> getPackages(int flags);

    int[] getUserIds();
}