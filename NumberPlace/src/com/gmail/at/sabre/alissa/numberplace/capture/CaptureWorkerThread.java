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
        public void onPuzzleRecognized(byte[][] puzzle);
    }

    private Callback mCallback = null;

    private Ocr mOcr = null;

    private Bitmap mBitmap = null;

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
     * This method is called back by the Thread runtime system as usual.
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
            // a user pressed a BACK button on the capture activity.
            return;
        }
        if (mQuit) return;

        // A bitmap is ready. Try recognizing a puzzle on it.
        byte[][] puzzle = new byte[9][9];
        boolean captured = recognize(mOcr, mBitmap, puzzle);
        if (!captured) puzzle = null;

        // Invoke a callback.
        Callback callback = mCallback;
        if (callback != null) {
            callback.onPuzzleRecognized(puzzle);
        }
    }

    /***
     * Convert an Android bitmap image to an OpenCV Mat image,
     * then call {@link ImageProcessing#recognize(Ocr, Mat, byte[][])}
     * to recognize a puzzle on the image.
     * We make the image format conversion here (not in {@link ImageProcessing})
     * so that it is independent from Android specific classes and
     * can be run on any platform/framework.
     *
     * @param ocr
     *            An OCR engine
     * @param src_bitmap
     *            A source bitmap image in {@link Config#RGB_565} or {@link Config#ARGB_8888}.
     * @param puzzle
     *            A 9x9 array to receive a recognized puzzle
     * @return
     *            True if a puzzle is successfully recognized
     */
    private static boolean recognize(Ocr ocr, Bitmap src_bitmap, byte[][] puzzle) {
        if (src_bitmap.getConfig() != Config.RGB_565 &&
            src_bitmap.getConfig() != Config.ARGB_8888) return false; // Just in case

        final Mat src = new Mat();
        Utils.bitmapToMat(src_bitmap, src);
        // I thought I can call src_bitmap.recycle() here, but it was wrong.
        // The src_bitmap is shared with CaptureActivity, and the activity
        // shows the bitmap on the screen when this thread is working.
        // If we recycled it, the ImageView in CaptureActivity would crush
        // when it needs to redraw the screen.
        final boolean success = ImageProcessing.recognize(ocr, src, puzzle);
        src.release();

        return success;
    }

}
