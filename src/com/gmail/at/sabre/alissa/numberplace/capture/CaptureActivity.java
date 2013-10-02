package com.gmail.at.sabre.alissa.numberplace.capture;

import com.gmail.at.sabre.alissa.numberplace.K;
import com.gmail.at.sabre.alissa.numberplace.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class CaptureActivity extends Activity {
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture);
        
        findViewById(R.id.done).setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent result = new Intent();
				result.putExtra(K.PUZZLE_DATA, new byte[][] {
						{ 2, 1, 0, 4, 0, 0, 0, 3, 6 },
						{ 8, 0, 0, 0, 0, 0, 0, 0, 5 },
						{ 0, 0, 5, 3, 0, 9, 8, 0, 0 },
						{ 6, 0, 4, 9, 0, 7, 1, 0, 0 },
						{ 0, 0, 0, 0, 3, 0, 0, 0, 0 },
						{ 0, 0, 7, 5, 0, 4, 6, 0, 2 },
						{ 0, 0, 6, 2, 0, 3, 5, 0, 0 },
						{ 5, 0, 0, 0, 0, 0, 0, 0, 9 },
						{ 9, 3, 0, 0, 0, 5, 0, 2, 7 },	
				});
				setResult(RESULT_OK, result);
				finish();
			}
		});
    }
}
