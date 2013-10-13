package com.gmail.at.sabre.alissa.numberplace.capture;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;

import com.gmail.at.sabre.alissa.ocr.Ocr;

/***
 * The thread that recognize a puzzle board. This class only contains a driver
 * and some glue between Android and OpenCV. The actual recognition algorithm is
 * in a separate class {@link ImageProcessing}.
 *
 * @author alissa
 *
 */
class CaptureWorkerThread extends Thread {

	public interface Callback {
		public void onPuzzleRecognized(byte[][] puzzle, Bitmap bitmap);
	}

	private Callback mCallback = null;

	private Ocr mOcr = null;

	private Bitmap mBitmap = null;

	private int mRotationHint = 0;

	private final Object mLock = new Object();

	private volatile boolean mQuit = false;

	/***
	 * Specify a callback that is invoked when a recognition is complete.
	 * @param callback
	 */
	public void setCallback(Callback callback) {
		mCallback = callback;
	}

	/***
	 * Start this thread if it has not been started. Do nothing if it has.
	 */
	public void startWorking() {
		if (getState() == State.NEW) {
			start();
		}
	}

	/***
	 * Specify the OCR engine to use.  There is no default OCR engine.
	 * This method must be called before {@link #setBitmap(Bitmap)}.
	 *
	 * @param ocr An OCR engine.
	 */
	public void setOcr(Ocr ocr) {
		mOcr = ocr;
	}

	/***
	 * Specify the Bitmap image data to recognize.
	 *
	 * @param bitmap
	 */
	public void setBitmap(Bitmap bitmap) {
		synchronized (mLock) {
			mBitmap = bitmap;
			mLock.notifyAll();
		}
	}

	/***
	 * Set the hint about the orientation of bitmap image data.
	 * <p>
	 * If the caller can somehow guess how much puzzle board is rotated in the
	 * bitmap image data, pass the information with this method. The rotation is
	 * how much the puzzle board is rotated counterclockwise. When the puzzle board is
	 * just up right in the image, the rotation is zero. When the puzzle board
	 * is totally on its side, and the image's left side is the puzzle board's
	 * top, the rotation is 90.
	 * <p>
	 * This is only a hint. The caller need not to call this method.
	 *
	 * @param rotation
	 *            Rotation of the puzzle in the bitmap image. It is measured
	 *            clockwise in degrees and is a zero or positive value.
	 */
	public void setBitmapRotationHint(int rotation) {
		mRotationHint = rotation;
	}

	/***
	 * Request this thread to stop. It may take some time before the thread to
	 * actually stop.
	 */
	public void quit() {
		// Unless it is waiting a lock, this thread can't stop immediately. As a
		// workaround we remove callback so that the main activity doesn't notice
		// this thread kept working.
		synchronized (mLock) {
			mCallback = null;
			mQuit = true;
			mLock.notifyAll();
		}
	}

	/***
	 * This method is called back by the runtime system as usual.
	 */
	@Override
	public void run() {
		// Wait until a bitmap to analyze is prepared.
		try {
			synchronized (mLock) {
				while (mBitmap == null && !mQuit) mLock.wait();
			}
		} catch (InterruptedException e) {
			// Note this can happen under normal course of operation, e.g., if
			// a user pressed the BACK button on the camera activity.
			return;
		}

		Bitmap captured = null;
		byte[][] puzzle = null;
		if (mBitmap != null) {
			puzzle = new byte[9][9];
			captured = recognize(mOcr, mBitmap, mRotationHint, puzzle);
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

	private static Bitmap recognize(Ocr ocr, Bitmap src_bitmap, int rotation_hint, byte[][] puzzle) {
		if (src_bitmap.getConfig() != Config.RGB_565 &&
			src_bitmap.getConfig() != Config.ARGB_8888) return null;

		final Mat src = new Mat();
		Utils.bitmapToMat(src_bitmap, src);

		final Mat dst = new Mat();
		final boolean success = ImageProcessing.recognize(ocr, src, dst, rotation_hint, puzzle);

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
