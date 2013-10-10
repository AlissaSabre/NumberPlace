package com.gmail.at.sabre.alissa.ocr;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class ClassifierBase implements Classifier {
	
	public abstract int classify(byte[] feature);

	protected String mSaveFilePrefix = "njm";
	
	protected String mSaveFileSuffix;

	protected File mTmpDir;
	
	/***
	 * Set a full pathname of a directory that this ClassifierBase may use for a temporary directory.
	 * If not set or a null is set, the class tries to find and use a directory
	 * through the runtime environment. 
	 * @param path
	 */
	public void setTmpDir(final File tmpDir) {
		mTmpDir = tmpDir;
	}
	
	protected static void copyStream(final InputStream istream, final OutputStream ostream) throws IOException {
		final byte[] buffer = new byte[4096]; 
		for (;;) {
			final int n = istream.read(buffer);
			if (n < 0) break;
			ostream.write(buffer, 0, n);
		}
	}

	public void load(final InputStream istream) throws IOException {
		final File file = File.createTempFile(mSaveFilePrefix, mSaveFileSuffix, mTmpDir);
		try {
			// Copy the content of the specified InputStream into a temporary file.
			final OutputStream ostream = new FileOutputStream(file);
			try {
				copyStream(istream, ostream);
			} finally {
				ostream.close();
			}
			
			// Use the file to load the state.
			load(file);

		} finally {
			file.delete();
		}
	}
	
	protected void load(File file) throws IOException {
	}
	
	public abstract Learner getLearner();

	protected abstract class LearnerBase implements Learner {

		public abstract void setParameter(String name, String value) throws IllegalArgumentException;

		public abstract void learn(byte[][] trainData, int[] responses) throws IllegalArgumentException;
		
		public void save(final OutputStream ostream) throws IOException {
			final File file = File.createTempFile(mSaveFilePrefix, mSaveFileSuffix, mTmpDir);
			try {
				// Save the classifier sate into the temporary file.
				save(file);
				
				// copy the content of the temporary file into the specified OutputStream.
				final InputStream istream = new FileInputStream(file);
				try {
					copyStream(istream, ostream);
				} finally {
					istream.close();
				}
			} finally {
				file.delete();
			}
		}
		
		protected void save(File file) throws IOException {
		}
	}
}
