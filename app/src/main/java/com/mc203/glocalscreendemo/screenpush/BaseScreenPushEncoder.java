package com.mc203.glocalscreendemo.screenpush;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.mc203.glocalscreendemo.util.DisplayUtil.byteToHex;

public class BaseScreenPushEncoder {
    private static final String TAG = "BaseScreenPushEncoder";

    private Surface surface;

    private int width;
    private int height;

    private MediaCodec videoEncodec;
    private MediaFormat videoFormat;
    private MediaCodec.BufferInfo videoBufferinfo;

    private BaseScreenPushEncoder.VideoEncodecThread videoEncodecThread;

    private MediaProjection mMediaProjection;

    private VirtualDisplay mVirtualDisplay;

    private BaseScreenPushEncoder.OnMediaInfoListener onMediaInfoListener;

    public BaseScreenPushEncoder(Context context) {

    }

    public void setOnMediaInfoListener(OnMediaInfoListener onMediaInfoListener) {
        this.onMediaInfoListener = onMediaInfoListener;
    }

    public void initEncodec(int width, int height, MediaProjection mp)
    {
        this.width = width;
        this.height = height;
        mMediaProjection = mp;
        initMediaEncodec(width, height, 44100, 2);
    }

    public void startRecord()
    {
        if(surface != null)
        {
            videoEncodecThread = new BaseScreenPushEncoder.VideoEncodecThread(new WeakReference<BaseScreenPushEncoder>(this));
            videoEncodecThread.start();
        }
    }

    public void stopRecord()
    {
        if(videoEncodecThread != null)
        {
            videoEncodecThread.exit();
//            audioEncodecThread.exit();
            videoEncodecThread = null;
//            audioEncodecThread = null;
        }
    }



    private void initMediaEncodec(int width, int height, int sampleRate, int channelCount)
    {
        initVideoEncodec(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
//        initAudioEncodec(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount);
//        initPCMRecord();
    }

    private void initVideoEncodec(String mimeType, int width, int height)
    {
        try {
            videoBufferinfo = new MediaCodec.BufferInfo();
            videoFormat = MediaFormat.createVideoFormat(mimeType, width, height);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 4);
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            videoEncodec = MediaCodec.createEncoderByType(mimeType);
            videoEncodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            surface = videoEncodec.createInputSurface();

            mVirtualDisplay = mMediaProjection.createVirtualDisplay(TAG + "-display", width, height, 1,
                                                                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                                                                surface, null, null);
            Log.d(TAG, "created virtual display: " + mVirtualDisplay);

        } catch (IOException e) {
            e.printStackTrace();
            videoEncodec = null;
            videoFormat = null;
            videoBufferinfo = null;
        }

    }

    static class VideoEncodecThread extends Thread
    {
        private WeakReference<BaseScreenPushEncoder> encoder;

        private AtomicBoolean isExit = new AtomicBoolean(false);

        private MediaCodec videoEncodec;
        private MediaCodec.BufferInfo videoBufferinfo;

        private long pts;
        private byte[] sps;
        private byte[] pps;
        private boolean keyFrame = false;

        public VideoEncodecThread(WeakReference<BaseScreenPushEncoder> encoder) {
            this.encoder = encoder;
            videoEncodec = encoder.get().videoEncodec;
            videoBufferinfo = encoder.get().videoBufferinfo;
        }

        @Override
        public void run() {
            super.run();
            pts = 0;

            isExit.set(false);
            videoEncodec.start();
            while(true)
            {
                if(isExit.get())
                {
                    videoEncodec.stop();
                    videoEncodec.release();
                    videoEncodec = null;
                    Log.d("ywl5320", "录制完成");
                    break;
                }

                int outputBufferIndex = videoEncodec.dequeueOutputBuffer(videoBufferinfo, 0);
                keyFrame = false;
                if(outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)
                {
                    Log.d("ywl5320", "INFO_OUTPUT_FORMAT_CHANGED");

                    ByteBuffer spsb = videoEncodec.getOutputFormat().getByteBuffer("csd-0");
                    sps = new byte[spsb.remaining()];
                    spsb.get(sps, 0, sps.length);

                    ByteBuffer ppsb = videoEncodec.getOutputFormat().getByteBuffer("csd-1");
                    pps = new byte[ppsb.remaining()];
                    ppsb.get(pps, 0, pps.length);

                    Log.d("ywl5320", "sps:" + byteToHex(sps));
                    Log.d("ywl5320", "pps:" + byteToHex(pps));

                }
                else
                {
                    while (outputBufferIndex >= 0)
                    {
                        ByteBuffer outputBuffer = videoEncodec.getOutputBuffer(outputBufferIndex);
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
                        Log.d("ywl5320", "data("+data.length+"):" + byteToHex(data));

                        if(videoBufferinfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME)
                        {
                            keyFrame = true;
                            if(encoder.get().onMediaInfoListener != null)
                            {
                                encoder.get().onMediaInfoListener.onSPSPPSInfo(sps, pps);
                            }
                        }
                        if(encoder.get().onMediaInfoListener != null)
                        {
                            encoder.get().onMediaInfoListener.onVideoInfo(data, keyFrame);
                            encoder.get().onMediaInfoListener.onMediaTime((int) (videoBufferinfo.presentationTimeUs / 1000000));
                        }
                        videoEncodec.releaseOutputBuffer(outputBufferIndex, false);
                        outputBufferIndex = videoEncodec.dequeueOutputBuffer(videoBufferinfo, 0);
                    }
                }
            }
        }

        public void exit()
        {
            isExit.set(true);
        }

    }

    public interface OnMediaInfoListener
    {
        void onMediaTime(int times);

        void onSPSPPSInfo(byte[] sps, byte[] pps);

        void onVideoInfo(byte[] data, boolean keyframe);

        void onAudioInfo(byte[] data);

    }
}
