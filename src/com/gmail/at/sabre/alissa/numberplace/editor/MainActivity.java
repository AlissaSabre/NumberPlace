package com.gmail.at.sabre.alissa.numberplace.editor;

import com.gmail.at.sabre.alissa.numberplace.K;
import com.gmail.at.sabre.alissa.numberplace.R;
import com.gmail.at.sabre.alissa.numberplace.capture.CaptureActivity;
import com.gmail.at.sabre.alissa.numberplace.solver.PuzzleSolver;

import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.Toast;

public class MainActivity extends Activity {
	
	private static final int REQ_CAPTURE = 1;
	
	private Handler mHandler;
	private PuzzleSolver mSolver;
	private PuzzleEditorView mPuzzleEditor;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
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
		super.onActivityResult(requestCode, resultCode, data); // Don't we need this?  FIXME
		if (requestCode == REQ_CAPTURE && resultCode == RESULT_OK) {
			Object obj = data.getSerializableExtra(K.PUZZLE_DATA);
			// Hmm... It appears that a serialized byte[][] object ("[[B")
			// is deserialized back as an Object[] containing byte[].
			// I don't know why.  Anyway we need to live with it.
			onPuzzleCapture(toByteArrayArray(obj));
		}
	}

	@Override
    public void onStop() {
    	super.onStop();
    	mSolver.stop();
    }

    private void buttonCapture_onClick(View v) {
    	Intent request = new Intent(getApplicationContext(), CaptureActivity.class);
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
