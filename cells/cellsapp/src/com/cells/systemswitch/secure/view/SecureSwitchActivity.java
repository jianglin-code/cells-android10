package com.cells.systemswitch.secure.view;

import android.util.Log;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.os.SystemProperties;
import android.os.ServiceManager;
import java.io.IOException;
import android.os.RemoteException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import android.widget.Toast;

import com.cells.systemswitch.secure.R;

import android.app.CellsPrivateServiceManager;

public class SecureSwitchActivity extends Activity {
	private static final String TAG = "SecureSwitchActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if(!SystemProperties.get("ro.boot.vm","1").equals("0")){
			CellsPrivateServiceManager mCellsService;
			mCellsService = new CellsPrivateServiceManager(SecureSwitchActivity.this, 
																					ServiceManager.getService("CellsPrivateService"));
			try{
				mCellsService.switchCellsVM("host");
			}catch(RemoteException e){
				e.printStackTrace();
			}

			finish();
		}else{
			setContentView(R.layout.activity_main);
		}
	}

	public void btncell1(View v)
	{
		CellsPrivateServiceManager mCellsService;
		mCellsService = new CellsPrivateServiceManager(SecureSwitchActivity.this, 
																					ServiceManager.getService("CellsPrivateService"));

		if(SystemProperties.get("persist.sys.cell1.init").equals("0")){
			long beginTime=System.currentTimeMillis();
			try{
				//mCellsService.untarCellsVM("cell1");
				mCellsService.startCellsVM("cell1");
			}catch(RemoteException e){
				e.printStackTrace();
			}
			long ms = System.currentTimeMillis() - beginTime;
			Log.e(TAG, "启动镜像消耗 - " + ms + "(ms).");

			int count = 25;
			while(SystemProperties.get("persist.sys.cell1.init").equals("0") && count > 0){
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				count--;
			}
		}

		try {
			mCellsService.switchCellsVM("cell1");
		}catch(RemoteException e){
			e.printStackTrace();
		}

		finish();
	}

	public void btncell2(View v)
	{
		CellsPrivateServiceManager mCellsService;
		mCellsService = new CellsPrivateServiceManager(SecureSwitchActivity.this, 
																					ServiceManager.getService("CellsPrivateService"));

		if(SystemProperties.get("persist.sys.cell2.init").equals("0")){
			long beginTime=System.currentTimeMillis();
			try{
				//mCellsService.untarCellsVM("cell2");
				mCellsService.startCellsVM("cell2");
			}catch(RemoteException e){
				e.printStackTrace();
			}
			long ms = System.currentTimeMillis() - beginTime;
			Log.e(TAG, "启动镜像消耗 - " + ms + "(ms).");

			int count = 25;
			while(SystemProperties.get("persist.sys.cell2.init").equals("0") && count > 0){
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				count--;
			}
		}

		try {
			mCellsService.switchCellsVM("cell2");
		}catch(RemoteException e){
			e.printStackTrace();
		}

		finish();
	}
}
