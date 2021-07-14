package com.espressif.esptouch.android;

public class GlobalInitialize{
    public static final String DISPLAY_TITLE = "选择需要显示的日志文件";
    public static final String UPLOAD_TITLE = "选择需要上传的日志文件";
    public static final String FILE_PATH_SUFFIX = "/biz.mobile.mdm/log/crash/";
    public static final int DISPLAY_TYPE = 1;
    public static final int UPLOAD_TYPE = 2;
    public static final String TAG = "CrashHandler";
    public static final String DATE_FORMAT = "yyyy-MM-dd-HH:mm:ss";
    public static final String ERR_INFOR = "很抱歉,程序出现异常,即将退出.";
    public static final Long SLEEP_TIME = (long)3000;
    public static final String VERSION_NAME = "versionName";
    public static final String VERSION_CODE = "versionCode";
    public static final String COLLECT_ERR = "an error occured when collect package info";
    public static final String BLOCK_TAG = "====================================\n";
    public static final String DEVICE_INFO = "Device-Information\n";
    public static final String EXCEPTION_INFO = "Exception-Information\n";
    public static final String END_TAG = "END\n";
    public static final String WRITE_ERR = "an error occured while writing file...";
    public static final String FILE_NAME_PREFIX = "-log-";
    public static final String FILE_NAME_SUFFIX = ".txt";
    public static final String IMEI_NUM = "imei:";
    public static final String NULL_TAG = "null";
    public static final String THREAD_SLEEP_ERR = "error : thread sleep failed ";
    public static final int MAX_LOG_FILE_COUNT = 20;
    public static final String CONFIG_FILE_NAME = "/biz.mobile.mdm/log/crash/crash.config";
    public static final String CONFIG_FILE_SUFFIX = "config";
}
