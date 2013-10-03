package com.gmail.at.sabre.alissa.numberplace;

/***
 * A set of project wide constants.
 * @author Alissa
 */
public interface K {
	
	/***
	 * A key to an intent extra to pass a number place puzzle data (as a byte[][] object.)
	 * <p>
	 * NOTE that a byte[][] object stored in intent appears to be gotten as an Object[] object.
	 * I'm not sure why and when it happens.  The receiver needs to take care of it.
	 * (Probably I was fool choosing byte[][] for a puzzle data...) 
	 */
	public static final String EXTRA_PUZZLE_DATA = "EXTRA_PUZZLE_DATA";
	
	public static final String EXTRA_PUZZLE_SOLUTION = "EXTRA_PUZZLE_SOLUTION";
}
