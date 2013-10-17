package com.gmail.at.sabre.alissa.ocr;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/***
 * Simplified interface for a classifier.
 * <p>
 * A feature vector is an array of bytes of a certain length. A byte value in
 * the feature vector is considered as 0..255, as opposed to the Java's
 * canonical interpretation of -128..127. If an application wants a feature
 * variable of range -128..127, offset it by 128. Implementation may limit the
 * maximum length of a feature vector.
 * <p>
 * The responses (class id's) are represented by an integer value. The values
 * are considered discrete. (I.e., no interpolation or regression.) The minimum
 * acceptable value is 0. Since this is a classifier, and a response is an id,
 * dense allocation of possible response values is better. If you need n
 * classes, it's best to use 0 .. n-1. Implementation will also limit the
 * maximum values of a response, e.g., 0x00FFFFFF.
 * <p>
 * Note that this interface has no dependency other than to standard Java
 * classes.
 *
 * @author alissa
 */
public interface Classifier {

    /***
     * Classify a feature vector based on the learning.
     *
     * @param feature
     *            a feature vector.
     * @return the result of classification (class id).
     */
    public int classify(byte[] feature);

    /***
     * Load the classifier's internal states from the specified InputStream. The
     * content of the InputStream should be what was previously created by the
     * complementary {@link Learner#save(OutputStream)} method.
     *
     * @param istream
     *            InputStream consisting of classifier states.
     * @throws IOException
     *             If underlying I/O operation failed.
     */
    public void load(InputStream istream) throws IOException;

    /***
     * Set (an abstract absolute path name of) a temporary directory.
     * <p>
     * Some classifier and/or its complementary Learner require use of temporary
     * files to work properly. This method specifies the directory in which
     * those temporary files should be created. If this method was not called,
     * or if null is specified, the default temporary directory specified by the
     * underlying runtime environment is used.
     * <p>
     * <b>Please NOTE</b>, however, on some runtime environment, e.g., Android,
     * the default temporary directory doesn't work at all. Specifying a
     * directory by an application's own choice is a must on those systems.
     *
     * @param tmpDir
     *            The temporary directory.
     */
    public void setTmpDir(final File tmpDir);

    /***
     * Return a complementary Learner.
     * <p>
     * A {@Link Learner} instance is always tied to a {@link Classifier}
     * instance. This method retrieves this Classifier's complementary Learner.
     * <p>
     * If two or more invocations of this method against a same object are made,
     * the returned Learner object may or may not be same. It depends on the
     * implementation.
     *
     * @return The Learner.
     */
    public Learner getLearner();

    /***
     * A class to provides leaning methods for a {@link Classifier}.
     * <p>
     * At run time, most applications that use classifier only classifies
     * various inputs (feature vectors) but doesn't perform any learning. The
     * leaning process is usually done during the application development phase.
     * To minimize the run time footprint, the methods used only in learning
     * phase are not in the Classifier and put in a separate class. This is an
     * interface definition of the separate class.
     * <p>
     * An instance of (a class that implements) this interface is tied to an
     * instance of a classifier class and is available through
     * {@link Classifier#getLearner()}.
     *
     * @author alissa
     *
     */
    public interface Learner {

        /***
         * Specify a classifier's parameter. The set of acceptable parameter
         * names, parameter values, and their meanings are specific to the
         * actual Classifier implementation.
         *
         * @param name
         *            A name of a parameter.
         * @param value
         *            The string representation of a value of the parameter.
         */
        public void setParameter(String name, String value) throws IllegalArgumentException;

        /***
         * Supply a set of training data (feature vectors) and their expected
         * responses, and let the underlying classifier learn them.
         *
         * @param trainData
         *            a set of feature vectors of the same length. I.e.,
         *            trainData[i].length must be a same value for all i.
         * @param responses
         *            List of expected responses for the feature vectors in
         *            trainData. responses.length must be same as
         *            trainData.length. responses[i] holds the expected response
         *            for the feature vector trainData[i].
         * @throws IllegalArgumentException
         *             If the length constraints of the arguments were not met.
         */
        public void learn(byte[][] trainData, int[] responses) throws IllegalArgumentException;

        /***
         * Serialize and save the corresponding classifier's internal states
         * into an OutputStream. The internal states include the result of
         * learning built by {@link #learn(byte[][], int[])} previously and any
         * parameters specified by {@link #setParameter(String, String)}.
         *
         * @param ostream
         * @throws IOException
         */
        public void save(final OutputStream ostream) throws IOException;

    }

}
