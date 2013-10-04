package com.gmail.at.sabre.alissa.numberplace.capture;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.NativeCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

public class CameraActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener, View.OnClickListener {

	private CameraBridgeViewBase mCameraView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		mCameraView = new NativeCameraView(this, CameraBridgeViewBase.CAMERA_ID_ANY);
		setContentView(mCameraView);
		mCameraView.setCvCameraViewListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_6, this, new BaseLoaderCallback(this) {
			@Override
			public void onManagerConnected(int status) {
				super.onManagerConnected(status);
				if (status == LoaderCallbackInterface.SUCCESS) {
					onLibraryReady();
				}
			}
		});
	}

	protected void onLibraryReady() {
		mCameraView.setOnClickListener(this);
		mCameraView.enableView();
	}

	public void onCameraViewStarted(int width, int height) {
		// TODO Auto-generated method stub
	}

	public Mat onCameraFrame(Mat inputFrame) {
		//return inputFrame;
		Mat outputFrame = new Mat();
		Core.flip(inputFrame, outputFrame, 0);
		return outputFrame;
	}

	public void onClick(View v) {
		finish();
	}

	public void onCameraViewStopped() {
		// TODO Auto-generated method stub
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mCameraView != null) {
			mCameraView.disableView();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mCameraView != null) {
			mCameraView.disableView();
			mCameraView = null;
		}
	}

	
}
