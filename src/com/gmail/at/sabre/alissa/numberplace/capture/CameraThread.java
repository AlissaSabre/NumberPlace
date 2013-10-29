package com.gmail.at.sabre.alissa.numberplace.capture;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import android.annotation.SuppressLint;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.SurfaceHolder;

/***
 * The thread that accesses the camera device.
 * All public methods of this class (except for Camera callback methods)
 * are intended to be called by the UI thread.
 * It then uses {@link Handler} to execute the requested actions in this thread.
 *
 * @author alissa
 */
public class CameraThread extends HandlerThread
		implements AutoFocusCallback, ShutterCallback, PictureCallback {

	protected Handler mHandler;

    /***
     * The maximum size in pixels of the picture that the CameraActivity takes.
     * It tries to take the largest picture that is supported by the hardware
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
     * string constants are stored in the DEX file.
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

    /***
     * Callback interface.
     * @author alissa
     */
    public interface Callback {

        /***
         * Called once when an appropriate camera preview size
         * is decided during the initialization.
         *
         * @param pw
         *            preview width
         * @param ph
         *            preview height
         */
        public void onPreviewSizeDecided(int pw, int ph);

    	/***
         * Called once when the CameraThread has taken a picture.
         *
         * @param data
         *            JPEG picture data.
         *            The content is valid only during this method is executing.
         */
        public void onPictureTaken(byte[] data);
    }

    /*** The callback this thread invokes. */
	private final Callback mCallback;

	/*** The camera hardware. */
    private Camera mCamera;

    /*** Whether the camera require an app-issued auto-focusing. */
    private boolean mAutoFocusRequired;

    /***
     * The state of the camera operation.
     * It takes one of the CAMERA_* values.
     */
    private int mCameraState;

    /*** The camera is not working, i.e., not ready yet or already completed. */
    private static final int CAMERA_INACTIVE = 0;

    /*** The camera is initialized and the preview has been started. */
    private static final int CAMERA_PREVIEW = 1;

    /*** The camera is auto focusing. */
    private static final int CAMERA_FOCUSING = 2;

    /*** The camera finished auto focusing, locking its focus. */
    private static final int CAMERA_FOCUSED = 3;

    /*** The camera is auto focusing and will immediately take a picture when done. */
    private static final int CAMERA_FOCUSING_TO_SHOOT = 4;

    /***
     * The constructor.
     * @param callback
     *            The callback.  It must not be a null.
     */
    public CameraThread(final Callback callback) {
        super(CameraThread.class.getName());
        mCallback = callback;
        mCameraState = CAMERA_INACTIVE;
    }

    @Override
    public synchronized void start() {
        super.start();
        // Note this method is run by another thread.
        mHandler = new Handler(getLooper());
    }

    /***
     * Initialize the camera hardware.
     * This method must be called after this CameraThread is start()ed
     * and before any other methods are called.
     * The Surface that the specified SurfaceHolder holds should have been created.
     *
     * @param holder
     *             A SurfaceHolder whose surface is used for camera preview.
     */
    public void initialize(final SurfaceHolder holder) {
        mHandler.post(new Runnable() {
            public void run() {
            	initialize_Impl(holder);
            }
        });
    }

    private void initialize_Impl(final SurfaceHolder holder) {

        mCamera = Camera.open();
        if (mCamera == null) {
            // TODO: this event should be notified to user.
            throw new RuntimeException("Can't open the default camera");
        }

        // Get the camera parameters.
        final Camera.Parameters params = mCamera.getParameters();

        // Find and use a reasonable picture size. We will use a largest
        // size whose width and height are both within the MAX_SIZE. If
        // there are multiple candidates (and usually they are), choose
        // the one with the longest shorter side.
        // Note that a usual number place puzzle board is a right square.
        List<Camera.Size> list = params.getSupportedPictureSizes();
        final Camera.Size ZERO = mCamera.new Size(0, 0);
        Camera.Size optimal = ZERO;
        // Look for the _real_ optimal size.
        for (int i = 0; i < list.size(); i++) {
            final Camera.Size size = list.get(i);
            if (Math.max(size.width, size.height) <= MAX_SIZE
                && (Math.min(size.width, size.height) > Math.min(optimal.width, optimal.height)
                    || (Math.min(size.width, size.height) == Math.min(optimal.width, optimal.height)
                        && Math.max(size.width, size.height) < Math.max(optimal.width, optimal.height)))) {
                optimal = size;
            }
        }

        // If no such size is available, i.e., if all supported sizes
        // exceeded the app's maximum, use the minimum one as a fall back.
        if (optimal == ZERO) {
            optimal = list.get(0);
            for (int i = 1; i < list.size(); i++) {
                final Camera.Size size = list.get(i);
                if (size.width * size.height < optimal.width * optimal.height) {
                    optimal = size;
                }
            }
        }

        // Find a matching preview size with the optimal picture size.
        // It should have exactly same aspect ratio as optimal size
        // and should not be too large.
        list = params.getSupportedPreviewSizes();
        Camera.Size preview = ZERO;
        for (int i = 0; i < list.size(); i++) {
            Camera.Size size = list.get(i);
            if (size.width * optimal.height == size.height * optimal.width
                && size.width <= optimal.width && size.width > preview.width) {
                preview = size;
            }
        }

        // Since preview is a preview, all picture size should have at
        // least one preview size that has a same aspect ratio with the
        // picture size and whose size is smaller than or at least equal
        // to the picture size.
        // The following is the last resort fall back that will never be used.
        if (preview == ZERO) preview = list.get(0);

        // Update the camera parameter if needed.
        if (params.getPictureSize() != optimal || params.getPreviewSize() != preview) {
            params.setPictureSize(optimal.width, optimal.height);
            params.setPreviewSize(preview.width, preview.height);
            mCamera.setParameters(params);
        }

        // Pass the decided preview size to UI thread so that it can
        // resize the surface view appropriately.
        mCallback.onPreviewSizeDecided(preview.width, preview.height);

        // Next, take care of focus mode.
        String currentFocusMode = params.getFocusMode();
        List<String> supportedFocusModes = params.getSupportedFocusModes();
        int currentModePreference = Arrays.asList(FOCUS_MODE_PREFERENCES).indexOf(currentFocusMode);
        if (currentModePreference >= 0) {
            // We know the current focus mode.
            // Use a more preferred mode if the camera supports one.
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
        // app-issued auto-focus.
        mAutoFocusRequired =
                currentFocusMode.equals(Camera.Parameters.FOCUS_MODE_AUTO) ||
                currentFocusMode.equals(Camera.Parameters.FOCUS_MODE_MACRO);

        // Start preview.
        // It appears that we don't need to wait
        // for the resize of the surface view.  Great.
        // Thank you, Google!
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (IOException e) {
            // We can't capture if this happened...
            // TODO: this event should be notified to user, too.
            throw new RuntimeException("Can't preview camera", e);
        }
        mCamera.startPreview();
        mCameraState = CAMERA_PREVIEW;
    }

    /***
     * Start auto focus operation.
     */
    public void focus() {
    	mHandler.post(new Runnable() {
    		public void run() {
    			focus_Impl();
    		}
    	});
    }

    private void focus_Impl() {
    	switch (mCameraState) {
    	case CAMERA_PREVIEW:
    	case CAMERA_FOCUSED:
	    	if (mAutoFocusRequired) {
    			mCamera.cancelAutoFocus();
	    		mCameraState = CAMERA_FOCUSING;
	    		mCamera.autoFocus(this);
	    	} else {
	    		mCameraState = CAMERA_FOCUSED;
	    	}
	    	break;
    	case CAMERA_FOCUSING:
    	case CAMERA_FOCUSING_TO_SHOOT:
    		mCameraState = CAMERA_FOCUSING;
    		break;
    	case CAMERA_INACTIVE:
    		break;
    	}
    }

    /***
     * Callback method invoked when the auto focus operation completes.
     */
    public void onAutoFocus(final boolean success, final Camera camera) {
		switch (mCameraState) {
		case CAMERA_FOCUSING:
			mCameraState = CAMERA_FOCUSED;
			break;
		case CAMERA_FOCUSING_TO_SHOOT:
			mCameraState = CAMERA_FOCUSED;
			shoot_Impl();
			break;
		case CAMERA_INACTIVE:
		case CAMERA_PREVIEW:
		case CAMERA_FOCUSED:
			break;
		}
	}

    /***
     * Cancel the on-going auto focus operation, if any, and
     * release the focus lock.
     */
    public void unlockFocus() {
    	mHandler.post(new Runnable() {
			public void run() {
				unlockFocus_Impl();
			}
		});
    }

    private void unlockFocus_Impl() {
    	switch (mCameraState) {
    	case CAMERA_FOCUSING:
    	case CAMERA_FOCUSED:
    	case CAMERA_FOCUSING_TO_SHOOT:
    		mCameraState = CAMERA_PREVIEW;
	    	try {
	    		mCamera.cancelAutoFocus();
	    	} catch (Throwable e) {
	    	}
	    	break;
    	case CAMERA_INACTIVE:
    	case CAMERA_PREVIEW:
	    	break;
    	}
    }

    /***
     * Take a picture.
     * If auto focus operation is on-going, delay the shooting until it finishes.
     */
    public void shoot() {
    	mHandler.post(new Runnable() {
            public void run() {
            	shoot_Impl();
            }
        });
    }

    private void shoot_Impl() {
    	switch (mCameraState) {
    	case CAMERA_PREVIEW:
    	case CAMERA_FOCUSED:
        	mCameraState = CAMERA_INACTIVE;
            mCamera.takePicture(this, null, this);
            break;
    	case CAMERA_FOCUSING:
    	case CAMERA_FOCUSING_TO_SHOOT:
    		mCameraState = CAMERA_FOCUSING_TO_SHOOT;
    		break;
    	case CAMERA_INACTIVE:
    		break;
    	}
    }

    /***
     * Callback method invoked when the camera hardware started taking a picture.
     */
	public void onShutter() {
		// We have nothing to do here,
		// but just passing a null to takePicture method triggers a malfunction
		// on some phone,
		// so we need to pass a valid callback object.
	}

	/***
	 * Callback method invoked when the JPEG data of a picture is ready.
	 */
    public void onPictureTaken(final byte[] data, final Camera camera) {
        mCallback.onPictureTaken(data);
	}

    /***
     * Stop this CameraThread.
     * The caller can then call {@link #join()} to wait for actual termination.
     */
	public void terminate() {
        mHandler.post(new Runnable() {
            public void run() {
            	terminate_Impl();
            }
        });
    }

    private void terminate_Impl() {
        mCameraState = CAMERA_INACTIVE;
        // I'm not sure the following three consecutive try-catch blocks are really needed...
        try {
            mCamera.stopPreview();
        } catch (Throwable e) {
        }
        try {
            mCamera.cancelAutoFocus();
        } catch (Throwable e) {
        }
        try {
            mCamera.release();
        } catch (Throwable e) {
        }
        mCamera = null;

        quit();
    }
}
