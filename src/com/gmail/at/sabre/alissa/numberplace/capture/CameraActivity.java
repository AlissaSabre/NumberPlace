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
import android.view.ViewGroup;

import com.gmail.at.sabre.alissa.numberplace.K;
import com.gmail.at.sabre.alissa.numberplace.R;

/***
 * An activity that use a camera to take a picture. This is similar to a camera
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
 * <p>
 * IMPLEMENTATION NOTE: All CameraActivity methods are invoked by the UI thread
 * (Activity's main thread). Time consuming tasks are passed to another thread
 * (mThread) through Handler.
 *
 * @author alissa
 */
public class CameraActivity extends Activity implements SurfaceHolder.Callback, View.OnClickListener {

	/***
	 * The maximum size in pixels of the picture that this Activity takes. It
	 * tries to take the largest picture among ones supported by the hardware
	 * and whose width and height don't exceed this maximum.
	 */
	private static final int MAX_SIZE = 1024;

	/***
	 * List of known focus modes in the order of this app's preferences. The
	 * first supported mode will be used unless the camera is in an unknown
	 * mode.
	 * <p>
	 * Note that the list contains some values that are not available on this
	 * app's lowest supported Android version (2.2 (API8)), but it is no
	 * problem. The values are read from the .class file of
	 * {@link android.hardware.Camera.Parameters} during compile time, and plain
	 * int constants are stored in the DEX file.
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

	private View mContentView;

	private Handler mHandler;

	private Camera mCamera;

	private boolean mAutoFocusRequired;

	/***
	 * The thread that directly accesses the camera. Note that {@link #mCamera}
	 * and {@link #mAutoFocusRequired} logically belong to this thread, and the
	 * activity's main thread (our UI thread) never touches them.
	 */
	private CameraThread mThread;

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mView = (SurfaceView)findViewById(R.id.surfaceView);
        mContentView = findViewById(R.id.content_view);

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
		    		// TODO: this event should be notified to user.
		    		throw new RuntimeException("Can't open the default camera");
		    	}

				// Find and use a reasonable picture size. We will use a largest
				// size whose width and height are both within the MAX_SIZE. If
				// no such size is available, i.e., if all supported sizes
				// exceeded the app's maximum, use the minimum one.
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
					// We know the current focus mode. Use a more preferred mode
					// if the camera supports one.
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
					// TODO: this event should be notified to user, too.
					throw new RuntimeException("Can't preview camera", e);
				}

				// Resize the surface view to have the same aspect ratio as the camera preview.
				final int pw = preview.width;
				final int ph = preview.height;
				mHandler.post(new Runnable() {
					public void run() {
						final int vw = mView.getWidth();
						final int vh = mView.getHeight();
						final int nw, nh;
						if ((float)pw / ph > (float)vw / vh) {
							nw = vw;
							nh = vw * ph / pw;
						} else {
							nw = vh * pw / ph;
							nh = vh;
						}
						if (Math.abs(vw - nw) > 1 || Math.abs(vh - nh) > 1) {
							ViewGroup.LayoutParams layoutParams = mView.getLayoutParams();
					        layoutParams.width = nw;
						    layoutParams.height = nh;
						    mView.setLayoutParams(layoutParams);
						}
					}
				});
			}
		});


		mContentView.setOnClickListener(this);
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

		// Yes, the following spaghetti of callback is messy.  I know.
		// What I don't know is how to rewrite it...
		final Runnable shoot = new Runnable() {
			public void run() {
				mCamera.takePicture(null, null, new Camera.PictureCallback() {
					// This callback is for JPEG, by the way.
					public void onPictureTaken(final byte[] data, Camera camera) {
						final byte[] copy = data.clone();
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

	/***
	 * The thread that accesses the camera device. It uses {@link Handler} to
	 * execute the {@link #post}'ed actions in sequence.
	 *
	 * @author alissa
	 */
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
