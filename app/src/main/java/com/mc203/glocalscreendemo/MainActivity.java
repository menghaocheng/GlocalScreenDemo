package com.mc203.glocalscreendemo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }


    public void livepush(View view) {

        Intent intent = new Intent(this, LivePushActivity.class);
        startActivity(intent);

    }

    public void screenpush(View view) {
        Intent intent = new Intent(this, ScreenPushActivity.class);
        startActivity(intent);
    }
}
