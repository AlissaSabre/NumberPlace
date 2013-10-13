package com.gmail.at.sabre.alissa.ocr;

import java.io.File;
import java.io.IOException;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.ml.CvANN_MLP;

/***
 * A {@link Classifier} based on ANN MLP.
 *
 * @author alissa
 */
public class AnnClassifier extends ClassifierBase {

	private CvANN_MLP mAnn = new CvANN_MLP();

	public AnnClassifier() {
		// {@link CvANN_MLP.load(String)} and {@link CvANN_MLP.save(String)}
		// check for the filename suffix.
		mSaveFileSuffix = ".xml";
	}

	@Override
	public int classify(byte[] feature) {
		final Mat featureMat = OpenCVUtils.matFromFeatures(feature);
		final Mat resultsMat = new Mat();
		mAnn.predict(featureMat, resultsMat);

		// Find a class that got the highest score.
		final float[] results = new MatOfFloat(resultsMat).toArray();
		float max_score = -Float.MAX_VALUE;
		int c = 0;
		for (int i = 0; i < results.length; i++) {
			if (results[i] > max_score) {
				max_score = results[i];
				c = i;
			}
		}

		featureMat.release();
		resultsMat.release();

		return c;
	}

	@Override
	protected void load(File file) throws IOException {
		mAnn.load(file.getAbsolutePath());
	}

	@Override
	public Learner getLearner() {
		return new AnnLearner();
	}

	private class AnnLearner extends LearnerBase {

		private int mHiddenLayerSize = 20;

		@Override
		public void setParameter(String name, String value) throws IllegalArgumentException {
			if ("hls".equals(name)) {
				mHiddenLayerSize = Integer.parseInt(value);
			} else {
				throw new IllegalArgumentException("Unknown parameter name: " + name);
			}
		}

		@Override
		public void learn(byte[][] trainData, int[] responses) throws IllegalArgumentException {
			int max = 0;
			for (int i = 0; i < responses.length; i++) {
				if (responses[i] > max) max = responses[i];
			}

			// specify the number of neurons in input, hidden, and output layer.
			final Mat layers = new MatOfInt(trainData[0].length, mHiddenLayerSize, max + 1);
			mAnn.create(layers);

			final Mat trainDataMat = OpenCVUtils.matFromFeatures(trainData);
			final Mat responsesMat = OpenCVUtils.expandedMatFromResponses(responses);
			final Mat weightsMat = Mat.ones(trainData.length, 1, CvType.CV_32FC1);
			mAnn.train(trainDataMat, responsesMat, weightsMat);

			trainDataMat.release();
			responsesMat.release();
			weightsMat.release();
			layers.release();
		}

		@Override
		protected void save(File file) throws IOException {
			mAnn.save(file.getAbsolutePath());
		}

	}
}
