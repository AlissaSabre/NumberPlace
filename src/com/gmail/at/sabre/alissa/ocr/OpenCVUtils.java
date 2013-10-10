package com.gmail.at.sabre.alissa.ocr;

import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;

public class OpenCVUtils {
	
	public static Mat matFromFeatures(byte[]... features) {
		final int vec_length = features[0].length;
		final float[] data = new float[features.length * vec_length];
		for (int i = 0, p = 0; i < features.length; i++, p += vec_length) {
			final byte[] vector = features[i];
			for (int j = 0; j < vec_length; j++) {
				data[p + j] = vector[j] & 255; // consider a byte value in feature vector as 0..255.
			}
		}
		return new MatOfFloat(data).reshape(1, features.length);
	}
	
	public static Mat matFromResponses(int[] responses) {
		final float[] data = new float[responses.length];
		for (int i = 0; i < responses.length; i++) {
			data[i] = responses[i];
		}
		return new MatOfFloat(data).reshape(1, responses.length);
	}

	public static Mat expandedMatFromResponses(int[] responses) {
		int max = 0;
		for (int i = 0; i < responses.length; i++) {
			if (responses[i] > max) max = responses[i];
		}
		final int width = max + 1;
		
		final float[] data = new float[responses.length * width]; // filled by 0f.
		for (int i = 0, p = 0; i < responses.length; i++, p += width) {
			data[p + responses[i]] = 1f;
		}
		
		return new MatOfFloat(data).reshape(1, responses.length);
	}
}
