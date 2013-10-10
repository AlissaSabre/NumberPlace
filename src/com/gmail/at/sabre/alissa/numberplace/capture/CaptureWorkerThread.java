package com.gmail.at.sabre.alissa.numberplace.capture;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;

import com.gmail.at.sabre.alissa.ocr.Ocr;

class CaptureWorkerThread extends Thread {
	
	private static final String TAG = ".numberplace..CaptureWorkerThread";
	
	public interface Callback {
		public void onPuzzleRecognized(byte[][] puzzle, Bitmap bitmap);
	}
	
	private Callback mCallback = null;
	
	private Ocr mOcr = null;
	
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
	
	public void setOcr(Ocr ocr) {
		mOcr = ocr;
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
		
		Bitmap captured = null;
		byte[][] puzzle = null;
		if (mBitmap != null) {
			puzzle = new byte[9][9];
			captured = recognize(mOcr, mBitmap, puzzle);
			if (captured == null) puzzle = null;
		}
		
		Callback callback = mCallback;
		if (callback != null) {
			callback.onPuzzleRecognized(puzzle, captured);
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

	private static Bitmap recognize(Ocr ocr, Bitmap src_bitmap, byte[][] puzzle) {
		if (src_bitmap.getConfig() != Config.RGB_565 &&
			src_bitmap.getConfig() != Config.ARGB_8888) return null;
		
		final Mat src = new Mat();
		Utils.bitmapToMat(src_bitmap, src);
		
		final Mat dst = new Mat();
		final boolean success = ImageProcessing.recognize(ocr, src, dst, puzzle);
		
		Bitmap dst_bitmap = null;
		if (success) {
			dst_bitmap = Bitmap.createBitmap(dst.width(), dst.height(), Config.ARGB_8888);
			Utils.matToBitmap(dst, dst_bitmap);
		}
		
		src.release();
		dst.release();

		return dst_bitmap;
	}
}
