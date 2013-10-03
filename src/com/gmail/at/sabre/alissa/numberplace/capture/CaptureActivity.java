package com.gmail.at.sabre.alissa.numberplace.capture;

import java.io.IOException;

import com.gmail.at.sabre.alissa.numberplace.K;
import com.gmail.at.sabre.alissa.numberplace.R;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView.ScaleType;

public class CaptureActivity extends Activity implements SurfaceHolder.Callback {
	
	private static final int[] sShootButtons = {
		R.id.button_shoot_0,
		R.id.button_shoot_90,
		R.id.button_shoot_180,
		R.id.button_shoot_270,
	};
	
	private SurfaceHolder mHolder;
	
	private Camera mCamera;
	
	private int mDeviceRotationDegree;
	
	private Handler mHandler;
	
	private CaptureWorkerThread mWorker;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture);

        findViewById(R.id.busy).setVisibility(View.INVISIBLE);
        for (int i = 0; i < sShootButtons.length; i++) {
        	findViewById(sShootButtons[i]).setVisibility(View.INVISIBLE);
        }
        
        Intent data = getIntent();
        mDeviceRotationDegree = rotationToDegree(data.getIntExtra(K.DEVICE_ROTATION, -1));
        
        SurfaceView v = (SurfaceView)findViewById(R.id.surfaceView);
        mHolder = v.getHolder();
        // Although Google deprecated SURFACE_TYPE_PUSH_BUFFERS
        // and lint alerts on it, the call is absolutely
        // needed on Android devices before API 11 (3.0).
        // We can't live without one.
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mHolder.addCallback(this);
        
        mHandler = new Handler();
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

	private void initializeShootButton() {
        for (int i = 0; i < sShootButtons.length; i++) {
        	findViewById(sShootButtons[i]).setVisibility(View.INVISIBLE);
        }
		int id = R.id.button_shoot_0, r = 0;
		int view_rotation_degree = rotationToDegree(getWindowManager().getDefaultDisplay().getRotation());
		switch ((mDeviceRotationDegree - view_rotation_degree + 360) % 360) {
	    	case 0:   id = R.id.button_shoot_0;   r = 0;   break;
	    	case 90:  id = R.id.button_shoot_90;  r = 90;  break;
	    	case 180: id = R.id.button_shoot_180; r = 180; break;
	    	case 270: id = R.id.button_shoot_270; r = 270; break;
		}
		ImageButton b = (ImageButton)findViewById(id);
		Rect d = b.getDrawable().getBounds();
		Matrix m = new Matrix();
		m.setRotate(r, d.centerX(), d.centerY());
		b.setImageMatrix(m);
		b.setScaleType(ScaleType.MATRIX);
		b.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				buttonShoot_onClick(v);
			}
		});
		b.setVisibility(View.VISIBLE);
    }

	public void surfaceCreated(final SurfaceHolder holder) {
        mWorker = new CaptureWorkerThread();
        mWorker.start();
        
        initializeShootButton();
        
        try {
        	mWorker.waitUntilReady();
        } catch (InterruptedException e) {
        	throw new RuntimeException(e);
        }
		
		mWorker.post(new Runnable() {
			public void run() {
		        mCamera = Camera.open();
		    	if (mCamera == null) {
		    		throw new RuntimeException("Can't open the default camera");
		    	}
				try {
					mCamera.setPreviewDisplay(holder);
				} catch (IOException e) {
					// We can't capture if this happened...
					throw new RuntimeException("Can't preview camera", e);
				}
			}
		});
	}
    
	public void surfaceChanged(final SurfaceHolder holder, final int format, final int width, final int height) {
		mWorker.post(new Runnable() {
			public void run() {
				mCamera.startPreview();
			}
		});
	}

	public void surfaceDestroyed(final SurfaceHolder holder) {
		mWorker.post(new Runnable() {
			public void run() {
				mCamera.stopPreview();
				mCamera.release();
				mCamera = null;
				
				mWorker.quit();
			}
		});
		
		try {
			mWorker.join();
		} catch (InterruptedException e) {
			// I don't think we need to take care of the case.
		}
		mWorker = null;
	}

	private void buttonShoot_onClick(View view) {
		findViewById(R.id.busy).setVisibility(View.VISIBLE);
		
		mWorker.post(new Runnable() {
			public void run() {
				mCamera.takePicture(null, null, null, new Camera.PictureCallback() {
					public void onPictureTaken(final byte[] data, Camera camera) {
						camera_onPictureTaken(data, camera);
					}
				});
			}
		});
	}
	
	private void camera_onPictureTaken(byte[] data, Camera camera) {
		// Process image and recognize a puzzle board here...
		final byte[][] puzzle = new byte[][] {
				{ 2, 1, 0, 4, 0, 0, 0, 3, 6 },
				{ 8, 0, 0, 0, 0, 0, 0, 0, 5 },
				{ 0, 0, 5, 3, 0, 9, 8, 0, 0 },
				{ 6, 0, 4, 9, 0, 7, 1, 0, 0 },
				{ 0, 0, 0, 0, 3, 0, 0, 0, 0 },
				{ 0, 0, 7, 5, 0, 4, 6, 0, 2 },
				{ 0, 0, 6, 2, 0, 3, 5, 0, 0 },
				{ 5, 0, 0, 0, 0, 0, 0, 0, 9 },
				{ 9, 3, 0, 0, 0, 5, 0, 2, 7 },	
		};
		
		mHandler.postDelayed(new Runnable() {
			public void run() {
				Intent result = new Intent();
				result.putExtra(K.PUZZLE_DATA, puzzle);
				setResult(RESULT_OK, result);
				finish();    	
			}
		}, 3000);
    }

	private class CaptureWorkerThread extends HandlerThread {
		protected Handler mHandler;
		
		private boolean mReady = false;
		
		private Object mLock = new Object();
		
		public CaptureWorkerThread() {
			super(CaptureWorkerThread.class.getName());
		}

		@Override
		protected void onLooperPrepared() {
			super.onLooperPrepared();
			mHandler = new Handler();
			mHandler.post(new Runnable() {
				public void run() {
					synchronized (mLock) {
						mReady = true;
						mLock.notifyAll();
					}
				}
			});
		}
		
		/***
		 * The calling thread is blocked until this CaptureWorkerThread 
		 * is fully initialized and the underlying looper has started.
		 * @throws InterruptedException 
		 */
		public void waitUntilReady() throws InterruptedException {
			if (!mReady) {
				synchronized (mLock) {
					while (!mReady) {
						mLock.wait();
					}
				}
			}
		}
		
		public boolean post(Runnable r) {
			return mHandler.post(r);
		}
	}
}
