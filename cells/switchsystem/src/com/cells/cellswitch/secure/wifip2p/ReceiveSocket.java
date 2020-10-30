package com.cells.cellswitch.secure.wifip2p;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import com.cells.cellswitch.secure.wifip2p.FileBean;
import com.cells.cellswitch.secure.wifip2p.FileUtils;
import com.cells.cellswitch.secure.wifip2p.Md5Util;

/**
 * description:服务端监听的socket
 */

public class ReceiveSocket {

    public static final String TAG = "ReceiveSocket";
    public static final int PORT = 10000;
    private ServerSocket mServerSocket = null;
    private Socket mAcceptSocket = null;
    private InputStream mInputStream = null;
    private ObjectInputStream mObjectInputStream = null;
    private FileOutputStream mFileOutputStream = null;
    private File mFile = null;

    private Handler mHandler = new Handler(Looper.getMainLooper()) {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 40:
                    if (mListener != null) {
                        mListener.onReceive();
                    }
                    break;
                case 50:
                    int progress = (int) msg.obj;
                    if (mListener != null) {
                        mListener.onProgressChanged(mFile, progress);
                    }
                    break;
                case 60:
                    if (mListener != null) {
                        mListener.onFinished(mFile);
                    }
                    break;
                case 70:
                    if (mListener != null) {
                        mListener.onFaliure(mFile);
                    }
                    break;
            }
        }
    };

    public void createServerSocket() {
        try {
            mServerSocket = new ServerSocket();
            mServerSocket.setReuseAddress(true);
            mServerSocket.bind(new InetSocketAddress(PORT));

            mAcceptSocket = mServerSocket.accept();

            Log.e(TAG, "客户端IP地址 : " + mAcceptSocket.getRemoteSocketAddress());

            long beginTime=System.currentTimeMillis();

            mInputStream = mAcceptSocket.getInputStream();

            byte blen[] = new byte[4];
            if(mInputStream.read(blen, 0, 4)!= 4){
                mHandler.sendEmptyMessage(70);
                clear();
                Log.e(TAG, "文件头接收异常.");
                return;
            }
            int fileLength = ((blen[3] << 24)&0x0FFFFFFFF) |
                                   ((blen[2] << 16)&0x0FFFFFF) |
                                   ((blen[1] << 8)&0x0FFFF) |
                                   (blen[0]&0x0FF);

            mFile = new File(FileUtils.CellsPath("hwcell.img"));
            if (mFile.exists()) {
                mFile.delete();
                Log.e(TAG, "客户端已有这个文件,删除.");
            }

            mFileOutputStream = new FileOutputStream(mFile);

            mHandler.sendEmptyMessage(40);

            byte bytes[] = new byte[4096];
            int len = 0;
            long total = 0;
            while ((len = mInputStream.read(bytes)) != -1) {
                mFileOutputStream.write(bytes, 0, len);

                total += len;

                Message message = Message.obtain();
                message.what = 50;
                message.obj = (int)((total * 100) / fileLength);
                mHandler.sendMessage(message);
            }

            long ms = System.currentTimeMillis() - beginTime;
            Log.e(TAG, "接收文件消耗 - " + ms + "(ms).");

            if(fileLength == total){
                mHandler.sendEmptyMessage(60);
                Log.e(TAG, "文件接收成功. ");
            }else{
                mHandler.sendEmptyMessage(70);
            }

            clear();
        } catch (Exception e) {
            mHandler.sendEmptyMessage(70);

            clear();
            Log.e(TAG, "文件接收异常.");
        }
    }

    /**
     * 监听接收进度
     */
    private ProgressReceiveListener mListener;

    public void setOnProgressReceiveListener(ProgressReceiveListener listener) {
        mListener = listener;
    }

    public interface ProgressReceiveListener {

        //开始传输
        void onReceive();

        //当传输进度发生变化时
        void onProgressChanged(File file, int progress);

        //当传输结束时
        void onFinished(File file);

        //传输失败回调
        void onFaliure(File file);
    }

    /**
     * 服务断开：释放内存
     */
    public void clear() {
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
                mServerSocket = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (mObjectInputStream != null) {
            try {
                mObjectInputStream.close();
                mObjectInputStream = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (mInputStream != null) {
            try {
                mInputStream.close();
                mInputStream = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (mFileOutputStream != null) {
            try {
                mFileOutputStream.close();
                mFileOutputStream = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (mAcceptSocket != null) {
            try {
                mAcceptSocket.close();
                mAcceptSocket = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
