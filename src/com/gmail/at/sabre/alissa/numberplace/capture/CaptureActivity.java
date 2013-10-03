package com.gmail.at.sabre.alissa.numberplace.capture;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.OpenCVLoader;

import com.gmail.at.sabre.alissa.numberplace.K;
import com.gmail.at.sabre.alissa.numberplace.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

/**
 * @author seki
 *
 */
public class CaptureActivity extends Activity {
	
	private static final String TAG = ".numberplace..CaptureActivity";
	
	private static final int REQ_CAMERA = 777;
	
	private static final String EXTRA_BITMAPDATA = "data"; // XXX
	
	private Handler mHandler;
	
	private CaptureWorkerThread mThread;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture);
        
        mHandler = new Handler();
        
        mThread = new CaptureWorkerThread();
        mThread.setCallback(new CaptureWorkerThread.Callback() {
			public void onPuzzleRecognized(byte[][] puzzle, Bitmap bitmap) {
				thread_onPuzzleSolved(puzzle, bitmap);
			}
		});

        //findViewById(R.id.busy).setVisibility(View.INVISIBLE);
        
//        File dir = getApplicationContext().getCacheDir();
//        File dir = getApplicationContext().getExternalCacheDir();
//        boolean e1 = dir.exists();
//        String filename = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".jpg";
//        File file = new File(dir, filename);
//        try {
//	        if (!file.exists()) {
//	        	file.createNewFile();
//	        }
//        } catch (IOException e) {
//        	finish(); // XXX
//        }
        
        File dir = getApplicationContext().getDir("tmpimg", Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE);
        String filename = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".jpg";
        File file = new File(dir, filename);
        
        	
        Intent request = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
//        request.setData(Uri.fromFile(file));
//        request.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//        request.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        request.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(file));
        startActivityForResult(request, REQ_CAMERA);
    }

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQ_CAMERA) {
			if (resultCode == RESULT_OK) {
				mThread.setBitmap((Bitmap)data.getParcelableExtra(EXTRA_BITMAPDATA));
			} else {
				mThread.quit();
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		
		// This MainActivity doesn't need OpenCV library,
		// but the initialization may pop up a dialog and
		// may invoke Google Play App for downloading
		// OpenCV stuff, so it's better the process to happen
		// early.
		Log.i(TAG, "start initializing OpenCV");
		final Context appContext = this;
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_6, appContext, new BaseLoaderCallback(appContext) {
			@Override
			public void onManagerConnected(int status) {
				if (status == SUCCESS) {
					Log.i(TAG, "OpenCV initialization successful");
					mThread.startWorking();
				} else {
					Log.w(TAG, "OpenCV initialization unsuccessful");
					super.onManagerConnected(status);
					finish();
				}
			}
		});
	}

	private void thread_onPuzzleSolved(final byte[][] puzzle, final Bitmap bitmap) {
		if (bitmap != null) {
			mHandler.post(new Runnable() {
				public void run() {
					((ImageView)findViewById(R.id.imageView1)).setImageBitmap(bitmap);
					((TextView)findViewById(R.id.debug_text)).setText(String.format("%d x %d", bitmap.getWidth(), bitmap.getHeight()));
				}
			});
		}
				
		mHandler.post(new Runnable() {	
			public void run() {
				((ImageView)findViewById(R.id.imageView1)).setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						Intent result = new Intent();
						result.putExtra(K.EXTRA_PUZZLE_DATA, puzzle);
						setResult((puzzle == null) ? RESULT_CANCELED : RESULT_OK, result);
						finish();   
					}
				});
			}
		});
    }

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mThread.quit();
		mThread = null;
	}
}
