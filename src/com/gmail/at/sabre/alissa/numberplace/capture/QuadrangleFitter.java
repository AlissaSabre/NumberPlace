package com.gmail.at.sabre.alissa.numberplace.capture;

import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

/***
 * Provides a static method to fit a contour to a quadrangle.
 *
 * @author alissa
 */
public abstract class QuadrangleFitter {

	/***
	 * Find a quadrangle that the given contour (a series of points) are close
	 * to its edges. The method works for 2D points (as opposed to 3D points.)
	 *
	 * @param contour
	 *            A set of points, as returned by {@link
	 *            Imgproc.findContours(Mat, List<MatOfPoint>, Mat, int, int)}.
	 *            It is not modified.
	 * @return
	 *            An array of four points that define a quadrangle.
	 */
	public static final Point[] fit(MatOfPoint contour) {

		// This method works as follows: (1) Analyze the locations of
		// the points in contour and find four points in contour that
		// are most appropriate for the four corner point of a
		// quadrangle. (2) Divide the contour into four groups of
		// points that are between two corner points detected in the
		// first step. (3) Use OpenCV line fitting algorithm on each
		// group of points to find a fit line.  It gives us four
		// lines.  (4) Build a fit quadrangle by calculating the
		// crossing points of two adjacent lines.

		final Point[] array = contour.toArray();
		if (array.length < 4) throw new IllegalArgumentException("Four or more points are needed.");

		// Find the two most distant points in the contour.  We
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
		final Point[] tentativeQuad = new Point[4];
		tentativeQuad[0] = pair1[0];
		tentativeQuad[1] = pair2[0];
		tentativeQuad[2] = pair1[1];
		tentativeQuad[3] = pair2[1];

		// Divide the contour into four groups of points, the edges.
		final MatOfPoint[] edges = divideContour(contour, tentativeQuad);

		// Use OpenCV fitLine function repeatedly to find lines that
		// fit to each edge.
		final Mat[] lines = fitLines(edges);

		// Calculate crossing points of consecutive two lines; they
		// are the corners of the fit quadrangle.
		final Point[] quadrangle = calculateCrossingPoint(lines);

		// Release Mats.
		for (int i = 0; i < edges.length; i++) edges[i].release();
		for (int i = 0; i < lines.length; i++) lines[i].release();

		// The four corner points should be stored in the _right_
		// order, that is: upper left, upper right, lower right, then
		// lower left.
		reorderPoints(quadrangle);

		return quadrangle;
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
	 *            The set of 2D points.  This array is not modified.
	 * @param pair
	 *            A pair of points.  This method works on the line
	 *            that connects the two points.  This array is not
	 *            modified.
	 * @return
	 *            An array of length two containing the two points
	 *            that are most distant from the line on each side.
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
	 * Square of the distance between two points.  The calculation is
	 * faster than an ordinary distance...
	 */
	private static double dist2(Point p1, Point p2) {
		final double x = p1.x - p2.x;
		final double y = p1.y - p2.y;
		return x * x + y * y;
	}

	/***
	 * Divide points in contour into four separate set of points based
	 * on the tentative quad corners.
	 *
	 * @param contour
	 *            The contour to divide.
	 * @param quad
	 *            Four points in the contour.
	 * @return
	 *            An array of four Mat each of which contains points
	 *            appropriate for an edge of a quadrangle.
	 */
	private static MatOfPoint[] divideContour(MatOfPoint contour, Point[] quad) {

		final Point[] points = contour.toArray();

		// Locate quad Points in pints array.
		final int[] indexes = new int[quad.length];
		for (int i = 0; i < quad.length; i++) {
			final Point p = quad[i];
			for (int j = 0; j < points.length; j++) {
				if (p.equals(points[j])) {
					indexes[i] = j;
					break;
				}
			}
		}

		// Make the points in quad in the same order as contour.
		int f = 0;
		for (int i = 0; i < indexes.length - 1; i++) {
			if (indexes[i] > indexes[i + 1]) f++;
		}
		if (f >= 2) {
			// quad has points in a reversed order.  flip it.
			for (int i = 1, j = indexes.length - 1; i < j; i++, --j) {
				final int t = indexes[i];
				indexes[i] = indexes[j];
				indexes[j] = t;
			}
		}

		// Extract points between two corner points in quad to form
		// four edges.  A corner point is included in both two
		// adjacent edges.
		final MatOfPoint[] edges = new MatOfPoint[indexes.length];
		for (int i = 0; i < indexes.length; i++) {
			final int m = indexes[i];
			final int n = indexes[(i + 1) % indexes.length];
			if (m < n) {
				Mat tmp = contour.rowRange(m,  n);
				edges[i] = new MatOfPoint(tmp);
				tmp.release();
			} else {
				final Point[] edge = new Point[points.length - m + n + 1];
				System.arraycopy(points, m, edge, 0, points.length - m);
				System.arraycopy(points, 0, edge, points.length - m, n + 1);
				edges[i] = new MatOfPoint(edge);
			}
		}

		return edges;
	}

	/***
	 * A wrapper around OpenCV fitLine function.
	 *
	 * @param edges
	 *            An array of sets of points to fit a line to.
	 * @return
	 *            The line parameters as calculated by OpenCV fitLine
	 *            function.
	 */
	private static Mat[] fitLines(MatOfPoint[] edges) {
		final Mat[] lines = new Mat[edges.length];
		for (int i = 0; i < edges.length; i++) {
			final Mat line = new Mat();
			Imgproc.fitLine(edges[i], line, Imgproc.CV_DIST_L2, 0, 0.01, 0.01);
			lines[i] = line;
		}
		return lines;
	}

	/***
	 * Given a series of line parameters as calculated by OpenCV fitLine
	 * function, calculate a series of crossing pints of two consecutive lines.
	 *
	 * @param lines
	 *            Series of line parameters.
	 * @return
	 *            Series of crossing points.
	 */
	private static Point[] calculateCrossingPoint(Mat[] lines) {
		final int length = lines.length;

		// Extract the line parameters out of Mats.
		final float[][] lineParams = new float[lines.length][];
		for (int i = 0; i < length; i++) {
			final MatOfFloat line = new MatOfFloat(lines[i]);
			lineParams[i] = line.toArray();
			line.release();
		}

		final Point[] points = new Point[length];
		for (int i = 0; i < length; i++) {

			// Load line parameters into a set of handy variables. Those of the
			// i'th line to p and a, and of i+1'th q and b.
			final int j = (i + 1) % length;
			final float[] li = lineParams[i];
			final float[] lj = lineParams[j];
			final double px = li[0];
			final double py = li[1];
			final double ax = li[2];
			final double ay = li[3];
			final double qx = lj[0];
			final double qy = lj[1];
			final double bx = lj[2];
			final double by = lj[3];

			// Find the crossing point of the i'th line and i+1'th.
			// d is a determinant of a matrix of two collinear vectors,
			// p and q.  d is (almost) zero if and only if the two
			// lines are (almost) parallel.  We don't test the case,
			// because it never happens.  s is (a part of) a formula
			// to give a solution to an equation representing a
			// crossing point of two lines.
			final double d = px * qy - qx * py;
			final double s = (qy * (bx - ax) - qx * (by - ay)) / d;

			final double rx = s * px + ax;
			final double ry = s * py + ay;

			points[i] = new Point(rx, ry);
		}

		return points;
	}

	/***
	 * Reorder the array of four points that define a quadrangle so that the
	 * points are on a right order, i.e., upper left, upper right, lower right,
	 * then lower left.
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

}
