package com.mc203.glocalscreendemo.push;

public interface WlConnectListener {

    void onConnecting();

    void onConnectSuccess();

    void onConnectFail(String msg);

}
