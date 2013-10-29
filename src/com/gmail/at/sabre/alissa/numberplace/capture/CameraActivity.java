package com.gmail.at.sabre.alissa.numberplace.capture;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
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
 * <p>
 * TODO: For now a picture is taken only by touching on the screen. It should
 * accept D-pad OK and 'shutter' button, if ones are available on the device.
 *
 * @author alissa
 */
public class CameraActivity extends Activity implements SurfaceHolder.Callback, View.OnClickListener {

    private static final String TAG = "numberplace..CameraActivity";

    private View mContentView;

    private Handler mHandler;

    /***
     * The thread that directly accesses the camera. Note that {@link #mCamera}
     * and {@link #mAutoFocusRequired} logically belong to this thread, and the
     * activity's main thread (our UI thread) never touches them.
     */
    private CameraThread mCameraThread;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mContentView = findViewById(R.id.content_view);

        final SurfaceHolder holder = ((SurfaceView)findViewById(R.id.surfaceView)).getHolder();
        holder.addCallback(this);
        initHolder(holder);

        mHandler = new Handler();
    }

    @SuppressWarnings("deprecation")
    private static void initHolder(SurfaceHolder holder) {
        // Although Google deprecated SURFACE_TYPE_PUSH_BUFFERS
        // and lint alerts on it, the call is absolutely
        // needed on Android devices before API 11 (3.0).
        // We can't live without one.
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void surfaceCreated(final SurfaceHolder holder) {
        Log.i(TAG, "surfaceCreated");
        mCameraThread = new CameraThread(this);
        mCameraThread.start();
        mCameraThread.initialize();
    }

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
		        if (Math.abs(vw - nw) > 1 || Math.abs(vh - nh) > 1) {
		            final ViewGroup.LayoutParams layoutParams = surfaceView.getLayoutParams();
		            layoutParams.width = nw;
		            layoutParams.height = nh;
		            surfaceView.setLayoutParams(layoutParams);
		        }

		        // We have the surface view resized properly.  Start camera preview now.
		        final SurfaceHolder holder = surfaceView.getHolder();
		        mCameraThread.startPreview(holder);
		        mContentView.setOnClickListener(CameraActivity.this);
			}
		});
    }

    public void surfaceChanged(final SurfaceHolder holder, final int format, final int width, final int height) {
        Log.i(TAG, "surfaceChanged");

        // I'm surprised to find I have nothing to do here. It appears because
        // the camera preview code takes care of everything we need to do when
        // the surface is changed. Thank you, Google!
    }

    public void onClick(View v) {
        mContentView.setOnClickListener(null);
        final int rotation = getWindowManager().getDefaultDisplay().getRotation();
        mCameraThread.focusAndShoot(rotation);
    }

    public void onPictureTaken(final byte[] data, final int rotation) {
        Log.i(TAG, "camera_onPictureTaken");
        mHandler.post(new Runnable() {
			public void run() {
		        Intent result = new Intent();
		        result.putExtra(K.EXTRA_IMAGE_DATA, data);
		        result.putExtra(K.EXTRA_DEVICE_ROTATION, rotation);
		        setResult(RESULT_OK, result);
		        finish();
			}
		});
    }

    public void surfaceDestroyed(final SurfaceHolder holder) {
        Log.i(TAG, "surfaceDestroyed");

        mContentView.setOnClickListener(null);
        mCameraThread.terminate();
        try {
            mCameraThread.join();
        } catch (InterruptedException e) {
            // I don't think we need to take care of the case.
        }
        mCameraThread = null;
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

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
    }

}
