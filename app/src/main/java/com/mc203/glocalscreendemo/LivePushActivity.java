package com.mc203.glocalscreendemo;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.mc203.glocalscreendemo.camera.WlCameraView;
import com.mc203.glocalscreendemo.push.WlBasePushEncoder;
import com.mc203.glocalscreendemo.push.WlConnectListener;
import com.mc203.glocalscreendemo.push.WlPushEncodec;
import com.mc203.glocalscreendemo.push.WlPushVideo;

public class LivePushActivity extends AppCompatActivity {

    private WlPushVideo wlPushVideo;
    private WlCameraView wlCameraView;
    private boolean start = false;
    private WlPushEncodec wlPushEncodec;
    private Button btnStartPush;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_livepush);
        wlCameraView = findViewById(R.id.cameraview);
        btnStartPush = findViewById(R.id.btn_startpush);
        wlPushVideo = new WlPushVideo();
        wlPushVideo.setWlConnectListener(new WlConnectListener() {
            @Override
            public void onConnecting() {
                Log.d("ywl5320", "链接服务器中..");

            }

            @Override
            public void onConnectSuccess() {
                Log.d("ywl5320", "链接服务器成功，可以开始推流了");
                wlPushEncodec = new WlPushEncodec(LivePushActivity.this, wlCameraView.getTextureId());
                wlPushEncodec.initEncodec(wlCameraView.getEglContext(), 720, 1280);
                wlPushEncodec.startRecord();
                wlPushEncodec.setOnMediaInfoListener(new WlBasePushEncoder.OnMediaInfoListener() {
                    @Override
                    public void onMediaTime(int times) {

                    }

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
                        wlPushVideo.pushAudioData(data);
                    }
                });
            }

            @Override
            public void onConnectFail(String msg) {
                Log.d("ywl5320", msg);
            }
        });
    }

    public void startpush(View view) {
        start = !start;
        if(start)
        {
            btnStartPush.setText("正在推流");
            wlPushVideo.initLivePush(GlobalConfig.RTMP_ADDRESS);
        }
        else
        {
            btnStartPush.setText("开始推流");
            if(wlPushEncodec != null)
            {
                wlPushEncodec.stopRecord();
                wlPushVideo.stopPush();
                wlPushEncodec = null;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        start = true;
        if(wlPushEncodec != null)
        {
            wlPushEncodec.stopRecord();
            wlPushVideo.stopPush();
            wlPushEncodec = null;
        }
    }
}
