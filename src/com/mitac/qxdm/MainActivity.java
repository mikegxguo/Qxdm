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



public class MainActivity extends Activity {
    /** Called when the activity is first created. */
    private static String TAG = "MainActivity";
    private TextView mTextView01;
    private static String VERSION = "1.0.0.0    2022/8/03 12:00";
    private Context mContext;
    private StorageManager mStorageManager;
    private static String external_sdcard_path = "";
    private static String property = "sys.qxdm.running";

    public static boolean isQxdmEnabled(){
        return "true".equals(getSystemProperty(property, "false"));
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
        mTextView01.setText(VERSION);
        mContext = this;
        GetExternalSDPath();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public void enableQXDM(View v) {
        // enable QXDM here.
        if(isQxdmEnabled() == false) {
            Log.d(TAG, "start enabling QXDM");
            if (!TextUtils.isEmpty(external_sdcard_path)) {
                String config = "default_logmask.cfg";
                String log_folder = "diag_logs";
                String path = external_sdcard_path+"/"+log_folder;
                String default_path= "/sdcard/"+log_folder;
                String input = external_sdcard_path+"/"+log_folder+"/"+config;
                String cmd_00 = "mkdir "+external_sdcard_path+"/"+log_folder;
                String cmd_01 = "cp "+default_path+"/"+config+" "+input;
                String cmd_02 = "/vendor/bin/diag_mdlog -f "+input+" -o "+path+" &";
                copyFilesFromRaw(mContext, R.raw.default_logmask, config, default_path);
                Log.d(TAG, cmd_00);
                execCmd(mContext, cmd_00);
                Log.d(TAG, cmd_01);
                execCmd(mContext, cmd_01);
                Log.d(TAG, cmd_02);
                execCmd(mContext, cmd_02);
            } else { //Internal Flash
                String config = "default_logmask.cfg";
                String path = "/sdcard/diag_logs";
                String input = "/sdcard/diag_logs/default_logmask.cfg";
                String cmd = "/vendor/bin/diag_mdlog -f "+input+" -o "+path+" &";
                copyFilesFromRaw(mContext, R.raw.default_logmask, config, path);
                Log.d(TAG, cmd);
                execCmd(mContext, cmd);
            }
            setSystemProperty(property, "true");
            Log.d(TAG, "end enabling QXDM");
        }

        String msg = "QXDM log is enabled";
        Toast.makeText(mContext,msg,Toast.LENGTH_SHORT).show();
    }
}
