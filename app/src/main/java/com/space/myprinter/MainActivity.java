package com.space.myprinter;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.print.PrintHelper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private String[] mPicture_urls;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button btnPrint = findViewById(R.id.btn_print);
        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }
        mPicture_urls = intent.getStringArrayExtra("picture_urls");
        requestPerm();

        btnPrint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                printPhoto();
                printPhotoNew();
            }
        });
    }

    private String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE};

    private void requestPerm() {
        if (Build.VERSION.SDK_INT >= 23) {
            int permission = -1;
            permission = ActivityCompat.checkSelfPermission(this, perms[0]);
            if (permission == PackageManager.PERMISSION_DENIED) {
                ActivityCompat.requestPermissions(this, perms, 100);
            } else {
                printPhotoNew();
            }
        } else {
            printPhotoNew();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.shouldShowRequestPermissionRationale(this, perms[0]);
        } else {
            printPhotoNew();
        }
    }

    private void printPhoto() {
        PrintHelper printHelper = new PrintHelper(this);
        printHelper.setScaleMode(PrintHelper.SCALE_MODE_FIT);
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.video_play);
        printHelper.printBitmap("droids.jpg - test print", bitmap);
    }

    private void printPhotoNew() {
        PrintManager manager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        PrintAttributes.Builder builder = new PrintAttributes.Builder();
        builder.setColorMode(PrintAttributes.COLOR_MODE_COLOR);
        List<Bitmap> bitmaps = new ArrayList<>();
        if (mPicture_urls == null || mPicture_urls.length == 0) {
            Log.e("mPicture_urls", "printPhotoNew: ");
            return;
        }
        boolean is_land = false;
        for (int i = 0; i < mPicture_urls.length; i++) {
            Bitmap bitmap = BitmapFactory.decodeFile(mPicture_urls[i]);
            bitmaps.add(bitmap);
            if (bitmap.getWidth() > bitmap.getHeight())
                is_land = true;
        }
//        builder.setMediaSize(PrintAttributes.MediaSize.PRC_3);

        if (is_land){
            builder.setMediaSize(PrintAttributes.MediaSize.UNKNOWN_LANDSCAPE);

        }
        else
            builder.setMediaSize(PrintAttributes.MediaSize.UNKNOWN_PORTRAIT);


        CustomPhotoAdapter printAdapter = new CustomPhotoAdapter("photo project", bitmaps, this);
        manager.print("photo project", printAdapter, builder.build());
    }
}
