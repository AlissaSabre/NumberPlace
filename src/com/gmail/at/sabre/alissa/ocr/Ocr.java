package com.gmail.at.sabre.alissa.ocr;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class Ocr {

	private static final int MAGIC = 0xfe69d6fd;

	public static class Params {
		public final int mThumbWidth;
		public final int mThumbHeight;
		public final boolean mUseHist;
		public final String mClassifier;

		public Params(int width, int height, boolean useHist, String classifier) {
			mThumbWidth = width;
			mThumbHeight = height;
			mUseHist = useHist;
			mClassifier = classifier;
		}
	}

	private final int mThumbWidth;

	private final int mThumbHeight;

	private final Size mThumbSize;

	private final boolean mUseHist;

	private final int mVecSize;

	private String[] mDecoder; // not final due to Builder impl.  FIXME!

	private final Classifier mClassifier;

	/***
	 * Constructor used with {@link Builder}. {@link #mDecoder} will be set
	 * later. FIXME!
	 */
	public Ocr(Params params) {
		mThumbWidth = params.mThumbWidth;
		mThumbHeight = params.mThumbHeight;
		mThumbSize = new Size(params.mThumbWidth, params.mThumbHeight);
		mUseHist = params.mUseHist;
		mVecSize = featureVectorSize(params.mThumbWidth, params.mThumbHeight, params.mUseHist);
		mClassifier = getClassifier(params.mClassifier);
	}

	/***
	 * Create a new Ocr instance using the data in the specified InputStream.
	 *
	 * @param istream
	 * @throws IOException
	 */
	public Ocr(InputStream istream) throws IOException {
		final ObjectInputStream ois = new ObjectInputStream(istream);
		if (ois.readInt() != MAGIC) throw new IllegalArgumentException("illegal magic number");
		mThumbWidth = ois.readInt();
		mThumbHeight = ois.readInt();
		mThumbSize = new Size(mThumbWidth, mThumbHeight);
		mUseHist = ois.readBoolean();
		mVecSize = featureVectorSize(mThumbWidth, mThumbHeight, mUseHist);
		mDecoder = readDecoder(ois);
		mClassifier = getClassifier(ois.readUTF());
		mClassifier.load(ois);
	}

	private static int featureVectorSize(int width, int height, boolean useHist) {
		return (width * height) + (useHist ? width + height : 0);
	}

	private static Classifier getClassifier(String name) {
		try {
			return (Classifier)Class.forName(name).newInstance();
		} catch (Exception e) {
			throw new RuntimeException("classifier instanciation failed: " + name, e);
		}
	}

	private static String[] readDecoder(ObjectInputStream ois) throws IOException{
		try {
			return (String[])ois.readObject();
		} catch (ClassNotFoundException e) {
			throw new IOException("ClassNotFoundException caught: " + e.getMessage());
		}
	}

	public interface Callback {
		public void onRecognize(Mat image);
	}

	private Callback mCallback;

	public void setCallback(Callback callback) {
		mCallback = callback;
	}

	public String recognize(Mat image) {
		final Callback callback = mCallback;
		if (callback != null) callback.onRecognize(image);

		return mDecoder[mClassifier.classify(getFeature(image))];
	}

	/***
	 * Return a feature vector for a specified image.
	 *
	 * @param image
	 *            A CV_8UC1 binary (or high contrast gray scale) image
	 *            containing and being fit to a digit.
	 * @return The feature vector.
	 */
	private byte[] getFeature(Mat image) {
		final byte[] feature = new byte[mVecSize];

		final Mat thumb = new Mat();
		Imgproc.resize(image, thumb, mThumbSize, 0, 0, Imgproc.INTER_AREA);
		System.arraycopy(new MatOfByte(thumb.reshape(1, 1)).toArray(), 0, feature, 0, thumb.width() * thumb.height());

		if (mUseHist) {
			double scale = 255d / mThumbHeight;
			int hist = mThumbWidth * mThumbHeight;
			for (int i = 0; i < mThumbWidth; i++) {
				Mat m = thumb.col(i);
				feature[hist + i] = (byte)(Core.countNonZero(m) * scale);
				m.release();
			}
			scale = 255d / mThumbWidth;
			hist += mThumbWidth;
			for (int i = 0; i < mThumbHeight; i++) {
				Mat m = thumb.row(i);
				feature[hist + i] = (byte)(Core.countNonZero(m) * scale);
				m.release();
			}
		}

		return feature;
	}

	public class Builder {

		private final Classifier.Learner mLearner = mClassifier.getLearner();

		private final Map<String, Integer> mEncoder = new HashMap<String, Integer>();

		private final List<byte[]> mFeatures = new ArrayList<byte[]>();

		private final List<Integer> mResponses = new ArrayList<Integer>();

		public void setClassifierParameters(String... params) {
			if (params.length % 2 != 0) {
				throw new IllegalArgumentException("odd number of params");
			}
			for (int i = 0; i < params.length; i += 2) {
				mLearner.setParameter(params[i], params[i + 1]);
			}
		}

		public void beginLearning() {
			mEncoder.clear();
			mFeatures.clear();
			mResponses.clear();
		}

		public void feedSample(Mat image, String digit) {
			final Integer id;
			if (mEncoder.containsKey(digit)) {
				id = mEncoder.get(digit);
			} else {
				id = mEncoder.size();
				mEncoder.put(digit, id);
			}

			mFeatures.add(getFeature(image));
			mResponses.add(id);
		}

		public void finishLearning() {
			mDecoder = new String[mEncoder.size()];
			for (Map.Entry<String, Integer> entry : mEncoder.entrySet()) {
				mDecoder[entry.getValue()] = entry.getKey();
			}

			final byte[][] features = mFeatures.toArray(new byte[0][0]);
			final int[] responses = new int[mResponses.size()];
			for (int i = 0; i < responses.length; i++) {
				responses[i] = mResponses.get(i);
			}
			mLearner.learn(features, responses);
		}

		public void save(OutputStream ostream) throws IOException {
			final ObjectOutputStream oos = new ObjectOutputStream(ostream);
			oos.writeInt(MAGIC);
			oos.writeInt(mThumbWidth);
			oos.writeInt(mThumbHeight);
			oos.writeBoolean(mUseHist);
			oos.writeObject(mDecoder);
			oos.writeUTF(mClassifier.getClass().getName());
			mLearner.save(oos);
		}
	}
}
