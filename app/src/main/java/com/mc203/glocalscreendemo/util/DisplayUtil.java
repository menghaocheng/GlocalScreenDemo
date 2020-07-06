package com.mc203.glocalscreendemo.util;

import android.content.Context;
import android.util.DisplayMetrics;

public class DisplayUtil {

    public static int getScreenWidth(Context context)
    {
        DisplayMetrics metric = context.getResources().getDisplayMetrics();
        return metric.widthPixels;
    }

    public static int getScreenHeight(Context context)
    {
        DisplayMetrics metric = context.getResources().getDisplayMetrics();
        return metric.heightPixels;
    }


    public static String byteToHex(byte[] bytes)
    {
        StringBuffer stringBuffer = new StringBuffer();
        for(int i = 0; i < bytes.length; i++)
        {
            String hex = Integer.toHexString(bytes[i]);
            if(hex.length() == 1)
            {
                stringBuffer.append("0" + hex);
            }
            else
            {
                stringBuffer.append(hex);
            }
            if(i > 20)
            {
                break;
            }
        }
        return stringBuffer.toString();
    }
}
