package com.gmail.at.sabre.alissa.numberplace.capture;

import java.io.IOException;
import java.io.InputStream;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.OpenCVLoader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.widget.ImageView;

import com.gmail.at.sabre.alissa.numberplace.K;
import com.gmail.at.sabre.alissa.numberplace.R;
import com.gmail.at.sabre.alissa.ocr.Ocr;

/**
 * An activity to run a separate thread to recognize a puzzle board on a
 * picture. From the UI perspective, the only apperance of this activity is to
 * show an indeterminate progress bar (a rounding circle) until the puzzle
 * recognition is complete. :-) It usually takes several seconds unless the CPU
 * is too slow.
 *
 * @author alissa
 *
 */
public class CaptureActivity extends Activity {

	private static final String TAG = ".numberplace..CaptureActivity";

	private Handler mHandler;

	private CaptureWorkerThread mThread;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture);

        mHandler = new Handler();

		// If the user turns the device when the view (a rounding circle) of
		// this activity is visible, this activity is destroyed and recreated to
		// update the view orientation. It causes a bad side effects: The
        // CaptureWorkerThread is discarded and another instance is restarted,
        // dropping all
		// intermediate results from a time consuming recognition jobs.
        // It is a waste of CPU power and battery life.
		// FIXME!

		final byte[] bytes = getIntent().getByteArrayExtra(K.EXTRA_IMAGE_DATA);
		final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
		((ImageView)findViewById(R.id.imageView)).setImageBitmap(bitmap);

		mThread = new CaptureWorkerThread();
        mThread.setOcr(prepareOcr());
		mThread.setBitmap(bitmap);
        mThread.setCallback(new CaptureWorkerThread.Callback() {
			public void onPuzzleRecognized(byte[][] puzzle, Bitmap bitmap) {
				thread_onPuzzleRecognized(puzzle, bitmap);
			}
		});
    }

    private Ocr prepareOcr() {
    	try {
    		final InputStream is = getApplicationContext().getResources().openRawResource(R.raw.ocr_data);
    		try {
    			return new Ocr(is);
    		} finally {
    			is.close();
    		}
    	} catch (IOException e) {
    		throw new RuntimeException(e);
    	}
    }

	@Override
	protected void onResume() {
		super.onResume();

		// We have duplicate OpenCV initialization codes here and in MainActivity.  XXX
		// Also, we need to show a more user friendly dialog than those of OpenCV library default.  XXX
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

	private void thread_onPuzzleRecognized(final byte[][] puzzle, Bitmap bitmap) {
		mHandler.post(new Runnable() {
			public void run() {
				Intent result = new Intent();
				result.putExtra(K.EXTRA_PUZZLE_DATA, puzzle);
				setResult((puzzle == null) ? RESULT_CANCELED : RESULT_OK, result);
				finish();
			}
		});
    }

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mThread.quit();
		mThread = null;
	}

	// TODO: Take care of device rotation.  The following method may help.
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
