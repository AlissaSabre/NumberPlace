package com.gmail.at.sabre.alissa.numberplace;

/***
 * A set of project wide constants, including intent extra keys.
 * @author alissa
 */
public interface K {

	/***
	 * A key to an intent extra to pass a number place puzzle data.
	 * The content is a byte[][] object.
	 * <p>
	 * NOTE that a byte[][] object stored in intent appears to be gotten as an
	 * Object[] object. I'm not sure why and when it happens. The receiver needs
	 * to take care of it. (Probably I was fool choosing byte[][] for a puzzle
	 * data...)
	 */
	public static final String EXTRA_PUZZLE_DATA = "PUZZLE_DATA";

	/***
	 * A key to an intent extra to pass a number place puzzle solution.
	 * This is not used yet.
	 */
	public static final String EXTRA_PUZZLE_SOLUTION = "PUZZLE_SOLUTION";

	/***
	 * A key to an intent extra to pass a device rotation state.
	 * The content is of type int and the value is as returned by
	 * {@link android.view.Display#getRotation()}.
	 */
	public static final String EXTRA_DEVICE_ROTATION = "DEVICE_ROTATION";

	/***
	 * A key to an intent extra to pass a raw image data. The content is of type
	 * byte[] and containing raw data that
	 * {@link android.graphics.BitmapFactory#decodeByteArray(byte[], int, int)}
	 * can handle.
	 */
	public static final String EXTRA_IMAGE_DATA = "IMAGE_DATA";
}
