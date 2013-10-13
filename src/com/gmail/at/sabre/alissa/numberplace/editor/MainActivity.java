package com.gmail.at.sabre.alissa.numberplace.editor;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.OpenCVLoader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.gmail.at.sabre.alissa.numberplace.K;
import com.gmail.at.sabre.alissa.numberplace.R;
import com.gmail.at.sabre.alissa.numberplace.capture.CaptureActivity;
import com.gmail.at.sabre.alissa.numberplace.solver.PuzzleSolver;

/***
 * The main activity of the number place app.
 *
 * @author alissa
 *
 */
public class MainActivity extends Activity {

	private static final String TAG = "numberplace..MainActivity";

	private static final int REQ_CAPTURE = 1;

	private Handler mHandler;
	private PuzzleSolver mSolver;
	private PuzzleEditorView mPuzzleEditor;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");

    	super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.button_capture).setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				buttonCapture_onClick(v);
			}
		});
        findViewById(R.id.button_solve).setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				buttonSolve_onClick(v);
			}
		});
        findViewById(R.id.button_about).setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				buttonAbout_onClick(v);
			}
		});

        findViewById(R.id.busy).setVisibility(View.INVISIBLE);

        mHandler = new Handler();
        mSolver = new PuzzleSolver();
        mPuzzleEditor = (PuzzleEditorView)findViewById(R.id.puzzleEditorView);
    }

	@Override
    public void onSaveInstanceState(Bundle outState) {
    	super.onSaveInstanceState(outState);
    	mPuzzleEditor.saveState(outState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
    	super.onRestoreInstanceState(savedInstanceState);
    	mPuzzleEditor.restoreState(savedInstanceState);
    }

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data); // Is this unneeded?  FIXME
		if (requestCode == REQ_CAPTURE && resultCode == RESULT_OK) {
			Object obj = data.getSerializableExtra(K.EXTRA_PUZZLE_DATA);
			// Hmm... It appears that a serialized byte[][] object ("[[B")
			// is deserialized back as an Object[] containing byte[].
			// I don't know why.  Anyway we need to live with it.
			// Well, I have a slight feeling that the choice of byte[][]
			// for puzzle data *was* not wise. XXX
			onPuzzleCapture(toByteArrayArray(obj));
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		// This MainActivity doesn't need OpenCV library, but the initialization
		// may pop up a dialog and may invoke Google Play App for downloading
		// OpenCV stuff, so it's better the process to happen early.
		Log.i(TAG, "start initializing OpenCV");
		final Context appContext = this;
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_6, appContext, new BaseLoaderCallback(appContext) {
			@Override
			public void onManagerConnected(int status) {
				if (status == SUCCESS) {
					Log.i(TAG, "OpenCV initialization successful");
				} else {
					Log.w(TAG, "OpenCV initialization unsuccessful");
					super.onManagerConnected(status);
				}
			}
		});
	}

	@Override
    public void onStop() {
    	super.onStop();
    	mSolver.stop();
    }

    private void buttonCapture_onClick(View v) {
    	Intent request = new Intent(getApplicationContext(), CaptureActivity.class);
		request.putExtra(K.EXTRA_DEVICE_ROTATION, getWindowManager().getDefaultDisplay().getRotation());
    	startActivityForResult(request, REQ_CAPTURE);
	}

    private void onPuzzleCapture(byte[][] puzzle) {
    	mPuzzleEditor.setFixedDigits(puzzle);
    	mPuzzleEditor.setSolution(null);
    }

    private void buttonSolve_onClick(View view) {
    	mPuzzleEditor.setEnabled(false);
    	mPuzzleEditor.setSolution(null);

    	findViewById(R.id.busy).setVisibility(View.VISIBLE);

    	findViewById(R.id.button_capture).setEnabled(false);
    	findViewById(R.id.button_solve).setEnabled(false);
    	findViewById(R.id.button_about).setEnabled(false);

    	// The following code fragment does:
    	// (1) Execute mSolver.solve() in a separate thread.
    	// (2) Then, execute onPuzzleSolved() in the UI thread.
		final byte[][] puzzle = mPuzzleEditor.getFixedDigits();
		new Thread() { { setDaemon(true); }
    		@Override
    		public void run() {
    	    	final byte[][] solution = mSolver.solve(puzzle);
				mHandler.post(new Runnable() {
					public void run() {
						onPuzzleSolved(solution);
					}
				});
    		}
    	}.start();
    }

    private void onPuzzleSolved(byte[][] solution) {
    	mPuzzleEditor.setSolution(solution);
    	mPuzzleEditor.setEnabled(true);

    	findViewById(R.id.button_capture).setEnabled(true);
    	findViewById(R.id.button_solve).setEnabled(true);
    	findViewById(R.id.button_about).setEnabled(true);

    	findViewById(R.id.busy).setVisibility(View.INVISIBLE);

    	if (solution == null) {
    		Toast.makeText(getApplicationContext(), R.string.toast_text_impossible, Toast.LENGTH_LONG).show();
    	}
    }

    private void buttonAbout_onClick(View view) {
    	startActivity(new Intent(getApplicationContext(), AboutActivity.class));
    }

    private byte[][] toByteArrayArray(Object obj) {
    	try {
    		Object[] src = (Object[])obj;
    		byte[][] dst = new byte[src.length][];
    		for (int i = 0; i < src.length; i++) {
    			dst[i] = (byte[])src[i];
    		}
    		return dst;
    	} catch (ClassCastException e) {
    		return null;
    	}
    }
}
