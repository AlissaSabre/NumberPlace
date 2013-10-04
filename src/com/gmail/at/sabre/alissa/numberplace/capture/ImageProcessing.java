package com.gmail.at.sabre.alissa.numberplace.capture;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
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
		
		tmp = src.clone();
		List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
		Mat hierarchy = new Mat(); // will be 1*n*CV_32C4
		Imgproc.findContours(tmp, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);
		tmp.release();
		tmp = null;
		
		// Find blobs that *may* be number place puzzle frame.  I.e., 
		// (1) the blob has at least nine and at most 81 holes, and
		// (2) the blob covers the center of the image.
		List<Integer> candidates = new ArrayList<Integer>();
		int[] hinfo = new int[4];
		Point center = new Point(source.width() / 2f, source.height() / 2f);
		for (int i = 0; i < contours.size(); i++) {
			hierarchy.get(0, i, hinfo);
			if (hinfo[3] >= 0) {
				// this is an internal contour (a contour of a hole) of a blob.
				continue;
			}
			// count how many holes this blob has.
			int holes = 0;
			for (int p = hinfo[2]; p >= 0; p = hinfo[0]) {
				++holes;
				hierarchy.get(0, p, hinfo);
			}
			if (holes < 9 || holes > 81) {
				continue;
			}
			// see if it covers the center
			//if (Imgproc.pointPolygonTest(new MatOfPoint2f(contours.get(i)), center, false) >= 0f) {
			if (Imgproc.boundingRect(contours.get(i)).contains(center)) {
				candidates.add(i);
			}
		}

		// If we have no candidates, give up.
		// If we have two or more candidates, take the first one, praying!
		if (candidates.size() == 0) return null;
		
		// Fit the contour to a quadrangle
		Point[] quad = fitQuadrangle(contours.get(candidates.get(0)));

		
		
		
		
		Point[] goal = new Point[] { new Point(80, 80), new Point(800, 80), new Point(800, 800), new Point(80, 800) };
		Mat pers = Imgproc.getPerspectiveTransform(new MatOfPoint2f(quad), new MatOfPoint2f(goal));
		dst.release();
		dst = new Mat(880, 880, source.type());
		Imgproc.warpPerspective(source, dst, pers, dst.size());
		Core.rectangle(dst, new Point(80, 80), new Point(800, 800), new Scalar(255, 0, 0, 255));
		tmp = src; src = dst; dst = tmp; tmp = null;

		
		
		
		src.copyTo(result);
		src.release();
		dst.release();
		
		return null;
	}
	
	private static Point[] fitQuadrangle(MatOfPoint contour) {
		Point[] array = contour.toArray();
		if (array.length < 4) return null;
		if (array.length == 4) return array;
		
		Point[] result = new Point[4];

		// find the two most distant points in array as p and q.
		twoDistantPoints(array, result);
		Point p = result[0];
		Point q = result[1];
		
		// find the most distant point from the line p-q as s.
		mostDistantPointFromLine(array, p, q, result);
		Point s = result[0];
		
		// find the most distant point from the line p-q at an opposite side of s (against line p-q) as t
		// NOTE this doesn't work the given contour forms a very flat trapezoid with 
		// very different lengths parallel edges.  For our purpose, such contour is not important, and
		// we ignore the case.
		mostDistantOppositePointFromLine(array, p, q, s, result);
		Point t = result[0];
		
		result[0] = p;
		result[1] = s;
		result[2] = q;
		result[3] = t;
		
		// Our caller wants the four corner points are stored in the _right_ order,
		// that is, upper left, upper right, lower right, then lower left.
		reorderPoints(result);
		return result;
	}
	
	private static void twoDistantPoints(Point[] array, Point[] result) {
		Point p = array[0];
		Point q = p;
		double d0 = 0;
		while (true) {
			double d = 0;
			for (int i = 0; i < array.length; i++) {
				double e = dist2(p, array[i]);
				if (e > d) {
					q = array[i];
					d = e;
				}
			}
			Point r = new Point((p.x + q.x) / 2, (p.y + q.y) / 2);
			d = 0;
			Point s = p;
			for (int i = 0; i < array.length; i++) {
				double e = dist2(r, array[i]);
				if (e > d) {
					s = array[i];
					d = e;
				}
			}
			if (d <= d0) break;
			d0 = d;
			p = s;
		}
		result[0] = p;
		result[1] = q;
	}
	
	private static void mostDistantPointFromLine(Point[] array, Point p, Point q, Point[] result) {
		double a = p.y - q.y;
		double b = -p.x + q.x;
		double c = p.x * q.y - p.y * q.x;
		Point s = p;
		double d = 0;
		for (int i = 0; i < array.length; i++) {
			Point t = array[i];
			double e = Math.abs(a * t.x + b * t.y + c);
			if (e > d) {
				s = t;
				d = e;
			}
		}
		result[0] = s;
	}
	
	private static void mostDistantOppositePointFromLine(Point[] array, Point p, Point q, Point r, Point[] result) {
		double a = p.y - q.y;
		double b = -p.x + q.x;
		double c = p.x * q.y - p.y * q.x;
		if (a * r.x + b * r.y + c > 0) {
			a = -a;
			b = -b;
			c = -c;
		}
		Point s = p;
		double d = 0;
		for (int i = 0; i < array.length; i++) {
			Point t = array[i];
			double e = a * t.x + b * t.y + c;
			if (e > d) {
				s = t;
				d = e;
			}
		}
		result[0] = s;
	}

	private static void reorderPoints(Point[] points) {
		int w = 0;
		double d = Double.MAX_VALUE; 
		for (int i = 0; i < points.length; i++) {
			Point p = points[i];
			double e = p.x * p.x + p.y * p.y;
			if (e < d) {
				d = e;
				w = i;
			}
		}
		if (w > 0) {
			Point[] temp = new Point[w];
			System.arraycopy(points, 0, temp, 0, w);
			System.arraycopy(points, w, points, 0, points.length - w);
			System.arraycopy(temp, 0, points, points.length - w, w);
		}
		double x0 = points[0].x - points[1].x;
		double x1 = points[1].x - points[2].x;
		double y0 = points[0].y - points[1].y;
		double y1 = points[1].y - points[2].y;
		double cross = x0 * y1 - y0 * x1;
		if (cross < 0) {
			// Remember that our coordinate system is flipped
			// (from the standard mathematical planar system)
			// in that the Y axis grows toward bottom.
			for (int i = 1, j = points.length - 1; i < j; i++, --j) {
				Point t = points[i];
				points[i] = points[j];
				points[j] = t;
			}
		}
	}
	
	private static double dist2(Point p1, Point p2) {
		final double x = p1.x - p2.x;
		final double y = p1.y - p2.y;
		return x * x + y * y;
	}
}
