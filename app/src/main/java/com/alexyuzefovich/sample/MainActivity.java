package com.alexyuzefovich.sample;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;

import com.alexyuzefovich.loadon.Loadon;

public class MainActivity extends AppCompatActivity {

    private boolean b = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Loadon loadon = findViewById(R.id.loadon);
        loadon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!b) {
                    loadon.startLoading();
                    b = true;
                } else {
                    loadon.stopLoading();
                    b = false;
                }
            }
        });
    }

}