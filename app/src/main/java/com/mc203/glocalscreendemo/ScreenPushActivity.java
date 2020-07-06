package com.mc203.glocalscreendemo;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.mc203.glocalscreendemo.camera.WlCameraView;
import com.mc203.glocalscreendemo.push.WlConnectListener;
import com.mc203.glocalscreendemo.push.WlPushVideo;
import com.mc203.glocalscreendemo.screenpush.BaseScreenPushEncoder;
import com.mc203.glocalscreendemo.screenpush.ScreenPushEncodec;

import java.util.Locale;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.os.Build.VERSION_CODES.M;

public class ScreenPushActivity extends Activity {
    private static final String TAG = "ScreenPushActivity";

    private static final int REQUEST_MEDIA_PROJECTION = 1;
    private static final int REQUEST_PERMISSIONS = 2;

    private WlPushVideo wlPushVideo;
    private WlCameraView wlCameraView;
    private boolean start = false;

    private static final int REQUEST_CODE = 1;
    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;
    private ScreenPushEncodec mScreenPushEncodec;
    private Button btnStartScreenPush;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screenpush);
        wlCameraView = findViewById(R.id.cameraview);
        btnStartScreenPush = findViewById(R.id.btn_screenpush);
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        wlPushVideo = new WlPushVideo();
        wlPushVideo.setWlConnectListener(new WlConnectListener() {
            @Override
            public void onConnecting() {
                Log.d("mhc203", "链接服务器中..");
            }

            @Override
            public void onConnectSuccess() {
                Log.d("mhc203", "链接服务器成功，可以开始推流了");
                mScreenPushEncodec = new ScreenPushEncodec( ScreenPushActivity.this);
                mScreenPushEncodec.initEncodec(720, 1280, mMediaProjection);
                mScreenPushEncodec.startRecord();
                mScreenPushEncodec.setOnMediaInfoListener(new BaseScreenPushEncoder.OnMediaInfoListener() {
                    @Override
                    public void onMediaTime(int times) {                    }

                    @Override
                    public void onSPSPPSInfo(byte[] sps, byte[] pps) {
                        wlPushVideo.pushSPSPPS(sps, pps);
                    }

                    @Override
                    public void onVideoInfo(byte[] data, boolean keyframe) {
                        wlPushVideo.pushVideoData(data, keyframe);
                    }

                    @Override
                    public void onAudioInfo(byte[] data) {

                    }
                });

                moveTaskToBack(true);
            }

            @Override
            public void onConnectFail(String msg) {
                Log.d("ywl5320", msg);
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.e(TAG, "requestCode:" + requestCode + ",resultCode:" + requestCode );
        MediaProjection mediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            Log.e(TAG, "media projection is null");
            return;
        }
        mMediaProjection = mediaProjection;
        mMediaProjection.registerCallback(mProjectionCallback, new Handler());
        wlPushVideo.initLivePush(GlobalConfig.RTMP_ADDRESS);
        btnStartScreenPush.setText("停止共享");

    }

    public void startScreenPush(View v) {
        if (mScreenPushEncodec != null) {
            mScreenPushEncodec.stopRecord();
            wlPushVideo.stopPush();
            mScreenPushEncodec = null;
            btnStartScreenPush.setText("共享屏幕");
        } else {
            if(hasPermissions()){
                Intent captureIntent = mMediaProjectionManager.createScreenCaptureIntent();
                startActivityForResult(captureIntent, REQUEST_CODE);
            } else if (Build.VERSION.SDK_INT >= M) {
                requestPermissions();
            } else {
                toast(getString(R.string.no_permission_to_write_sd_ard));
            }

        }
    }

    private MediaProjection.Callback mProjectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            if (mScreenPushEncodec != null) {
                mScreenPushEncodec.stopRecord();
            }
        }
    };

    private void toast(String message, Object... args) {

        int length_toast = Locale.getDefault().getCountry().equals("BR") ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT;
        // In Brazilian Portuguese this may take longer to read

        Toast toast = Toast.makeText(this,
                (args.length == 0) ? message : String.format(Locale.US, message, args),
                length_toast);
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(toast::show);
        } else {
            toast.show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mScreenPushEncodec != null){
            mScreenPushEncodec.stopRecord();
            wlPushVideo.stopPush();
            mScreenPushEncodec = null;
        }
    }

    @TargetApi(M)
    private void requestPermissions() {
        String[] permissions =  new String[]{WRITE_EXTERNAL_STORAGE, RECORD_AUDIO};

        boolean showRationale = false;
        for (String perm : permissions) {
            showRationale |= shouldShowRequestPermissionRationale(perm);
        }
        if (!showRationale) {
            requestPermissions(permissions, REQUEST_PERMISSIONS);
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.using_your_mic_to_record_audio))
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        requestPermissions(permissions, REQUEST_PERMISSIONS))
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .show();
    }

    private boolean hasPermissions() {
        PackageManager pm = getPackageManager();
        String packageName = getPackageName();
        int granted = pm.checkPermission(RECORD_AUDIO, packageName)
                | pm.checkPermission(WRITE_EXTERNAL_STORAGE, packageName);
        return granted == PackageManager.PERMISSION_GRANTED;
    }
}
