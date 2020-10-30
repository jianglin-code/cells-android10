package com.cells.cellswitch.secure.view;

import android.content.Intent;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.os.RemoteException;
import android.content.Context;
import android.content.IntentFilter;
import android.widget.Toast;
import android.widget.Button;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.IBinder;
import android.util.Log;

import android.app.CellsPrivateServiceManager;

import java.util.Collection;
import java.util.ArrayList;
import java.io.File;

import android.os.SystemProperties;

import com.cells.cellswitch.secure.wifip2p.Wifip2pReceiver;
import com.cells.cellswitch.secure.wifip2p.Wifip2pActionListener;
import com.cells.cellswitch.secure.wifip2p.ReceiveSocket;
import com.cells.cellswitch.secure.wifip2p.Wifip2pService;
import com.cells.cellswitch.secure.R;

public class BaseActivity extends Activity implements Wifip2pActionListener {

    private static final String TAG = "BaseActivity";

    public WifiP2pManager mWifiP2pManager;
    public WifiP2pManager.Channel mChannel;
    public Wifip2pReceiver mWifip2pReceiver;
    public WifiP2pInfo mWifiP2pInfo;

    private AlertDialog mSearchDeviceDialog;
    private WifiP2pDevice mMyWifiP2pDevice;
    private Intent mReceiveIntent;

    public CellsPrivateServiceManager mCellsService;

    private void stopReceive() {
        unbindService(mReceiveServiceConnection);

        stopServiceAsUser(mReceiveIntent,UserHandle.CURRENT);
    }

    private void startReceive(){
        startServiceAsUser(mReceiveIntent,UserHandle.CURRENT);

        bindServiceAsUser(mReceiveIntent, mReceiveServiceConnection, Context.BIND_AUTO_CREATE,UserHandle.CURRENT);
    }

    private ServiceConnection mReceiveServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //调用服务里面的方法进行绑定
            Wifip2pService.MyBinder receiveBinder;
            receiveBinder = (Wifip2pService.MyBinder) service;
            receiveBinder.init(BaseActivity.this);
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            //服务断开重新绑定
            bindService(mReceiveIntent, mReceiveServiceConnection, Context.BIND_AUTO_CREATE);
        }
    };

    private void initHost(){
        //注册WifiP2pManager
        mWifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mWifiP2pManager.initialize(this, getMainLooper(), this);

        //注册广播
        mWifip2pReceiver = new Wifip2pReceiver(mWifiP2pManager, mChannel, this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        registerReceiver(mWifip2pReceiver, intentFilter);

        connectServer();

        createGroup();

        if(SystemProperties.get("ro.boot.vm").equals("1"))
            return;

        mReceiveIntent = new Intent(BaseActivity.this, Wifip2pService.class);

        startReceive();
    }

    private void freeHost(){
        if(mWifiP2pManager == null)
            return;

        removeGroup();

        mWifiP2pManager.stopPeerDiscovery(mChannel, null);

        unregisterReceiver(mWifip2pReceiver);
        mWifip2pReceiver = null;

        mWifiP2pManager = null;

        if(SystemProperties.get("ro.boot.vm").equals("1"))
            return;

        stopReceive();
    }

    public void exit(){
        freeHost();

        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initHost();
    }

    @Override
    protected void onDestroy() {
        freeHost();

        super.onDestroy();
    }

    private void connectServer() {

        mSearchDeviceDialog = new AlertDialog.Builder(this, R.style.SearchDeviceTransparent).create();
        mSearchDeviceDialog.show();
        mSearchDeviceDialog.setCancelable(false);
        mSearchDeviceDialog.setContentView(R.layout.loading_progressba);

        mWifiP2pManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.e(TAG, "搜索设备成功");
                mSearchDeviceDialog.dismiss();
            }

            @Override
            public void onFailure(int reasonCode) {
                Log.e(TAG, "搜索设备失败");
                mSearchDeviceDialog.dismiss();
            }
        });
    }

    @Override
    public void wifiP2pEnabled(boolean enabled) {
        Log.e(TAG, "传输通道是否可用：" + enabled);
    }

    @Override
    public void onConnection(WifiP2pInfo wifiP2pInfo) {
        Log.e(TAG, "WifiP2pInfo:" + wifiP2pInfo);
        if (wifiP2pInfo != null  && !wifiP2pInfo.isGroupOwner) {
            mWifiP2pInfo = wifiP2pInfo;
            Button btn_vm = (Button) BaseActivity.this.findViewById(R.id.btn_vm);
            btn_vm.setTextColor(BaseActivity.this.getResources().getColor(R.color.green));
            //Toast.makeText(BaseActivity.this, "连接成功", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDisconnection() {
        Log.e(TAG, "连接断开");

        Button btn_vm = (Button) BaseActivity.this.findViewById(R.id.btn_vm);
        btn_vm.setTextColor(BaseActivity.this.getResources().getColor(R.color.white));

        mWifiP2pInfo = null;
    }

    @Override
    public void onDeviceInfo(WifiP2pDevice wifiP2pDevice) {
        mMyWifiP2pDevice = wifiP2pDevice;
        Log.e(TAG, "当前的的设备名称" + wifiP2pDevice.deviceName);
    }

    private ArrayList<WifiP2pDevice> mListDevice = new ArrayList<>();
    private ArrayList<String> mListDeviceName = new ArrayList();
    @Override
    public void onPeersInfo(Collection<WifiP2pDevice> wifiP2pDeviceList) {
        for (WifiP2pDevice device : wifiP2pDeviceList) {

            Log.e(TAG, "onPeersInfo：" + device.deviceName + "--------" + device.deviceAddress);

            if(mMyWifiP2pDevice == null)
                continue;

            if(mMyWifiP2pDevice == device || mMyWifiP2pDevice.deviceName == device.deviceName)
                continue;

            if (device.deviceName.startsWith("Android_cells") &&!mListDeviceName.contains(device.deviceName) && !mListDevice.contains(device)) {
                Log.e(TAG, "连接的设备信息：" + device.deviceName + "--------" + device.deviceAddress);
                mListDeviceName.add("设备：" + device.deviceName + "----" + device.deviceAddress);
                mListDevice.add(device);
            }
        }
    }

    @Override
    public void onChannelDisconnected() {

    }

    private void createGroup() {
        mWifiP2pManager.createGroup(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.e(TAG, "创建群组成功");
                //Toast.makeText(BaseActivity.this, "创建群组成功", Toast.LENGTH_SHORT).show();
                /*Button btn_group_vm = (Button) BaseActivity.this.findViewById(R.id.btn_group_vm);
                btn_group_vm.setTextColor(BaseActivity.this.getResources().getColor(R.color.green));*/
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "创建群组失败: " + reason);
                //Toast.makeText(BaseActivity.this, "创建群组失败,请移除已有的组群或者连接同一WIFI重试", Toast.LENGTH_SHORT).show();
                /*Button btn_group_vm = (Button) BaseActivity.this.findViewById(R.id.btn_group_vm);
                btn_group_vm.setTextColor(BaseActivity.this.getResources().getColor(R.color.white));*/
            }
        });
    }

    private void removeGroup() {
        mWifiP2pManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.e(TAG, "移除组群成功");
                //Toast.makeText(BaseActivity.this, "移除组群成功", Toast.LENGTH_SHORT).show();
                /*Button btn_group_vm = (Button) BaseActivity.this.findViewById(R.id.btn_group_vm);
                btn_group_vm.setTextColor(BaseActivity.this.getResources().getColor(R.color.white));*/
            }
            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "移除组群失败");
                //Toast.makeText(BaseActivity.this, "移除组群失败,请创建组群重试", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void connect(WifiP2pDevice wifiP2pDevice) {
        WifiP2pConfig config = new WifiP2pConfig();
        if (wifiP2pDevice != null) {
            config.deviceAddress = wifiP2pDevice.deviceAddress;
            config.wps.setup = WpsInfo.PBC;
            mWifiP2pManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.e(TAG, "连接成功");
                }

                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "连接失败");
                    //Toast.makeText(BaseActivity.this, "连接失败", Toast.LENGTH_SHORT).show();
                    Button btn_vm = (Button) BaseActivity.this.findViewById(R.id.btn_vm);
                    btn_vm.setTextColor(BaseActivity.this.getResources().getColor(R.color.white));
                }
            });
        }
    }

    public void btnConnect(View v){
        removeGroup();

        connectServer();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if(mListDevice.size() > 0){
                    connect(mListDevice.get(0));
                    mListDevice.clear();
                    mListDeviceName.clear();
                }
            }
        }).start();
    }

    public void btnGroup(View v){
        removeGroup();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        createGroup();
    }
}
