package com.uestc.hb.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import com.uestc.hb.R;
import com.uestc.hb.common.BluetoothConst;
import com.uestc.hb.ui.PairActivity;
import com.uestc.hb.utils.NotifyUtil;
import com.uestc.hb.utils.ToolUtil;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class BluetoothService extends Service {
	private static final String TAG = BluetoothService.class.getName();
	private static final String SERVICE = "BTService";

	private BluetoothAdapter mBluetoothAdapter;
	private ConnectThread mConnectThread;
	private ConnectedThread mConnectedThread;

	private static Handler mHandler = null;

	private final IBinder mBinder = new LocalBinder();

	private BroadcastReceiver serviceReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			switch (action) {
			case BluetoothConst.ACTION_SERVICE_CANCEL_CONNECT:
				trace("断开连接");
				stop();
				break;
			case BluetoothDevice.ACTION_FOUND:
				BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				Log.i(TAG, "deviceName--" + device.getName());
				if (BluetoothConst.MAC_ADDRESS.equals(device.getAddress())) {
					trace("连接设备");
					connectToDevice(device);
				}
				break;
			case BluetoothAdapter.ACTION_STATE_CHANGED:
				int state = intent
						.getIntExtra(BluetoothAdapter.EXTRA_STATE,-1);
				if (BluetoothAdapter.STATE_TURNING_OFF == state) {
					Intent i = new Intent(BluetoothService.this, PairActivity.class);
					i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					NotifyUtil.toNotify(BluetoothService.this, R.drawable.ic_girl,
							"蓝牙被改变", 1, i, "蓝牙被关闭，HeartBeat无法正常工作");
					stop();
				}
				break;
			case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
				trace("查找结束");
				break;
			default:
				trace("异常");
				break;
			}
		}
	};

	public synchronized void connectToDevice(BluetoothDevice device) {
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}
		mConnectThread = new ConnectThread(device);
		mConnectThread.start();
	}

	public synchronized void stop() {
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}

		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}
		if (mBluetoothAdapter != null) {
			mBluetoothAdapter.cancelDiscovery();
		}
		stopSelf();
	}

	private synchronized void manageConnectedSocket(BluetoothSocket mmSocket) {
		trace("设备已连接");
		mConnectedThread = new ConnectedThread(mmSocket);
		mConnectedThread.start();
		sendBroadcast(BluetoothConst.ACTION_PAIR_CONNECTED);
	}

	private class ConnectThread extends Thread {
		private final BluetoothSocket mmSocket;
		public ConnectThread(BluetoothDevice device) {
			BluetoothSocket tmp = null;
			try {
				tmp = device
						.createRfcommSocketToServiceRecord(BluetoothConst.MY_UUID);
			} catch (IOException e) {
				e.printStackTrace();
			}
			mmSocket = tmp;
		}

		@Override
		public void run() {
			Log.i(TAG, "ConnectThread run");
			mBluetoothAdapter.cancelDiscovery();
			trace("mmSocket--" + mmSocket);
			try {
				mmSocket.connect();
			} catch (IOException e) {
				trace("异常--" + e.toString());
				sendBroadcast(BluetoothConst.ACTION_PAIR_NOT_FOUND);
				try {
					mmSocket.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				return;

			}
			manageConnectedSocket(mmSocket);
		}

		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private class ConnectedThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		public ConnectedThread(BluetoothSocket socket) {
			mmSocket = socket;
			InputStream tmpIn = null;
			// Get the input and output streams, using temp objects because
			// member streams are final
			try {
				tmpIn = socket.getInputStream();
			} catch (IOException e) {
			}

			mmInStream = tmpIn;
		}

		public void run() {
			trace("ConnectedThread run");
			byte[] buffer = new byte[1024]; // buffer store for the stream
			// Keep listening to the InputStream until an exception occurs
			while (true) {
				try {
					mmInStream.read(buffer);
					float data = ToolUtil.getFloat(buffer);
					sendDataMessage(data);
				} catch (IOException e) {
					sendStateMessage(BluetoothConst.MESSAGE_CONNECTED_ERROR);
					break;
				}
			}
		}

		/* Call this from the main activity to shutdown the connection */
		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
			}
		}
	}

	private void registReceiver() {
		IntentFilter filter = new IntentFilter();
		// 取消连接
		filter.addAction(BluetoothConst.ACTION_SERVICE_CANCEL_CONNECT);
		// 系统状态
		filter.addAction(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		trace("注册广播");
		registerReceiver(serviceReceiver, filter);
	}

	private void sendBroadcast(String action) {
		Intent intent = new Intent();
		intent.setAction(action);
		sendBroadcast(intent);
	}

	private void sendDataMessage(float data) {
		if (mHandler != null) {
			mHandler.obtainMessage(BluetoothConst.MESSAGE_DATA, data)
					.sendToTarget();
		} else {
			trace("handler为空");
		}
	}

	private void sendStateMessage(int what) {
		if (mHandler != null) {
			mHandler.obtainMessage(what).sendToTarget();
		} else {
			trace("handler为空");
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Log.i(SERVICE, "Service started");
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		registReceiver();
	}

	@Override
	public IBinder onBind(Intent intent) {
		trace("onBind");
		sendStateMessage(BluetoothConst.MESSAGE_BIND_SUCCESS);
		return mBinder;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		trace("开始discovery");
		mBluetoothAdapter.startDiscovery();
		Set<BluetoothDevice> pairedDevices = mBluetoothAdapter
				.getBondedDevices();
		// If there are paired devices
		if (pairedDevices.size() > 0) {
			// Loop through paired devices
			for (BluetoothDevice device : pairedDevices) {
				if (BluetoothConst.MAC_ADDRESS.equals(device.getAddress())) {
					trace("连接设备");
					connectToDevice(device);
				}
			}
		}
		return START_STICKY;
	}

	@Override
	public boolean stopService(Intent name) {
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}

		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}
		mBluetoothAdapter.cancelDiscovery();
		return super.stopService(name);
	}

	@Override
	public void onDestroy() {
		Log.d(SERVICE, "Destroyed");
		super.onDestroy();
		unregisterReceiver(serviceReceiver);
	}

	public class LocalBinder extends Binder {
		public BluetoothService getService() {
			return BluetoothService.this;
		}

		public void setHandler(Handler handler) {
			trace("setHandler");
			BluetoothService.mHandler = handler;
		}
	}

	// 用来追踪状态信息
	public void trace(String msg) {
		Log.i(TAG, msg);
	}
}
