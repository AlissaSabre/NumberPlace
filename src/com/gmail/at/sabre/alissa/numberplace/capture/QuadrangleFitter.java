package com.gmail.at.sabre.alissa.numberplace.capture;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;

/***
 * Provides a static method to fit a set of points to a quadrangle.
 * @author alissa
 */
public abstract class QuadrangleFitter {
	
	/***
	 * Find a quadrangle that the given set of points are close to its
	 * edges.  The method works for 2D points (as opposed to 3D
	 * points.)
	 * 
	 * @param contour
	 *             A set of points, as returned by
	 *             {@link Imgproc.findContours(Mat, List<MatOfPoint>, Mat, int, int)}.
	 *             It is not modified.
	 * @return
	 *             An array of four points that define a quadrangle.
	 */
	public static final Point[] fit(MatOfPoint contour) {

		final Point[] array = contour.toArray();
		if (array.length < 4) throw new IllegalArgumentException("Four or more points are needed.");

		// Find the two most distant points in the set.  We
		// tentatively assume the two points are a diagonal pair of
		// corners of the resulting quadrangle
		final Point[] pair1 = mostDistantPoints(array);
		
		// For each side of the tentative diagonal line, find a most
		// distant point from the tentative diagonal line.  The new
		// two points are tentatively assumed to be another pair of
		// diagonal corners.
		final Point[] pair2 = mostDistantPointsFromLine(array, pair1);
		
		// NOTE that the above strategy doesn't work well if the given
		// contour formed quadrangle whose one edge is very long
		// relative to other three edges, because the two most distant
		// points are two ends of an edge, as opposed to diagonal
		// points, and two other corner points are on a same side of
		// the line connecting the first two points.  For example,
		// consider the following trapezoid: (0,0)-(10,0)-(4,1)-(3,1).
		// For our purpose, however, such contour is not important,
		// and we ignore the case.

		// The following four points from the set (in the order) are
		// the tentative corners of quadrangle.
		final Point[] result = new Point[4];
		result[0] = pair1[0];
		result[1] = pair2[0];
		result[2] = pair1[1];
		result[3] = pair2[1];
		
		// The four corner points should be stored in the _right_
		// order, that is: upper left, upper right, lower right, then
		// lower left.
		reorderPoints(result);
		
		return result;
	}
	
	/***
	 * Find two points that are most distant in the given set of 2D
	 * points.
	 * 
	 * @param array
	 *            The set of 2D points.  This array is not modified.
	 * @return
	 *            An array of length two containing the two points
	 *            that are most distant.
	 */ 
	private static Point[] mostDistantPoints(Point[] array) {

		// Try all combinations of two points in the given set.
		double max = -Double.MAX_VALUE;
		Point p = null, q = null;
		for (int i = 0; i < array.length; i++) {
			for (int j = i + 1; j < array.length; j++) {
				final double d = dist2(array[i], array[j]);
				if (d > max) {
					max = d;
					p = array[i];
					q = array[j];
				}
			}
		}

		// Return the found pair in an array.
		return new Point[] { p, q };
	}
	
	/***
	 * Find two points in the given set, one point for each side of a
	 * line, that are most distant from the line.
	 * 
	 * @param array
	 *             The set of 2D points.  This array is not modified.
	 * @param pair
	 *             A pair of points.  This method works on the line
	 *             that connects the two points.  This array is not
	 *             modofied.
	 * @return
	 *             An array of length two containing the two points
	 *             that are most distant from the line on each side.
	 */
	private static Point[] mostDistantPointsFromLine(Point[] array, Point[] pair) {

		final Point p = pair[0];
		final Point q = pair[1];

		// We use an expression d(x, y) = a * x + b * y + c, where x
		// and y are a coordinate of a point, a, b, and c are
		// constant.  The forumla d(x, y) == 0 defines a line.
		// Call it L.
		// 
		// If a point p = (x, y) is on the line L, d(x, y) is zero.
		// If the point p is on one side of the line L, d(x, y) gives
		// the distance between the point p and the line L.  If the
		// point p in on another side of the line L, d(x, y) gives a
		// negative value whose absolute value is the distance between
		// the point p and the line L.

		// First, we find the three constant a, b, and c, where d(p)
		// and d(q) are both zero.  That means the line defined by the
		// formula d(x, y) == 0 is the line connecting two points p
		// and q.  EXCERSISE: Verify that the line defined by d(x, y) == 0
		// for the following a, b, c connects p and q. :-)
		final double a = p.y - q.y;
		final double b = q.x - p.x;
		final double c = p.x * q.y - q.x * p.y;

		// Find two points that give maximum (a positive) and minimum
		// (a negative) values for the expression d.  They are the
		// most distant points from the line on each side.
		Point s = null, t = null;
		double max = -Double.MAX_VALUE, min = Double.MAX_VALUE;
		for (int i = 0; i < array.length; i++) {
			final Point r = array[i];
			final double d = a * r.x + b * r.y + c;
			if (d > max) {
				s = r;
				max = d;
			}
			if (d < min) {
				t = r;
				min = d;
			}
		}

		return new Point[] { s, t };
	}

	/***
	 * Reorder the array of four points that define a quadrangle so
	 * that the points are on a right order, i.e., upper left, upper
	 * right, lower right, then lower left.
	 */
	private static void reorderPoints(Point[] points) {

		// Find the upper left corner, assuming it is the one most
		// close to the origin.  (i.e., the original image's upper
		// left corner.)
		int w = 0;
		double d = Double.MAX_VALUE; 
		for (int i = 0; i < points.length; i++) {
			final Point p = points[i];
			final double e = p.x * p.x + p.y * p.y;
			if (e < d) {
				d = e;
				w = i;
			}
		}
		
		// Make the upper left corner the first point by shifting
		// elements in the array.
		if (w > 0) {
			final Point[] temp = new Point[w];
			System.arraycopy(points, 0, temp, 0, w);
			System.arraycopy(points, w, points, 0, points.length - w);
			System.arraycopy(temp, 0, points, points.length - w, w);
		}
		
		// Use cross products of first few (two, actually) edges to
		// see whether the points are in clockwise.
		final double x0 = points[0].x - points[1].x;
		final double y0 = points[0].y - points[1].y;
		final double x1 = points[1].x - points[2].x;
		final double y1 = points[1].y - points[2].y;
		final double cross = x0 * y1 - y0 * x1;

		// Be careful when interpreting signs of cross products to
		// note that our coordinate system is flipped from the
		// standard mathematical planar systems in that the Y axis
		// grows toward bottom.  This alert is to myself! :-(
		if (cross < 0) {
			// Reorder in reverse _wise_
			for (int i = 1, j = points.length - 1; i < j; i++, --j) {
				final Point t = points[i];
				points[i] = points[j];
				points[j] = t;
			}
		}
	}
	
	/***
	 * Square of the distance of two points.  The calculation is
	 * faster than an ordinary distance...
	 */
	private static double dist2(Point p1, Point p2) {
		final double x = p1.x - p2.x;
		final double y = p1.y - p2.y;
		return x * x + y * y;
	}

}
