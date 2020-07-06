package com.mc203.glocalscreendemo.push;

import android.content.Context;

import com.mc203.glocalscreendemo.encodec.WlBaseMediaEncoder;

public class WlPushEncodec extends WlBasePushEncoder{

    private WlEncodecPushRender wlEncodecPushRender;

    public WlPushEncodec(Context context, int textureId) {
        super(context);
        wlEncodecPushRender = new WlEncodecPushRender(context, textureId);
        setRender(wlEncodecPushRender);
        setmRenderMode(WlBaseMediaEncoder.RENDERMODE_CONTINUOUSLY);
    }
}
