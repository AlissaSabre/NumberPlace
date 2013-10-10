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
 * @author alissa
 */
public class KnnClassifier extends ClassifierBase {

	protected static final int MAGIC = 0xee576de7;
	
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
	
	@Override
	public Learner getLearner() {
		return new KnnLearner();
	}

	private class KnnLearner extends LearnerBase {

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
