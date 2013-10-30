package com.gmail.at.sabre.alissa.numberplace.capture;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import com.gmail.at.sabre.alissa.numberplace.K;
import com.gmail.at.sabre.alissa.numberplace.R;

/***
 * An activity that use a camera hardware to take a picture. This is similar to a camera
 * application invoked through an intent action
 * {@link android.provider.MediaStore#ACTION_IMAGE_CAPTURE}, but its UI is far
 * simpler and the way it returns the taken photo in a different way.
 * <p>
 * I wrote this code because I thought the difference I just mentioned was
 * important for the number place app: From the users' perspective, the full
 * feature camera functions (e.g., zooming, human face detection, self-timer,
 * ...) are useless, and it is just harmful that after taking a photo we need to
 * review the photo and push (touch) a button labeled "OK", "Save", or
 * "Use This Picture". And from the developer's perspective, the way the taken
 * picture is returned from the ACTION_IMAGE_CAPTURE activity is not documented
 * well and there are a lot of actual differences among implementations. I don't
 * want to write codes to handle tons of separate cases.
 *
 * @author alissa
 */
public class CameraActivity extends Activity
		implements SurfaceHolder.Callback, View.OnClickListener, CameraThread.Callback {

    private static final String TAG = "numberplace..CameraActivity";

    private Handler mHandler;

    /*** The thread that owns and directly accesses the camera. */
    private CameraThread mCameraThread;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        findViewById(R.id.content_view).setOnClickListener(this);

        final SurfaceHolder holder = ((SurfaceView)findViewById(R.id.surfaceView)).getHolder();
        holder.addCallback(this);
        setSurfaceType(holder);

        mCameraThread = new CameraThread(this);

        mHandler = new Handler();
    }

    /***
     * Set the type of a {@link SurfaceHolder} to {@link SurfaceHolder#SURFACE_TYPE_PUSH_BUFFERS}.
     * <p>
     * Although Google deprecated both {@link SurfaceHolder#setType(int)}
     * method and {@link SURFACE_TYPE_PUSH_BUFFERS} constant
     * and let lint alert on it, the call is absolutely
     * needed on Android devices before API 11 (3.0).
     * @param holder
     */
    @SuppressWarnings("deprecation")
    private static void setSurfaceType(final SurfaceHolder holder) {
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void surfaceCreated(final SurfaceHolder holder) {
        Log.i(TAG, "surfaceCreated");
        mCameraThread.start();
        mCameraThread.initialize(holder);
    }

    /***
     * Adjust the surface view size
     * to match its aspect ratio with the specified preview size's.
     * This method is Called by {@link CameraThread}
     * when an appropriate camera preview size
     * is decided during its initialization.
     * We need to use {@link Handler} since it is invoked by a no UI thread.
     *
     * @param pw preview width
     * @param ph preview height
     */
    public void onPreviewSizeDecided(final int pw, final int ph) {
        Log.i(TAG, "onPreviewSizeDetected");
        mHandler.post(new Runnable() {
			public void run() {
		        // Resize the surface view to have the same aspect ratio as
		        // the camera preview.
		        // Assuming it is now of its maximum size,
		        // reduce the length of one side to match the aspect ratio.
		        final SurfaceView surfaceView = (SurfaceView)findViewById(R.id.surfaceView);
		        final int vw = surfaceView.getWidth();
		        final int vh = surfaceView.getHeight();
		        final int nw, nh;
		        if (pw * vh > vw * ph) {
		            nw = vw;
		            nh = vw * ph / pw;
		        } else {
		            nw = vh * pw / ph;
		            nh = vh;
		        }
		        // The one pixel allowance is to cover the errors from integral division.
		        if (Math.abs(vw - nw) > 1 || Math.abs(vh - nh) > 1) {
		            final ViewGroup.LayoutParams layoutParams = surfaceView.getLayoutParams();
		            layoutParams.width = nw;
		            layoutParams.height = nh;
		            surfaceView.setLayoutParams(layoutParams);
		        }
			}
		});
    }

    public void surfaceChanged(final SurfaceHolder holder, final int format, final int width, final int height) {
        Log.i(TAG, "surfaceChanged");

        // I'm surprised to find I have nothing to do here. The reason appears that
        // the camera preview code takes care of everything we need to do when
        // the surface is changed. Thank you, Google!
    }

    /***
     * This method is called back when anywhere on the screen is touched.
     * We use it as a trigger to take a picture.
     */
    public void onClick(View v) {
   		mCameraThread.focus();
   		mCameraThread.shoot();
    }

    @Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
    	switch (keyCode) {
    	case KeyEvent.KEYCODE_FOCUS:
    		if (event.getRepeatCount() == 0) {
    			mCameraThread.focus();
    		}
    		return true;
    	case KeyEvent.KEYCODE_CAMERA:
    		if (event.getRepeatCount() == 0) {
    			mCameraThread.shoot();
    		}
    		return true;
    	case KeyEvent.KEYCODE_DPAD_CENTER:
    		if (event.getRepeatCount() == 0) {
	   			mCameraThread.focus();
	   			mCameraThread.shoot();
    		}
   			return true;
    	default:
    		return super.onKeyDown(keyCode, event);
    	}
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_FOCUS:
			if (mCameraThread != null) {
				mCameraThread.unlockFocus();
			}
			return true;
		case KeyEvent.KEYCODE_CAMERA:
		case KeyEvent.KEYCODE_DPAD_CENTER:
			return true;
		default:
			return super.onKeyUp(keyCode, event);
		}
	}

	/***
     * {@link CameraThread} calls this method when it finished taking a picture.
     *
     * @param data
     *            JPEG data of the taken picture.
     *            The content may be invalid after returning from this method.
     */
    public void onPictureTaken(final byte[] data) {
        Log.i(TAG, "camera_onPictureTaken");
        // The array data is accessed later through the Handler.
        // We need to save its content in our own array.
        final byte[] copy = data.clone();
        mHandler.post(new Runnable() {
			public void run() {
		        Intent result = new Intent();
		        result.putExtra(K.EXTRA_IMAGE_DATA, copy);
		        result.putExtra(K.EXTRA_DEVICE_ROTATION, getWindowManager().getDefaultDisplay().getRotation());
		        setResult(RESULT_OK, result);
		        finish();
			}
		});
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
    }

    public void surfaceDestroyed(final SurfaceHolder holder) {
        Log.i(TAG, "surfaceDestroyed");
        mCameraThread.quit();
        try {
            mCameraThread.join();
        } catch (InterruptedException e) {
            // I don't think we need to take care of the case.
        }
    }

    @Override
    protected void onStart() {
        Log.i(TAG, "onStart");
        super.onStart();
    }

    @Override
    protected void onRestart() {
        Log.i(TAG, "onRestart");
        super.onRestart();
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");
        super.onStop();
    }


}
