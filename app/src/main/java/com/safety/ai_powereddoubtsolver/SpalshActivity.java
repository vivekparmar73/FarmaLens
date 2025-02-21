package com.safety.ai_powereddoubtsolver;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

public class SpalshActivity extends AppCompatActivity {
    CardView splash_logo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spalsh);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        splash_logo = findViewById(R.id.splash_logo);



        Animation scaleAnimation = AnimationUtils.loadAnimation(this, R.anim.scale_anim);
        splash_logo.startAnimation(scaleAnimation);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(SpalshActivity.this, MainActivity.class);
                startActivity(intent);
            }
        }, 3000);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}