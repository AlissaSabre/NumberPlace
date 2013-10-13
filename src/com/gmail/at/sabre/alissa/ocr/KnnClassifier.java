package com.gmail.at.sabre.alissa.ocr;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import org.opencv.core.Mat;
import org.opencv.ml.CvKNearest;

/***
 * A wrapper for org.opencv.ml.CvKNearest to make it available as {@link Classifier}.
 *
 * @author alissa
 */
public class KnnClassifier extends ClassifierBase {

	/***
	 * Magic number at the top of the load/save file.
	 * <p>
	 * Although its documentation is unclear, OpenCV's {@link CvKNearest} class
	 * doesn't support its load and save methods. We need to implement our own.
	 * This magic number is written at the very beginning of the output stream
	 * when the internal states were saved and is checked when loading from the
	 * input stream.
	 */
	protected static final int MAGIC = 0xee576de7;

	/***
	 * The k parameter of KNN algorithm, that is, the number of neighbours to
	 * consider. The value is set through
	 * {@link KnnLearner#setParameter(String, String)} with the parameter name
	 * "k".
	 */
	protected int mK = 1;

	private CvKNearest mEngine = new CvKNearest();

	@Override
	public int classify(byte[] feature) {
		final Mat featureMat = OpenCVUtils.matFromFeatures(feature);
		final Mat resultsMat = new Mat();
		final Mat responsesMat = new Mat();
		final Mat distancesMat = new Mat();

		mEngine.find_nearest(featureMat, mK, resultsMat, responsesMat, distancesMat);

		final float[] results = new float[1];
		resultsMat.get(0, 0, results);
		final int result = (int)results[0];

		featureMat.release();
		resultsMat.release();
		responsesMat.release();
		distancesMat.release();

		return result;
	}

	/***
	 * Load internal states from an InputStream.
	 *
	 * @param istream
	 *            The input stream to read internal states from.
	 * @throws IOException
	 *             If an exceptional event related to I/O operation occurred.
	 */
	@Override
	public void load(InputStream istream) throws IOException {
		try {
			final ObjectInputStream ois = new ObjectInputStream(istream);

			if (MAGIC != ois.readInt()) throw new IOException("Magic number not match");

			mK = ois.readInt();

			final Mat featuresMat  = OpenCVUtils.matFromFeatures( (byte[][])ois.readObject());
			final Mat responsesMat = OpenCVUtils.matFromResponses((int[])   ois.readObject());

			// Don't close ois so that the underlying istream is live.

			mEngine.train(featuresMat, responsesMat);

		} catch (ClassNotFoundException e) {
			throw new IOException("ClassNotFoundException caught");
		}
	}

	/***
	 * Return this {@link KnnClassifier} object's complementary {@link KnnLearner} object.
	 * It is used for training.
	 */
	@Override
	public Learner getLearner() {
		return new KnnLearner();
	}

	/***
	 * The {@link Learner} implementation for {@link KnnClassifier}.
	 *
	 * @author alissa
	 */
	private class KnnLearner extends LearnerBase {

		/***
		 * Set KNN specific parameters. The following parameter names are
		 * accepted.
		 * <dl>
		 * <dt>"k"</dt>
		 * <dd>The number of neighbours to consider. It takes a positive
		 * integral value.</dd>
		 * </dl>
		 *
		 * @param name
		 *            The name of a parameter.
		 * @param value
		 *            The string representation of the parameter value.
		 * @throws IllegalArgumentException
		 *             If the specified parameter name is unknown.
		 */
		@Override
		public void setParameter(String name, String value) throws IllegalArgumentException {
			if ("k".equals(name)) {
				mK = Integer.parseInt(value);
			} else {
				throw new IllegalArgumentException("Unknown parameter name: " + name);
			}
		}

		// CvKNearest provides no way to take its internal state out of it.
		// (CvKNearest::save exists but doesn't work even with C++ binding.)
		// So, we keep our own copy of the original data and use it for
		// future save method.  It can be a big waste of memory, but it's OK.
		// {@link #learn(byte[][], int[]) is only run on a host environment.

		private byte[][] mTrainData;

		private int[] mResponses;

		@Override
		public void learn(byte[][] trainData, int[] responses) {
			mTrainData = new byte[trainData.length][];
			for (int i = 0; i < trainData.length; i++) mTrainData[i] = trainData[i].clone();
			mResponses = responses.clone();

			final Mat trainDataMat = OpenCVUtils.matFromFeatures(trainData);
			final Mat responsesMat = OpenCVUtils.matFromResponses(responses);
			mEngine.train(trainDataMat, responsesMat);
		}

		/***
		 * Save the corresponding {@link KnnClassifier} instance's internal
		 * states into an OputputStream.
		 *
		 * @param ostream
		 *            The output stream to save the states into.
		 * @throws IOException
		 *             If underlying I/O operation failed.
		 */
		@Override
		public void save(OutputStream ostream) throws IOException {
			final ObjectOutputStream oos = new ObjectOutputStream(ostream);
			oos.writeInt(MAGIC);
			oos.writeInt(mK);
			oos.writeObject(mTrainData);
			oos.writeObject(mResponses);
			oos.flush();
		}
	}
}
