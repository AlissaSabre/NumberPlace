package com.gmail.at.sabre.alissa.numberplace.capture;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.gmail.at.sabre.alissa.numberplace.K;

/***
 * 
 * NOTE that all CameraActivity methods are invoked by the UI thread
 * (Activity's main thread).  Time consuming tasks are passed to
 * another thread (mThread) through Handler.
 * 
 * @author alissa
 *
 */
public class CameraActivity extends Activity implements SurfaceHolder.Callback, View.OnClickListener {
	
	private static final int MAX_SIZE = 1024;
	
	/***
	 * List of known focus modes in the order of this app's preferences.
	 */
	@SuppressLint("InlinedApi")
	private static final String[] FOCUS_MODE_PREFERENCES = {
		Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE,
		Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO,
		Camera.Parameters.FOCUS_MODE_EDOF,
		Camera.Parameters.FOCUS_MODE_MACRO,
		Camera.Parameters.FOCUS_MODE_AUTO,
		Camera.Parameters.FOCUS_MODE_FIXED,
		Camera.Parameters.FOCUS_MODE_INFINITY,
	};

	private SurfaceHolder mHolder;
	
	private SurfaceView mView;
	
	private Handler mHandler;
	
	private Camera mCamera;
	
	private boolean mAutoFocusRequired;
	
	private CameraThread mThread;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mView = new SurfaceView(getApplicationContext());
        setContentView(mView);
        
        mHolder = mView.getHolder();
        mHolder.addCallback(this);
        initHolder(mHolder);
        
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
        mThread = new CameraThread();
        mThread.start();
        
		mThread.post(new Runnable() {
			public void run() {
		        mCamera = Camera.open();
		    	if (mCamera == null) {
		    		throw new RuntimeException("Can't open the default camera");
		    	}
		    	
		    	// Find and use a reasonable picture size.
		    	// We will use a largest size whose width and height are
		    	// both within the MIN_SIZE.  If no such size is available,
		    	// use the minimum one.
		    	Camera.Parameters params = mCamera.getParameters();
		    	List<Camera.Size> list = params.getSupportedPictureSizes();
		    	Camera.Size optimal = list.get(0);
		    	for (int i = 1; i < list.size(); i++) {
		    		Camera.Size size = list.get(i);
		    		if (size.width * size.height < optimal.width * optimal.height) {
		    			optimal = size;
		    		}
		    	}
		    	for (int i = 0; i < list.size(); i++) {
		    		Camera.Size size = list.get(i);
		    		if (Math.max(size.width, size.height) <= MAX_SIZE
		    				&& size.width * size.height > optimal.width * optimal.height) {
		    			optimal = size;
		    		}
		    	}
		    	// Find a matching preview size.
		    	list = params.getSupportedPreviewSizes();
		    	Camera.Size preview = list.get(0);
		    	for (int i = 1; i < list.size(); i++) {
		    		Camera.Size size = list.get(i);
		    		if (size.width * preview.height == size.height * preview.width) {
		    			preview = size;
		    			break;
		    		}
		    	}
		    	// Update the camera parameter if needed.
		    	if (params.getPictureSize() != optimal || params.getPreviewSize() != preview) {
		    		params.setPictureSize(optimal.width, optimal.height);
		    		params.setPreviewSize(preview.width, preview.height);
		    		mCamera.setParameters(params);
		    	}
		    	
		    	// Take care of focus mode.
		    	String currentFocusMode = params.getFocusMode(); 
		    	List<String> supportedFocusModes = params.getSupportedFocusModes();
		    	int currentModePreference = Arrays.asList(FOCUS_MODE_PREFERENCES).indexOf(currentFocusMode);
		    	if (currentModePreference >= 0) {
		    		// We know the current focus mode.  See if
		    		// the camera supports a more preferred mode.
		    		for (int i = 0; i < currentModePreference; i++) {
		    			if (supportedFocusModes.indexOf(FOCUS_MODE_PREFERENCES[i]) >= 0) {
		    				// Yes it does.  Use this mode.
		    				currentFocusMode = FOCUS_MODE_PREFERENCES[i];
		    				params.setFocusMode(currentFocusMode);
				    		mCamera.setParameters(params);
		    				break;
		    			}
		    		}
		    	}
		    	// Record whether the chosen (or default) focus mode requires
		    	// app issued auto-focus.
		    	mAutoFocusRequired = 
		    			currentFocusMode.equals(Camera.Parameters.FOCUS_MODE_AUTO) ||
		    			currentFocusMode.equals(Camera.Parameters.FOCUS_MODE_MACRO);
		    	
				try {
					mCamera.setPreviewDisplay(holder);
				} catch (IOException e) {
					// We can't capture if this happened...
					throw new RuntimeException("Can't preview camera", e);
				}
			}
		});
		
        mView.setOnClickListener(this);
	}
    
	public void surfaceChanged(final SurfaceHolder holder, final int format, final int width, final int height) {
		mThread.post(new Runnable() {
			public void run() {
				mCamera.startPreview();
			}
		});
	}

	public void onClick(View v) {
		mView.setOnClickListener(null);
		final int rotation = getWindowManager().getDefaultDisplay().getRotation();

		// Yes, the following spagetti of callback is messy.  I know.
		// What I don't know is how to rewrite it.
		final Runnable shoot = new Runnable() {
			public void run() {
				mCamera.takePicture(null, null, new Camera.PictureCallback() {
					// This callback is for JPEG, by the way.
					public void onPictureTaken(final byte[] data, Camera camera) {
						final byte[] copy = (byte[])data.clone();
						mHandler.post(new Runnable() {
							public void run() {
								camera_onPictureTaken(copy, rotation);
							}
						});
					}
				});
			}
		};
		mThread.post(new Runnable() {
			public void run() {
				// I've heard a rumor that in some device, invoking Camera.autoFocus
				// when not in MACRO/AUTO focus mode throws an Exception, though it is
				// contrary to the Android API document.  We can't simply call it
				// always.
				if (mAutoFocusRequired) {
					mCamera.autoFocus(new Camera.AutoFocusCallback() {
						public void onAutoFocus(boolean success, Camera camera) {
							mThread.post(shoot);
						}
					});
				} else {
					mThread.post(shoot);
				}
			}
		});
	}
	
	private void camera_onPictureTaken(final byte[] data, int rotation) {
		Intent result = new Intent();
		result.putExtra(K.EXTRA_IMAGE_DATA, data);
		result.putExtra(K.EXTRA_DEVICE_ROTATION, rotation);
		setResult(RESULT_OK, result);
		finish();
    }

	public void surfaceDestroyed(final SurfaceHolder holder) {
		mView.setOnClickListener(null);
		
		mThread.post(new Runnable() {
			public void run() {
				mCamera.stopPreview();
				mCamera.cancelAutoFocus();
				mCamera.release();
				mCamera = null;
				
				mThread.quit();
			}
		});
		
		try {
			mThread.join();
		} catch (InterruptedException e) {
			// I don't think we need to take care of the case.
		}
		mThread = null;
	}

	private class CameraThread extends HandlerThread {
		protected Handler mHandler;
		
		public CameraThread() {
			super(CameraThread.class.getName());
		}

		@Override
		public synchronized void start() {
			super.start();
			mHandler = new Handler(getLooper());
		}

		public boolean post(Runnable r) {
			return mHandler.post(r);
		}
	}
}
