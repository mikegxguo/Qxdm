package com.mitac.qxdm;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RadioGroup;
import android.text.TextUtils;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import java.io.IOException;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;



public class MainActivity extends Activity implements RadioGroup.OnCheckedChangeListener {
    /** Called when the activity is first created. */
    private static String TAG = "MainActivity";
    private TextView mTextView01;
    private static String TIP = "If you start a new configuration,\nplease stop QXDM tool(/vendor/bin/diag_mdlog)";
    private Context mContext;
    private StorageManager mStorageManager;
    private static String external_sdcard_path = "";
    private static String qxdm_running = "sys.qxdm.running";
    private static String qxdm_function = "persist.sys.qxdm.function";
    private static String gnss_log = "persist.sys.logservice_gps_enabled";
    private int resource_id;
    private String cmd_kill = "kill -9 `ps -e| grep diag_mdlog | awk -F\" \" '{print $2}'`";
    private RadioGroup mRadioGroup;
    private String mMode = "lte";
    private String config_def = "/sdcard/diag_logs/default_logmask.cfg";
    // DNS_MODE -> RadioButton id
    private static final Map<String, Integer> CONFIG_MAP;
    private static final String CONFIG_LTE = "lte";
    private static final String CONFIG_GNSS = "gnss";

    static {
        CONFIG_MAP = new HashMap<>();
        CONFIG_MAP.put(CONFIG_LTE, R.id.config_lte);
        CONFIG_MAP.put(CONFIG_GNSS, R.id.config_gnss);
    }

    public static String getModeFromProperty() {
        String mode = getSystemProperty(qxdm_function, "");
        if (!CONFIG_MAP.containsKey(mode)) {
            mode = CONFIG_LTE;
        }
        return CONFIG_MAP.containsKey(mode) ? mode : CONFIG_LTE;
    }

    public static boolean isQxdmEnabled(){
        return "true".equals(getSystemProperty(qxdm_running, "false"));
    }

    public static boolean isLte(){
        return "lte".equals(getSystemProperty(qxdm_function, ""));
    }

    public static boolean isGnss(){
        return "gnss".equals(getSystemProperty(qxdm_function, ""));
    }

    public static String getSystemProperty(String property, String defaultValue) {
        try {
            Class clazz = Class.forName("android.os.SystemProperties");
            Method getter = clazz.getDeclaredMethod("get", String.class);
            String value = (String) getter.invoke(null, property);
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        } catch (Exception e) {
            Log.d(TAG, "Unable to read system properties");
        }
        return defaultValue;
    }

    public static void setSystemProperty(String property, String value) {
        try {
            Class clazz = Class.forName("android.os.SystemProperties");
            Method setter = clazz.getDeclaredMethod("set", String.class, String.class);
            setter.invoke(null, property, value);
        } catch (Exception e) {
            Log.d(TAG, "Unable to write system properties");
        }
    }

    //This method is only provided by MITAC API Framework(Zico,Gemini).
    public boolean execCmd(final Context context, String cmd) {
        boolean result = false;
        try {
            Class clazzAPi = Class.forName("android.os.MitacApiManager");
            Method method = clazzAPi.getMethod("executeSpecialCommand",String.class, String.class);
            result = (boolean)method.invoke(context.getSystemService(clazzAPi), cmd, "Mitac62842");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static void copyFilesFromRaw(Context context, int id, String fileName, String storagePath) {
        File file = new File(storagePath);
        if (!file.exists()) {
            file.mkdirs();
        }
        InputStream inputStream = context.getResources().openRawResource(id);
        readInputStream(storagePath + File.separator + fileName, inputStream);
    }

    public static void readInputStream(String storagePath, InputStream inputStream) {
        File file = new File(storagePath);
        try {
            if (!file.exists()) {
                FileOutputStream fos = new FileOutputStream(file);
                byte[] buffer = new byte[inputStream.available()];
                int lenght = 0;
                while ((lenght = inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, lenght);
                }
                fos.flush();
                fos.close();
            }
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void GetExternalSDPath() {
        File path;
        mStorageManager = StorageManager.from(mContext);
        StorageVolume[] volumes = mStorageManager.getVolumeList();
        Log.d(TAG, "volume length: "+volumes.length);
        for (int ivolumes = 0; ivolumes < volumes.length; ivolumes++) {
            path = new File(volumes[ivolumes].getPath());
            Log.d(TAG,"Path="+path+", isRemovable="+ volumes[ivolumes].isRemovable()+
                ", getDescription="+volumes[ivolumes].getDescription(mContext)+", isEmulated="+
                volumes[ivolumes].isEmulated()+", isPrimary="+volumes[ivolumes].isPrimary());

            //if(volumes[ivolumes].isPrimary() == true && volumes[ivolumes].isEmulated() == true)
            //    default_sdcard_path = path.toString();

            if(volumes[ivolumes].isRemovable() == true)
                external_sdcard_path = path.toString();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mTextView01 = (TextView) findViewById(R.id.myTextView1);
        mTextView01.setText(TIP);
        mContext = this;
        GetExternalSDPath();

        mMode = getModeFromProperty();
        Log.d(TAG, "CONFIG_MAP, "+mMode);
        mRadioGroup = findViewById(R.id.config_radio_group);
        mRadioGroup.setOnCheckedChangeListener(this);
        mRadioGroup.check(CONFIG_MAP.getOrDefault(mMode, R.id.config_lte));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        switch (checkedId) {
            case R.id.config_lte:
                mMode = CONFIG_LTE;
                Log.d(TAG, "onCheckedChanged, CONFIG_LTE");
                break;
            case R.id.config_gnss:
                mMode = CONFIG_GNSS;
                Log.d(TAG, "onCheckedChanged, CONFIG_GNSS");
                break;
        }
    }

    public void enableQXDM(int resId) {
        // enable QXDM here.
        if(isQxdmEnabled() == false) {
            Log.d(TAG, "Start QXDM tool ......");
            GetExternalSDPath();//FIXME:End user may remove SD card during test
            if (!TextUtils.isEmpty(external_sdcard_path)) {
                String config = "default_logmask.cfg";
                String log_folder = "diag_logs";
                String path = external_sdcard_path+"/"+log_folder;
                String default_path= "/sdcard/"+log_folder;
                String input = external_sdcard_path+"/"+log_folder+"/"+config;
                String cmd_00 = "mkdir "+external_sdcard_path+"/"+log_folder;
                String cmd_01 = "cp "+default_path+"/"+config+" "+input;
                String cmd_02 = "/vendor/bin/diag_mdlog -f "+input+" -o "+path+" &";
                copyFilesFromRaw(mContext, resId, config, default_path);
                Log.d(TAG, cmd_00);
                execCmd(mContext, cmd_00);
                Log.d(TAG, cmd_01);
                execCmd(mContext, cmd_01);
                Log.d(TAG, cmd_02);
                execCmd(mContext, cmd_02);
            } else { //Internal Flash
                String config = "default_logmask.cfg";
                String path = "/sdcard/diag_logs";
                String cmd = "/vendor/bin/diag_mdlog -f "+config_def+" -o "+path+" &";
                copyFilesFromRaw(mContext, resId, config, path);
                Log.d(TAG, cmd);
                execCmd(mContext, cmd);
            }
            setSystemProperty(qxdm_running, "true");
        }

        String msg = "QXDM tool is just starting";
        Toast.makeText(mContext,msg,Toast.LENGTH_SHORT).show();
    }

    public void clearQxdmData() {
        if (!TextUtils.isEmpty(external_sdcard_path)) {
            String log_folder = "diag_logs";
            String cmd_00 = "rm "+external_sdcard_path+"/"+log_folder+" -rf";
            Log.d(TAG, cmd_00);
            execCmd(mContext, cmd_00);
            String cmd_01 = "rm "+config_def+" -rf";
            Log.d(TAG, cmd_01);
            execCmd(mContext, cmd_01);
        } else { //Internal Flash
            String path = "/sdcard/diag_logs";
            String cmd = "rm "+path+" -rf";
            Log.d(TAG, cmd);
            execCmd(mContext, cmd);
        }
    }

    public void stopQXDM(View v) {
        if(isQxdmEnabled()) {
            String msg = "Stop QXDM tool now ...";
            Toast.makeText(mContext,msg,Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Stop QXDM tool ......");
            execCmd(mContext, cmd_kill);
            clearQxdmData();
            setSystemProperty(qxdm_running, "false");
        }
    }

    public void startQXDM(View v) {
        if(mMode.equals(CONFIG_LTE)) {//LTE
            resource_id = R.raw.default_logmask;
            setSystemProperty(gnss_log, "false");
        } else if(mMode.equals(CONFIG_GNSS)) {//GNSS
            resource_id = R.raw.gnss_logmask;
            setSystemProperty(gnss_log, "true");
        }
        Log.d(TAG, "mMode="+mMode+" resource ID="+resource_id);
        setSystemProperty(qxdm_function, mMode);

        if(isQxdmEnabled() == false) {
            enableQXDM(resource_id);
        } else {
            String msg = "QXDM tool is already running";
            Toast.makeText(mContext,msg,Toast.LENGTH_SHORT).show();
        }
    }


}
