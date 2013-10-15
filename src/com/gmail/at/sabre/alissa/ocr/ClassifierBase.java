package com.gmail.at.sabre.alissa.ocr;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/***
 * Base implementation for typical Classifiers.
 * <p>
 * Note that this class has no dependency other than to standard Java
 * classes.
 *
 * @author alissa
 *
 */
public abstract class ClassifierBase implements Classifier {

    public abstract int classify(byte[] feature);

    /***
     * The prefix of a temporary filenames, used by
     * {@link File#createTempFile(String, String, File)}. {@link ClassifierBase}
     * provides its own default, "njm", that has no special meaning at all.
     * <p>
     * Subclasses can change this string during its construction.
     */
    protected String mSaveFilePrefix = "njm";

    /***
     * The suffix of a temporary filenames, used by
     * {@link File#createTempFile(String, String, File)}. {@link ClassifierBase}
     * provides a null as a default, causing the underlying runtime enrionment's
     * default to be used.
     * <p>
     * Subclasses can change this string during its construction.
     */
    protected String mSaveFileSuffix;

    /***
     * A variable to hold a directory to create temporary files in.
     * {@link ClassifierBase} sets this variable in its {@link #setTmpDir(File)}
     * implementation.
     */
    protected File mTmpDir;

    public void setTmpDir(final File tmpDir) {
        mTmpDir = tmpDir;
    }

    /***
     * Copy the contents of an InputStream to an OutputStream. Whatever position
     * the given InputStream's read pointer is, this method sequentially reads
     * the contents from the current position to the end of the stream and
     * sequentially writes the read contents to the given OutputStream.
     * <p>
     * {@link ClassifierBase} uses this method to bridge
     * {@link #load(InputStream)} to {@link #load(File)}, and {@link LearnerBase}
     * does {@link LearnerBase#save(OutputStream)} to
     * {@link LearnerBase#save(File)}.
     *
     * @param istream
     *            The InputStream to copy the contents from.
     * @param ostream
     *            The OutputStream to copy the contents into.
     * @throws IOException
     *             If underlying I/O operation caused an IOException.
     */
    protected static void copyStream(final InputStream istream, final OutputStream ostream) throws IOException {
        final byte[] buffer = new byte[4096];
        for (;;) {
            final int n = istream.read(buffer);
            if (n < 0) break;
            ostream.write(buffer, 0, n);
        }
        ostream.flush();
    }

    /***
     * Load the classifier's internal states from the specified InputStream. The
     * implementation in {@link ClassifierBase} provides a glue code for the
     * {@link Classifier} implementation that require its internal states to be
     * passed in a file but a stream.
     * <p>
     * If a subclass wants to load its state from a file, override
     * {@link #load(File)} and keep this method as is. If a subclass wants to
     * load its state from a stream, override this method and ignore
     * {@link #load(File)}. The overriding method in a subclass need not to call
     * this method in {@link ClassifierBase}.
     * <p>
     * {@link ClassifierBase} uses a temporary file in its implementation of
     * this method. The caller of this method may need to use
     * {@link Classifier#setTmpDir(File)} to set its own temporary directory on
     * some runtime environment.
     *
     * @param istream
     *            InputStream consisting of classifier states.
     * @throws IOException
     *             If underlying I/O operation failed.
     */
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

    /***
     * Load the classifier's internal states from a file. The default
     * implementation of this method in {@link ClassifierBase} does nothing.
     * <p>
     * This method is called from {@link ClassifierBase}'s default
     * implementation of {@link #load(InputStream)}. Subclass implementors are
     * recommended to read description of
     * {@link ClassifierBase#load(InputStream)}.
     *
     * @param file
     *            The file to read the states from.
     * @throws IOException
     *             If an exceptional event related to an I/O operation occurred.
     */
    protected void load(File file) throws IOException {
    }

    public abstract Learner getLearner();

    /***
     * Provides a base implementation of {@link Learner} to be used with {@link ClassifierBase}.
     *
     * @author alissa
     *
     */
    protected abstract class LearnerBase implements Learner {

        public abstract void setParameter(String name, String value) throws IllegalArgumentException;

        public abstract void learn(byte[][] trainData, int[] responses) throws IllegalArgumentException;

        /***
         * Serialize and save the corresponding classifier's internal states
         * into an OutputStream. The
         * implementation in {@link LearnerBase} provides a glue code for the
         * {@link Learner} implementation that require its internal states to be
         * saved into a file but a stream.
         * <p>
         * If a subclass wants to save its state to a file, override
         * {@link #save(File)} and keep this method as is. If a subclass wants to
         * save its state to a stream, override this method and ignore
         * {@link #save(File)}. The overriding method in a subclass need not to call
         * this method in {@link LearnerBase}.
         * <p>
         * {@link LearnerBase} uses a temporary file in its implementation of
         * this method. The caller of this method may need to use
         * {@link Classifier#setTmpDir(File)} to set its own temporary directory on
         * some runtime environment.
         *
         * @param ostream
         *            OutputStream to write the complementary Classifier's internal states to.
         * @throws IOException
         *             If underlying I/O operation failed.
         */

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

        /***
         * save the classifier's internal states to a file. The default
         * implementation of this method in {@link LearnerBase} does nothing.
         * <p>
         * This method is called from {@link LearnerBase}'s default
         * implementation of {@link #save(OutputStream)}. Subclass implementors are
         * recommended to read description of
         * {@link LearnerBase#save(OutputStream)}.
         *
         * @param file
         *            The file to write the states to.
         * @throws IOException
         *             If an exceptional event related to an I/O operation occurred.
         */
        protected void save(File file) throws IOException {
        }
    }
}
