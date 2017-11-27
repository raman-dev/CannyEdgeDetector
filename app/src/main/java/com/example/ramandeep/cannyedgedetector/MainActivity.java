package com.example.ramandeep.cannyedgedetector;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    private CameraFragment cameraFragment;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        System.out.println("onCreate!");

        cameraFragment = new CameraFragment();

        getFragmentManager().beginTransaction().add(R.id.fragment_container,cameraFragment).commit();
    }

    private void hideStatusAndActionBar(){
        View decorView = getWindow().getDecorView();
        // Hide the status bar.
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LOW_PROFILE |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);
    }


    @Override
    protected void onStart() {
        super.onStart();
        System.out.println("onStart!");
    }

    @Override
    protected void onRestart(){
        super.onRestart();
        System.out.println("onRestart!");
    }

    @Override
    protected void onResume(){
        super.onResume();
        System.out.println("onResume!");
        hideStatusAndActionBar();
    }

    @Override
    protected void onPause(){
        super.onPause();
        System.out.println("onPause!");
    }

    @Override
    protected void onStop(){
        super.onStop();
        System.out.println("onStop!");
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        System.out.println("onDestroy!");
    }
}
