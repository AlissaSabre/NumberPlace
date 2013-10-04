package com.gmail.at.sabre.alissa.numberplace.capture;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class ImageProcessing {
	public static byte[][] recognize(Mat source, Mat result) {
		Mat src, dst, tmp;
		src = source.clone();
		dst = new Mat();
		
		Imgproc.GaussianBlur(src, src, new Size(5, 5), 0f, 0f);
		
		Imgproc.cvtColor(src, dst, Imgproc.COLOR_RGBA2GRAY);
		tmp = src; src = dst; dst = tmp; tmp = null;
		
		int blockSize = (Math.min(src.width(), src.height()) / 16) | 1; // make the blocksize odd so that a block has a center pixel.
		Imgproc.adaptiveThreshold(src, src, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, blockSize, 2);

		src.copyTo(result);
		src.release();
		dst.release();
		
		return null;
	}
}
