package com.gmail.at.sabre.alissa.numberplace.capture;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import android.annotation.SuppressLint;

import com.gmail.at.sabre.alissa.ocr.Ocr;

/***
 * Provides a series of static methods for number place puzzle board
 * recognition.
 *
 * @author alissa
 */
public class ImageProcessing {

    /***
     * Length in pixels of a unit square.
     * <p>
     * After detecting a puzzle board and before recognizing digits on the
     * board, we adjust the image into a <i>right</i> position and angle. We
     * prepare a right square grid consisting of 11x11 square blocks and fit the
     * puzzle board into the center 9x9 blocks, so that a cell of the puzzle
     * board is just on a block.
     */
    private static final int UNIT = 80;

    /***
     * Process the given image data, recognize a number place puzzle on it, and
     * return the puzzle data in a format that a solver can handle. It uses a
     * separate OCR (optical character recognition) engine to recognize digits
     * on the puzzle.
     *
     * @param ocr
     *            An OCR engine to use when recognizing digits.
     * @param source
     *            The source image of a number place puzzle. It is not modified.
     * @param result
     *            An empty Mat object to receive an image data showing a
     *            detected puzzle board. It is primarily for debugging. Set to
     *            null if you don't want to receive it.
     * @param puzzle
     *            A nine by nine array to receive the recognized puzzle.
     * @return True if a puzzle is successfully recognized.
     */
    public static boolean recognize(Ocr ocr, Mat source, Mat result, byte[][] puzzle) {

        // Recognize the puzzle board and get a right-fit image of the
        // board.
        Mat right = new Mat();
        boolean ok = recognizeBoard(source, right);

        // Recognize fixed digits on the board.  We rely on the grid
        // inferred by the puzzle frame to locate the digits.
        if (ok) ok = recognizeDigits(ocr, right, puzzle);

        // Show this method's understanding of the puzzle board, if
        // requested.  This is primarily for debugging.
        if (ok && result != null) {
            ok = prepareDebugImage(right, result);
        }

        right.release();

        return ok;
    }

    /***
     * Recognize a puzzle board in a source image and transform the board part
     * of the image to be <i>right</i>. The resulting image will be gray scale
     * (as opposed to color) and will have a size of exactly UNIT*11 by UNIT*11
     * pixels with its central UNIT*9 by UNIT*9 square area occupied by the
     * puzzle board.
     *
     * @param source
     *            The source image containing a puzzle board. It is not
     *            modified.
     * @param right
     *            A Mat object to receive the right image of the puzzle board.
     * @return True if a board is successfully recognized.
     */
    private static boolean recognizeBoard(Mat source, Mat right) {

        // Prepare a clean gray scale image of the source for
        // processing.
        final Mat gray = new Mat();
        makeGrayImage(source, gray);

        // Find an outer contour that is most likely of the puzzle
        // frame.
        MatOfPoint frame_contour = findFrameContour(gray);
        if (frame_contour == null) return false;

        // Fit the frame contour to a quadrangle
        final Point[] quad = QuadrangleFitter.fit(frame_contour);

        frame_contour.release();

        // The puzzle in a photo is often on a surface that is not
        // parallel to the camera's lens plane, and the puzzle image
        // is deformed in perspective.  We need to compensate it
        // before going further.
        //
        // Of course, the image is often deformed by other factors,
        // too, e.g., lens aberration or curved paper.  For the
        // moment, we ignore them.
        //
        // Assuming the fit quadrangle is a good estimation of the
        // outer border of the puzzle board, and it was a right square
        // on its original surface, perform a perspective compensation
        // to the original image to get the right image of the puzzle.
        // In practice, the quadrangle is only an estimate, and the
        // puzzle may extends beyond it.  So, we prepare an image of
        // 11x11 virtual cells and map the puzzle image into the
        // central 9x9 area.  In other words, we keep some surrounding
        // area of the estimated board area in the right image as a
        // margin.  That's what the following method does.
        adjustPerspective(gray, right, quad);

        gray.release();
        return true;
    }

    /***
     * Make a gray scale image from an RGBA image, denoising.
     *
     * @param src
     *            An RGBA image of type CV_8UC4.
     * @param dst
     *            A gray scale image of type CV_8UC1.
     */
    private static void makeGrayImage(Mat src, Mat dst) {
        if (src.channels() == 1) {
        	// If the source image is already gray scale,
        	// use it as-is without blurring.
        	// This is for debugging.
        	src.copyTo(dst);
        } else {
	    	final Mat tmp = new Mat();
	        Imgproc.GaussianBlur(src, tmp, new Size(5, 5), 0f, 0f);
	        Imgproc.cvtColor(tmp, dst, Imgproc.COLOR_RGBA2GRAY);
	        tmp.release();
        }
        mDebug.dump(dst);
    }

    /***
     * Given a gray scale image containing a puzzle board,
     * locale the most likely puzzle board on the image and return its contour.
     *
     * @param src
     * @return
     */
    private static MatOfPoint findFrameContour(final Mat src) {

    	final List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        final Mat hierarchy = new Mat();

        // We use adaptive thresholding, because the lighting is
        // often not uniform on the puzzle picture.  (For example,
        // upper left is very bright and right half is dark.)  The
        // block size and offset are the key to get a good binary image.
        // We use a fixed block size, 31, in this app.
        // The value is chosen by experiments.
        //
        // The offset, on the other hand, needs to be adjusted for each image.
        // (The best value appears depending even on the camera hardware;
        // different cameras created very different images under the same
        // conditions in my experiments and I couldn't find one fixed value
        // that covers all.)
        //
        // We try several offset values,
        // process the resulting images for frame contours,
        // evaluate each candidate contour, and take the best one.
        // The range of offsets to try is determined by experiments.

        double max_score = 0;
        final MatOfPoint best_contour = new MatOfPoint();

        for (int offset = 1; offset <= 15; offset += 2) {
        	findContours(src, offset, contours, hierarchy);
        	final MatOfPoint[] candidates = chooseFrameContours(src.size(), contours, hierarchy);
        	for (int i = 0; i < candidates.length; i++) {
        		final double score = evaluateContourAsFrame(candidates[i]);
        		if (score > max_score) {
        			max_score = score;
        			candidates[i].copyTo(best_contour); // XXX
        		}
        	}
        	for (MatOfPoint m : contours) m.release();
        	contours.clear();
        }

        hierarchy.release();
        return best_contour.empty() ? null : best_contour;
    }

	/***
	 * Perform a blob analysis on the source gray scale image and return their
	 * contours. This is just a wrapper around OpenCV
	 * {@link Imgproc#adaptiveThreshold(Mat, Mat, double, int, int, int, double)}
	 * and {@link Imgproc#findContours(Mat, List, Mat, int, int)}.
	 *
	 * @param src
	 *            A gray scale image for analysis. This image is not modified.
	 * @param offset
	 *            The offset (C parameter) to be used for adaptive thresholding.
	 * @param contours
	 *            List of contours to be filled upon return.
	 * @param hierarchy
	 *            List of hierarchy info to be filled upon return.
	 */
    private static void findContours(Mat src, int offset, List<MatOfPoint> contours, Mat hierarchy) {

        final Mat tmp = new Mat();
        Imgproc.adaptiveThreshold(src, tmp, 255,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV,
                31, offset);
        mDebug.dump(tmp);

        // Analyze blob shapes.  We use one of the detected contours
        // (the outer contour of the puzzle board) to fit several
        // lines to.  The fitting is by least squares.  For the best
        // fitting, we need all points in the contour, so the last
        // parameter to the following findCOntours should be
        // CHAIN_APROX_NONE.  However, the following call usually
        // return a lot of contours, say 500-600, and large contour
        // contain thousands of points, if we don't approximate.
        // Experiments showed that the differences of the line
        // fitting to a contour of CHAIN_APPROX_NONE and
        // CHAIN_APPROX_SIMPLE was very small, and I decided to use
        // CHAIN_APPROX_SIMPLE here.
        //
        // In this version, we repeat this process several times for an image.
        // We also perform time-consuming processing on contours,
        // such as polygon cover or convex hull analysis.
        // We absolutely can't live with the full contour points.
        Imgproc.findContours(tmp, contours, hierarchy,
                Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);
        tmp.release();
    }

    /***
     * Given a set of contours of blobs, find blobs that look as a
     * number place puzzle frame. We use two criteria: (1) the blob has at least
     * nine and at most 81 holes, and (2) the blob covers the center of the
     * image.
     * <p>
     * A number place puzzle is shown in a 9x9 (81) bordered cells. if all the
     * borders are fully recognized as a blob, it has 81 holes. However, the
     * cells are divided into nine 3x3 blocks, with major borders divide the
     * blocks and minor borders divide cells in a block. Minor borders are thin
     * and in a lighter color than major borders and/or digits, so parts of
     * minor borders often disappear or are broken in the binary image. If all
     * minor borders were ignored, only major borders remain, and the puzzle
     * frame blob would have nine holes. That's why our first criterion says
     * "9 to 81".
     * <p>
     * The second criterion is ad hoc. Since this app is dedicated for
     * recognizing the number place puzzle, when shooting, users are expected to
     * _center_ the primary subject, the puzzle. When a user shoots a page of a
     * puzzle magazine, for example, the page may show two or more number place
     * puzzles, and the picture will very likely contain parts of other puzzles.
     * The "covers center" criterion effectively eliminates them.
     * An additional benefit of this test is we can eliminates large blobs
     * partly surrounding the real puzzle board.
     *
     * @param image_size
     *            The size of the image where the contours are on.
     * @param contours
     *            The list of contours to choose from.
     * @param hierarchy
     *            The hierarchy info associated with the contours.
     * @return
     *            Candidates of the outer contour of the puzzle frame.
     */
    private static MatOfPoint[] chooseFrameContours(Size image_size, List<MatOfPoint> contours, Mat hierarchy) {

        final List<MatOfPoint> candidates = new ArrayList<MatOfPoint>();
        final int[] hinfo = new int[4];
        final Point center = new Point(image_size.width / 2f, image_size.height / 2f);

        for (int i = 0; i < contours.size(); i++) {

            // Retrieve the hierarchy info for the i'th contour.
            hierarchy.get(0, i, hinfo);
            if (hinfo[3] >= 0) {
                // This is an internal contour (a contour of a hole)
                // of a blob.  just skip it.
                continue;
            }

            // Count how many holes this blob has.
            int holes = 0;
            for (int p = hinfo[2]; p >= 0; p = hinfo[0]) {
                ++holes;
                hierarchy.get(0, p, hinfo);
            }

            // If this blob has too few or too many holes, it is not a
            // puzzle frame.  Reject it.
            if (holes < 9 || holes > 81) continue;

            // See if it covers the center of the original image.
            if (!Imgproc.boundingRect(contours.get(i)).contains(center)) continue;

            // See if it _really_ covers the center.
            if (pointPolygonTest(contours.get(i), center, false) < 0) continue;

            // This appears a puzzle frame.
            candidates.add(contours.get(i));
        }

        // Return an array of all candidates.
        return candidates.toArray(new MatOfPoint[0]);
    }

    /***
     * Evaluate how the given contour is likely of a puzzle frame.
     * The return value is in range 0..1.  Larger value means more likely.
     * <p>
     * In this version, we use a ratio of areas of the contour itself
     * and a covering (rotated) rectangle.
     */
    private static double evaluateContourAsFrame(final MatOfPoint contour) {
    	final double contourArea = Imgproc.contourArea(contour);
    	final RotatedRect rect = minAreaRect(contour);
    	final double hullArea = rect.size.area();
    	return contourArea / hullArea;
	}

    /***
     * The {@link MatOfPoint} version of {@link Imgproc#pointPolygonTest(MatOfPoint2f, Point, boolean)}.
     * BTW why Java binding of OpenCV lacks it,
     * making Java programmers' live harder
     * by making the function's typical use case of passing contours from findCountour complicated
     * and slowing down the execution speed
     * by effectively keeping java apps off from going through the integer only fast path...
     */
    private static double pointPolygonTest(final MatOfPoint contour, final Point point, final boolean measureDist) {
        final MatOfPoint2f m = new MatOfPoint2f();
        contour.convertTo(m, CvType.CV_32FC2);
        final double result = Imgproc.pointPolygonTest(m, point, measureDist);
        m.release();
        return result;
    }

    /***
     * The {@link MatOfPoint} version of {@link Imgproc#minAreaRect(MatOfPoint2f)}.
     * Ditto.
     * @see {@link #pointPolygonTest(MatOfPoint, Point, boolean)}
     */
    private static RotatedRect minAreaRect(final MatOfPoint contour) {
        final MatOfPoint2f m = new MatOfPoint2f();
        contour.convertTo(m, CvType.CV_32FC2);
        final RotatedRect result = Imgproc.minAreaRect(m);
        m.release();
        return result;
    }

    /***
     * The four points defining the right square on the resulting image of
     * {@link #adjustPerspective(Mat, Mat, Point[])}. It is used only in that
     * method.
     */
    private static final Point[] GOAL = new Point[] {
            new Point(UNIT,      UNIT),
            new Point(UNIT * 10, UNIT),
            new Point(UNIT * 10, UNIT * 10),
            new Point(UNIT,      UNIT * 10)
    };

    /***
     * Perform a perspective transformation so that a specified quadrangle on
     * the source image is on the central 9x9 parts of the whole 11x11 grid on
     * the destination image. This is to fit the detected puzzle frame to the
     * 9x9 right square.
     *
     * @param src
     *            The source image. It is not modified.
     * @param dst
     *            A Mat to receive the resulting image.
     * @param quad
     *            An array of four points defining a quadrangle on the source
     *            image. The array's length must be four. The four points must
     *            corresponds to the upper left, upper right, bottom right, and
     *            bottom left corners in this order. This array is not modified.
     */
    private static void adjustPerspective(Mat src, Mat dst, Point[] quad) {
        // Before adjusting perspective, _normalize_ the source image based on the pixels inside quad.
    	// Pixels outside quad may saturate, but it doesn't matter.
    	// This process may enhance the quality of the resulting image.
    	final Mat mask = Mat.zeros(src.size(), CvType.CV_8UC1);
    	final MatOfPoint poly = new MatOfPoint(quad);
    	Core.fillConvexPoly(mask, poly, new Scalar(255));
    	Core.MinMaxLocResult minmax = Core.minMaxLoc(src, mask);
    	poly.release();
    	mask.release();
    	final double alpha = 256 / (minmax.maxVal - minmax.minVal + 1);
    	final double beta = -alpha * minmax.minVal;
    	final Mat tmp = new Mat();
    	src.convertTo(tmp, src.type(), alpha, beta);

    	// Now, prepare transformation matrix.
    	final Mat quadMat = new MatOfPoint2f(quad);
        final Mat goalMat = new MatOfPoint2f(GOAL);
        final Mat transformer = Imgproc.getPerspectiveTransform(quadMat, goalMat);

        // Warp!
        final Size size = new Size(UNIT * 11, UNIT * 11);
        dst.create(size, src.type());
        Imgproc.warpPerspective(tmp, dst, transformer, size,
                Imgproc.INTER_LINEAR, Imgproc.BORDER_REPLICATE, new Scalar(0));

        transformer.release();
        goalMat.release();
        quadMat.release();
        tmp.release();
    }

    /***
     * Draw the estimated grid lines on the right image. This is primarily to
     * help debugging the puzzle frame detection ({@link
     * #chooseFrameContour(Size, List<MatOfPoint>, Mat)}) and quad-fit (
     * {@link QuadrangleFitter}) codes.
     *
     * @param right
     *            The right image of the puzzle as created by {@link
     *            #chooseFrameContour(Size, List<MatOfPoint>, Mat)}.
     * @param result
     *            The right image with estimated grid lines.
     * @return
     *            True.
     */
    private static boolean prepareDebugImage(Mat right, Mat result) {

        final Mat tmp = new Mat();
        Imgproc.cvtColor(right, tmp, Imgproc.COLOR_GRAY2RGBA);

        Scalar color = new Scalar(255, 0, 0, 255); // opaque red
        for (int i = UNIT; i <= UNIT * 10; i += UNIT) {
            Core.line(tmp, new Point(i, UNIT), new Point(i, UNIT * 10), color);
            Core.line(tmp, new Point(UNIT, i), new Point(UNIT * 10, i), color);
        }
        tmp.copyTo(result);

        tmp.release();
        return true;
    }

    /***
     * Recognize fixed digits on the puzzle board and fill the specified puzzle
     * array. This method is expected to be called after a perspective
     * compensation phase. The source image must be of size UNIT*11 by UNIT*11
     * pixels, and the puzzle board should be approximately on its central
     * UNIT*9 by UNIT*9 area.
     *
     * @param ocr
     *            An OCR engine.
     * @param src
     *            The right source image. It is not modified.
     * @param puzzle
     *            A nine by nine array initialized to all zeros. The recognized
     *            digits will be set to appropriate elements of this array upon
     *            return.
     * @return
     *            True if successful.
     */
    private static boolean recognizeDigits(Ocr ocr, Mat src, byte[][] puzzle) {

        // We recognize a digit by focusing on a relatively small area
        // that the digit is expected to be in.  The area is the
        // estimated cell plus some margin around it.
        //
        // We add the margin for two reasons: the estimated cell may
        // be off from the real position and the digit may exceed the
        // estimated cell, and we need a part of the grid lines
        // (borders) are visible in the focused area so that an empty
        // cell is properly recognized as empty.  (If we passed to
        // {@link #recognizeOneDigit(Ocr, Rect)} an image containing
        // an empty backgrounds only, it would malfunction by
        // amplifying minor texture on the surface and camera noises
        // to find some figures.)
        //
        // We give the margin UNIT / 2 pixels width, so
        // the total width and heights of the focus area is UNIT * 2
        // by UNIT * 2 pixels.
        final Rect roi = new Rect(0, 0, UNIT * 2, UNIT * 2);

        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {

                // Focus on the cell at x'th column of y'th row in the
                // estimated grid.
                roi.x = UNIT / 2 + x * UNIT;
                roi.y = UNIT / 2 + y * UNIT;
                final Mat focused = src.submat(roi);

                // Try to recognize a digit in the focused area.
                final String digit = recognizeOneDigit(ocr, focused);

                // If one is recognized, set it to the puzzle data.
                if (digit != null) {
                    final byte d = Byte.parseByte(digit);
                    if (d >= 1 && d <= 9) {
                        puzzle[y][x] = d;
                    } else {
                        // puzzle[y][x] = -1;
                    }
                }

                focused.release();
            }
        }

        return true;
    }

    /***
     * Recognize a digit in an image of a focused area. It is expected that the
     * source image is perspective compensated, is gray scale, and contains
     * (parts of) grid lines around the digit or an empty cell.
     *
     * @param ocr
     *            The OCR engine.
     * @param src
     *            The image of a focused area. It is not modified.
     * @return
     *            A string representation of the recognized digit.
     */
    private static String recognizeOneDigit(Ocr ocr, Mat src) {

        // Beginning with the gray scale image again, make a binary
        // image of this focused area.  This time we process the
        // focused area only, so it's safe to use a single threshold
        // for all pixels.  Moreover, we use Otsu method to determine
        // the threshold value, so the light differences on the whole
        // board is adjusted well.  Note that if we used the Otsu
        // method on an empty area, that is a sub-image containing
        // white background only, the method adjusted the threshold to
        // emphasis the minor textures of the background or even
        // noises.  Our focused area, on the other hand, is wider than
        // a cell, and it is expected to contain parts of the grid
        // lines on the puzzle board as well as the background.  Otsu
        // method in this case sets a threshold value to isolate
        // border lines from the background, and the empty area inside
        // the cell will be (almost) empty after thresholding.
        final Mat tmp = new Mat();
        Imgproc.threshold(src, tmp, 0, 255, Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU);
    	mDebug.dump(src);
    	mDebug.dump(tmp);

        // Run the blob analysis on the focused area to detect a blob
        // for the digit.
        final List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        final Mat hierarchy = new Mat();
        Imgproc.findContours(tmp, contours, hierarchy, Imgproc.RETR_CCOMP,
                Imgproc.CHAIN_APPROX_SIMPLE);

        // Among the blobs, detect one for the digit, and return its
        // bounding rectangle.
        final Rect rect = detectDigitRect(contours, hierarchy);

        contours.clear();
        hierarchy.release();

        String d;
        if (rect == null) {

            // If no digit blob was found, this probably is an empty
            // cell.
            d = null;

        } else {

            // Run the thresholding again, exactly in the same way as
            // we did as the first step of this method, because the
            // blobbing ({@link Imgproc.findContours(Mat, List<MatOfPoint>, Mat, int, int)})
            // garbles its input image data.  (We could have kept a
            // copy of the binary image.  I'm not sure which is
            // better.  FIXME.)
            Imgproc.threshold(src, tmp, 0, 255, Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU);

            // Recognize the blob as a digit.
            final Mat tmp2 = tmp.submat(rect);
            d = ocr.recognize(tmp2);
            tmp2.release();

            // A null from Ocr.recognize means that it couldn't
            // recognize the given image (blob) as any digit.  If it
            // happened, it means "we detected something in this cell,
            // but we don't know which digit it is." in our case.  It
            // is a totally different condition than the null return
            // from this ({@link #recognizeOneDigit(Ocr, Mat)}) method
            // that means "We are sure this cell contains no digit."
            // As an indication, we return a no digit character.
            if (d == null) d = "*";

            // Well, I have a feeling that the above, ah,
            // _specification_, or behaviour, is a bit broken.  It may
            // be better to change the meaning of null to "unknown
            // something" and use a " " or a "" to indicate an empty
            // cell.  FIXME.
        }

        tmp.release();

        return d;
    }

    /***
     * Given a series of contours in a focused area (i.e., a cell with some
     * margin), find one for a digit, and return its bounding rectangle.
     * <p>
     * We use the following criteria: (1) The size of the blob is not too large;
     * (2) The size of the blob is not too small; and (3) The center of the blob
     * is within the estimated cell. If two or more contours satisfied the
     * criteria, take the largest one.
     * <p>
     * A focused area usually contains a part of grid lines, parts of digits
     * from adjacent cells, and noises, as well as the digit in the cell. The
     * first criteria is expected to eliminate the grid lines (because they are
     * parts of longer lines trimmed to the focused area the blobs from the grid
     * lines will taller and wider than a cell,) the second the noises, and the
     * third the adjacent digits.
     * <p>
     * In my experiments, there were very few occasions that multiple candidates
     * were detected by the above criteria and that were essentially caused by a
     * same situation. That is, the original puzzle board image was too
     * distorted, and the grid estimation was not very good, causing the
     * estimated size and position of a cell was very different from the real.
     * In particular, the cell size was estimated larger and was located just in
     * between two real cells (the center of estimated cell was almost on the
     * grid line.) Both the cell in focus and an adjacent cell contained digits.
     * As a result, the focused area, that is an estimated cell plus some large
     * margin around it, contained two (almost) complete blobs from two digits.
     * It's no wonder the algorithm can't find which of the two complete digits
     * were the one to be in the cell. To handle the case correctly, we could
     * analyze grid lines in more detail to make finer adjustment of the cell
     * estimation. Or, we could just indicate to the upper layer that we failed
     * detecting _one_ digit blob. For the moment, since the case is very rare,
     * this method silently picks up an arbitrary one.
     *
     * @param contours
     *            The contours.
     * @param hierarchy
     *            The contour hierarchy informations.
     * @return
     *            The bounding rectangle of a most likely digit, or a null if no
     *            such contour was found.
     */
    private static Rect detectDigitRect(List<MatOfPoint> contours, Mat hierarchy) {

        double maxArea = 0;
        Rect maxRect = null;
        final int[] hinfo = new int[4];

        for (int i = 0; i < contours.size(); i++) {

            // Skip inner contour, i.e., a hole.
            hierarchy.get(0, i, hinfo);
            if (hinfo[3] >= 0) continue;

            // Get the bounding rectangle.
            final MatOfPoint points = contours.get(i);
            final Rect rect = Imgproc.boundingRect(points);
            points.release();

            // The criteria #1.  We drop a blob larger than the
            // estimated cell size.
            if (rect.width >= UNIT || rect.height >= UNIT) continue;

            // The criteria #2.  We drop a blob too small relative to
            // the estimated cell size.  Note that we can't be very
            // aggressive, because some publishers of number place
            // puzzle use smaller font than others, making larger
            // spaces around digits.
            //
            // Also note that a digit "1" may have a very small width.
            // It's safe to allow any width here.
            if (rect.height < UNIT / 3) continue;

            // The criteria #3.  We use the center of the bounding
            // rectangle to compare against the estimated cell
            // position and do not use the centroid of the blob,
            // because it is sufficient and cheaper.
            final int midX = rect.x + rect.width / 2;
            final int midY = rect.y + rect.height / 2;
            if (midX <= UNIT / 2 || midX >= UNIT + UNIT / 2 ||
                midY <= UNIT / 2 || midY >= UNIT + UNIT / 2) continue;

            // This blob passed the criteria.  Keep it if it is larger
            // then the past largest.
            double area = rect.area();
            if (area > maxArea) {
                maxArea = area;
                maxRect = rect;
            }
        }

        return maxRect;
    }

    // When using DebugDumpEnabled, you need to add
    // <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
    // on the AndroidManifest.xml

    public static DebugDump mDebug = new DebugDump();

	public static class DebugDump {
    	public void dump(Mat image) {}
    }

    @SuppressLint({"SdCardPath", "SimpleDateFormat", "DefaultLocale"})
    public static class DebugDumpEnabled extends DebugDump {

	    public static final String mDebugFilenameDirectory = "/sdcard/Download";

	    public static final String mDebugFilenameBase = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());

	    public int mDebugFilenameSerial = 0;

		@Override
	    public void dump(Mat src) {
	        final String filename = String.format("%s/debug-%s-%02d.png",
	        		mDebugFilenameDirectory, mDebugFilenameBase, mDebugFilenameSerial);
	        mDebugFilenameSerial++;
	    	final Mat tmp = new Mat();
	        switch (src.channels()) {
	        case 4:
	        	Imgproc.cvtColor(src, tmp, Imgproc.COLOR_RGBA2BGRA);
	        	src = tmp;
	        	break;
	        case 3:
	        	Imgproc.cvtColor(src, tmp, Imgproc.COLOR_RGB2BGR);
	        	src = tmp;
	        	break;
	        case 1:
	        	break;
	        default:
	        	throw new IllegalArgumentException("Strange number of channels: " + src.channels());
	        }
	    	org.opencv.highgui.Highgui.imwrite(filename, src);
	    	tmp.release();
	    }
    }
}
