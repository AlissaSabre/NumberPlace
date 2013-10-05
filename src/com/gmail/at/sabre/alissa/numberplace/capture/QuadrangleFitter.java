package com.gmail.at.sabre.alissa.numberplace.capture;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;

public abstract class QuadrangleFitter {
	public static final Point[] fit(MatOfPoint contour) {
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
		
		// find the most distant point from the line p-q at an opposite side of
		// s (against line p-q) as t.
		// NOTE this doesn't work the given contour formed a very flat trapezoid
		// with very different lengths parallel edges. For our purpose, such
		// contour is not important, and we ignore the case.
		mostDistantOppositePointFromLine(array, p, q, s, result);
		Point t = result[0];
		
		// The following four points (in the order) are the
		// most likely corners of quadrangle.
		result[0] = p;
		result[1] = s;
		result[2] = q;
		result[3] = t;
		
		// The four corner points should be stored in the _right_ order,
		// that is: upper left, upper right, lower right, then lower left.
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
		// Find the right upper left corner,
		// assuming it is the one most close to the origin.
		// (i.e., the original image's upper left corner.)
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
		
		// Make the upper right corner the first point in the array.
		if (w > 0) {
			Point[] temp = new Point[w];
			System.arraycopy(points, 0, temp, 0, w);
			System.arraycopy(points, w, points, 0, points.length - w);
			System.arraycopy(temp, 0, points, points.length - w, w);
		}
		
		// Use cross products of first few (two, actually) edges to
		// see whether the points are in clockwise.
		double x0 = points[0].x - points[1].x;
		double y0 = points[0].y - points[1].y;
		double x1 = points[1].x - points[2].x;
		double y1 = points[1].y - points[2].y;
		double cross = x0 * y1 - y0 * x1;
		// Be careful when interpreting signs of cross products to note that our
		// coordinate system is flipped from the standard mathematical planar
		// systems in that the Y axis grows toward bottom.
		// [[This alert is to myself! :-(]]
		if (cross < 0) {
			// Reorder in reverse _wise_
			for (int i = 1, j = points.length - 1; i < j; i++, --j) {
				Point t = points[i];
				points[i] = points[j];
				points[j] = t;
			}
		}
	}
	
	/***
	 * Square of the distance of two points.
	 * @param p1
	 * @param p2
	 * @return
	 */
	private static double dist2(Point p1, Point p2) {
		final double x = p1.x - p2.x;
		final double y = p1.y - p2.y;
		return x * x + y * y;
	}

}
