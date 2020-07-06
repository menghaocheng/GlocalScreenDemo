/*
 * Copyright (c) 2014 Yrom Wang <http://www.yrom.net>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mc203.glocalscreendemo.screenpush;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.mc203.glocalscreendemo.util.DisplayUtil.byteToHex;


/**
 * @author Yrom
 */
public class ScreenPushEncoder_back extends Thread {
    private static final String TAG = "ScreenRecorder";

    private int mWidth;
    private int mHeight;
//    private int mBitRate;
    private int mDpi;
    //private String mDstPath;
    private MediaProjection mMediaProjection;

    private MediaCodec videoEncodec;
    private Surface mSurface;
    private AtomicBoolean mQuit = new AtomicBoolean(false);
    private MediaCodec.BufferInfo videoBufferinfo;

    private VirtualDisplay mVirtualDisplay;

    OnScreenInfoListener mOnScreenInfoListener;

    private long pts;
    private byte[] sps;
    private byte[] pps;
    private boolean keyFrame = false;

    public ScreenPushEncoder_back(int width, int height, MediaProjection mp) {
        super(TAG);
        mWidth = width;
        mHeight = height;
        mDpi = 1;
        mMediaProjection = mp;
    }



    public void setmOnScreenInfoListener(OnScreenInfoListener mOnScreenInfoListener) {
        this.mOnScreenInfoListener = mOnScreenInfoListener;
    }

    /**
     * stop task
     */
    public final void quit() {
        mQuit.set(true);
    }

    @Override
    public void run() {
        pts = 0;
        try {
            initVideoEncodec(MediaFormat.MIMETYPE_VIDEO_AVC, mWidth, mHeight);
            videoEncodec.start();

            mVirtualDisplay = mMediaProjection.createVirtualDisplay(TAG + "-display",
                    mWidth, mHeight, mDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    mSurface, null, null);
            Log.d(TAG, "created virtual display: " + mVirtualDisplay);
            recordVirtualDisplay();

        } finally {
            release();
        }
    }

    private void recordVirtualDisplay() {
        keyFrame = false;
        while (!mQuit.get()) {
            int outputBufferIndex = videoEncodec.dequeueOutputBuffer(videoBufferinfo, 0);
            if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d("ywl5320", "INFO_OUTPUT_FORMAT_CHANGED");
                ByteBuffer spsb = videoEncodec.getOutputFormat().getByteBuffer("csd-0");
                sps = new byte[spsb.remaining()];
                spsb.get(sps, 0, sps.length);

                ByteBuffer ppsb = videoEncodec.getOutputFormat().getByteBuffer("csd-1");
                pps = new byte[ppsb.remaining()];
                ppsb.get(pps, 0, pps.length);

                Log.d("ywl5320", "sps:" + byteToHex(sps));
                Log.d("ywl5320", "pps:" + byteToHex(pps));


            } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                //Log.d(TAG, "retrieving buffers time out!");
                try {
                    Thread.sleep(10); // wait 10ms
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else if (outputBufferIndex >= 0) {
                while (outputBufferIndex >= 0)
                {
                    ByteBuffer outputBuffer = videoEncodec.getOutputBuffers()[outputBufferIndex];
                    outputBuffer.position(videoBufferinfo.offset);
                    outputBuffer.limit(videoBufferinfo.offset + videoBufferinfo.size);
                    //
                    if(pts == 0)
                    {
                        pts = videoBufferinfo.presentationTimeUs;
                    }
                    videoBufferinfo.presentationTimeUs = videoBufferinfo.presentationTimeUs - pts;

                    byte[] data = new byte[outputBuffer.remaining()];
                    outputBuffer.get(data, 0, data.length);
                    //Log.d("ywl5320", "data(" + data.length + "):" + byteToHex(data));

                    if(videoBufferinfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME)
                    {
                        keyFrame = true;
                        if(mOnScreenInfoListener != null)
                        {
                            mOnScreenInfoListener.onSPSPPSInfo(sps, pps);
                        }
                    }
                    if(mOnScreenInfoListener != null)
                    {
                        mOnScreenInfoListener.onVideoInfo(data, keyFrame);
                        mOnScreenInfoListener.onMediaTime((int) (videoBufferinfo.presentationTimeUs / 1000000));
                    }
                    videoEncodec.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = videoEncodec.dequeueOutputBuffer(videoBufferinfo, 0);
                }
            }
        }
    }

    private void initVideoEncodec(String mimeType, int width, int height)
    {
        try {
            videoBufferinfo = new MediaCodec.BufferInfo();
            MediaFormat videoFormat = MediaFormat.createVideoFormat(mimeType, width, height);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 4);
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 3);

            videoEncodec = MediaCodec.createEncoderByType(mimeType);
            videoEncodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            mSurface = videoEncodec.createInputSurface();

        } catch (IOException e) {
            e.printStackTrace();
            videoEncodec = null;
            videoBufferinfo = null;
        }

    }


    private void release() {
        if (videoEncodec != null) {
            videoEncodec.stop();
            videoEncodec.release();
            videoEncodec = null;
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
        }

    }


    public interface OnScreenInfoListener
    {
        void onMediaTime(int times);

        void onSPSPPSInfo(byte[] sps, byte[] pps);

        void onVideoInfo(byte[] data, boolean keyframe);

        //void onAudioInfo(byte[] data);

    }
}
