package com.gmail.at.sabre.alissa.numberplace.capture;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;

class CaptureWorkerThread extends Thread {
	
	private static final String TAG = ".numberplace..CaptureWorkerThread";
	
	public interface Callback {
		public void onPuzzleRecognized(byte[][] puzzle, Bitmap bitmap);
	}
	
	private Callback mCallback = null;
	
	private Bitmap mBitmap = null;
	
	private final Object mLock = new Object();
	
	private volatile boolean mQuit = false; 
	
	public void setCallback(Callback callback) {
		mCallback = callback;
	}
	
	public void startWorking() {
		if (getState() == State.NEW) {
			start();
		}
	}
	
	public void setBitmap(Bitmap bitmap) {
		synchronized (mLock) {
			mBitmap = bitmap;
			mLock.notifyAll();
		}
	}
	
	public void quit() {
		synchronized (mLock) {
			mQuit = true;
			mLock.notifyAll();
		}
	}

	@Override
	public void run() {
		// Wait until a bitmap to analyze is prepared.
		try {
			synchronized (mLock) {
				while (mBitmap == null && !mQuit) mLock.wait();
			}
		} catch (InterruptedException e) {
			return; // XXX
		}
		
		Bitmap puzzle = null;
		// try {
			if (mBitmap != null) {
				puzzle = recognize(mBitmap);
			}
		// } catch (Throwable e) {
		//	Log.w(TAG, e.toString());
		//}
		
		Callback callback = mCallback;
		if (callback != null) {
			callback.onPuzzleRecognized(null, puzzle);
		}
	}
	
//	private static byte[][] recognize(Bitmap src_bitmap) throws Exception {
//		Thread.sleep(3000);
//		
//		// Process image and recognize a puzzle board here...
//		final byte[][] puzzle = new byte[][] {
//				{ 2, 1, 0, 4, 0, 0, 0, 3, 6 },
//				{ 8, 0, 0, 0, 0, 0, 0, 0, 5 },
//				{ 0, 0, 5, 3, 0, 9, 8, 0, 0 },
//				{ 6, 0, 4, 9, 0, 7, 1, 0, 0 },
//				{ 0, 0, 0, 0, 3, 0, 0, 0, 0 },
//				{ 0, 0, 7, 5, 0, 4, 6, 0, 2 },
//				{ 0, 0, 6, 2, 0, 3, 5, 0, 0 },
//				{ 5, 0, 0, 0, 0, 0, 0, 0, 9 },
//				{ 9, 3, 0, 0, 0, 5, 0, 2, 7 },	
//		};
//		
//		return puzzle;
//	}

	private static Bitmap recognize(Bitmap src_bitmap) {
		if (src_bitmap.getConfig() != Config.RGB_565 &&
			src_bitmap.getConfig() != Config.ARGB_8888) return null;
		
		Mat src = new Mat();
		Utils.bitmapToMat(src_bitmap, src);
		
		Mat dst = new Mat();
		ImageProcessing.recognize(src, dst);
		
		Bitmap dst_bitmap = Bitmap.createBitmap(dst.width(), dst.height(), Config.ARGB_8888);
		Utils.matToBitmap(dst, dst_bitmap);
		
		src.release();
		dst.release();

		return dst_bitmap;
	}
}
