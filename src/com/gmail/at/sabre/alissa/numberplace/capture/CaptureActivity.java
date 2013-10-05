package com.gmail.at.sabre.alissa.numberplace.capture;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.OpenCVLoader;

import com.gmail.at.sabre.alissa.numberplace.K;
import com.gmail.at.sabre.alissa.numberplace.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * @author alissa
 *
 */
public class CaptureActivity extends Activity {
	
	private static final String TAG = ".numberplace..CaptureActivity";
	
	private static final int REQ_CAMERA = 777;
	
	// private static final String EXTRA_BITMAPDATA = "data"; // XXX
	
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

        Intent request = new Intent(getApplicationContext(), CameraActivity.class);
        startActivityForResult(request, REQ_CAMERA);
    }

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQ_CAMERA) {
			if (resultCode == RESULT_OK) {
				final byte[] bytes = data.getByteArrayExtra(K.EXTRA_IMAGE_DATA); 
				Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
				((ImageView)findViewById(R.id.imageView)).setImageBitmap(bitmap);
				mThread.setBitmap(bitmap);
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
					((ImageView)findViewById(R.id.imageView)).setImageBitmap(bitmap);
					((TextView)findViewById(R.id.debug_text)).setText(String.format("%d x %d", bitmap.getWidth(), bitmap.getHeight()));
				}
			});
		}
				
		mHandler.post(new Runnable() {	
			public void run() {
				((ImageView)findViewById(R.id.imageView)).setOnClickListener(new View.OnClickListener() {
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
	
	private static int rotationToDegree(int rotation) {
		switch (rotation) {
		    case Surface.ROTATION_0:   return 0;
		    case Surface.ROTATION_90:  return 90;
		    case Surface.ROTATION_180: return 180;
		    case Surface.ROTATION_270: return 270;
		}
		return -1;
	}


}
