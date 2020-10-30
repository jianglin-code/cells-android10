package com.cells.cellswitch.secure.download;

import java.io.File;

public interface DownloadCallback {
    /**
     * 下载成功
     *
     * @param file
     */
    void onSuccess(File file);

    /**
     * 下载失败
     *
     * @param e
     */
    void onFailure(Exception e);

    /**
     * 下载进度
     *
     * @param progress
     */
    void onProgress(long progress, long currentLength);

    /**
     * 暂停
     *
     * @param progress
     * @param currentLength
     */
    void onPause(long progress, long currentLength);
}
