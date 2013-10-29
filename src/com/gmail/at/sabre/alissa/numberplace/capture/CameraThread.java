package com.gmail.at.sabre.alissa.numberplace.capture;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import android.annotation.SuppressLint;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.SurfaceHolder;

/***
 * The thread that accesses the camera device.
 * All public methods of this class are intended to be called by the UI thread.
 * It then uses {@link Handler} to execute the requested actions in this thread.
 *
 * @author alissa
 */
public class CameraThread extends HandlerThread {

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

	private final CameraActivity mActivity;

	private final SurfaceHolder mHolder;

    private Camera mCamera;

    private boolean mAutoFocusRequired;

    public CameraThread(final CameraActivity activity, final SurfaceHolder holder) {
        super(CameraThread.class.getName());
        mActivity = activity;
        mHolder = holder;
    }

    @Override
    public synchronized void start() {
        super.start();
        mHandler = new Handler(getLooper());
    }

    public void initialize() {
        mHandler.post(new Runnable() {
            public void run() {
            	initialize_Impl();
            }
        });
    }

    private void initialize_Impl() {

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

        // Next, take care of focus mode.
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

        // Pass the decided preview size to UI thread so that it can
        // resize the surface view appropriately.
        final int pw = preview.width;
        final int ph = preview.height;
        mActivity.onPreviewSizeDecided(pw, ph);
    }

    public void startPreview() {
        mHandler.post(new Runnable() {
            public void run() {
            	startPreview_Impl();
            }
        });
    }

    private void startPreview_Impl() {
        try {
            mCamera.setPreviewDisplay(mHolder);
        } catch (IOException e) {
            // We can't capture if this happened...
            // TODO: this event should be notified to user, too.
            throw new RuntimeException("Can't preview camera", e);
        }
        mCamera.startPreview();
    }

    public void focus() {
    	mHandler.post(new Runnable() {
    		public void run() {
    			focus_Impl();
    		}
    	});
    }

    private void focus_Impl() {
    	if (mAutoFocusRequired) {
    		mCamera.autoFocus(new Camera.AutoFocusCallback() {
				public void onAutoFocus(boolean success, Camera camera) {
				}
			});
    	}
    }

    public void unlockFocus() {
    	mHandler.post(new Runnable() {
			public void run() {
				unlockFocus_Impl();
			}
		});
    }

    private void unlockFocus_Impl() {
    	try {
    		mCamera.cancelAutoFocus();
    	} catch (Throwable e) {
    	}
    }

    public void focusAndShoot() {
        mHandler.post(new Runnable() {
            public void run() {
            	focusAndShoot_Impl();
            }
        });
    }

    private void focusAndShoot_Impl() {
        // I've heard a rumor that in some device, invoking Camera.autoFocus
        // when not in MACRO/AUTO focus mode throws an Exception, though it appears
        // contrary to the Android API document.
    	// If it is true, We can't simply call it always.
        if (mAutoFocusRequired) {
            mCamera.autoFocus(new Camera.AutoFocusCallback() {
                public void onAutoFocus(boolean success, Camera camera) {
                	shoot();
                }
            });
        } else {
        	shoot_Impl();
        }
    }

    public void shoot() {
    	mHandler.post(new Runnable() {
            public void run() {
            	shoot_Impl();
            }
        });
    }

    private void shoot_Impl() {
        mCamera.takePicture(new Camera.ShutterCallback() {
            public void onShutter() {
            }
        },
        null,
        new Camera.PictureCallback() {
            // This callback is for JPEG, by the way.
            public void onPictureTaken(final byte[] data, final Camera camera) {
                mActivity.onPictureTaken(data);
            }
        });
    }

    public void terminate() {
        mHandler.post(new Runnable() {
            public void run() {
            	terminate_Impl();
            }
        });
    }

    private void terminate_Impl() {
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
