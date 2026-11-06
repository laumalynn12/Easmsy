package com.easmsy.shuttle;

import android.content.ComponentName;
import com.easmsy.shuttle.IUnbinder;

interface IServiceConnection {
    oneway void onServiceConnected(in ComponentName name, in IBinder service, in IUnbinder unbinder);
    oneway void onServiceDisconnected(in ComponentName name);
    oneway void onServiceFailed();
}
