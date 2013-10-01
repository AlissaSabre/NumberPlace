package com.gmail.at.sabre.alissa.numberplace.editor;

import com.gmail.at.sabre.alissa.numberplace.R;
import com.gmail.at.sabre.alissa.numberplace.solver.PuzzleSolver;

import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import android.support.v4.app.NavUtils;

public class MainActivity extends Activity {
	
	private Handler mHandler;
	private PuzzleSolver mSolver;
	private PuzzleEditorView mPuzzleEditor;
	private View mBusy;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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
    public void onStop() {
    	super.onStop();
    	mSolver.stop();
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
						showSolution(solution);
					}
				});
    		}
    	}.start();
    }
    
    private void showSolution(byte[][] solution) {
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
}
