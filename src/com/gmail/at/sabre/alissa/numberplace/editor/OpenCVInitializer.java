package com.gmail.at.sabre.alissa.numberplace.editor;

import org.opencv.android.InstallCallbackInterface;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;

import com.gmail.at.sabre.alissa.numberplace.R;

/***
 * A wrapper around {@link OpenCVLoader} including its own implementation of {@link LoaderCallbackInterface}.
 *
 * @author alissa
 */
public class OpenCVInitializer {

	private final Context mContext;

	private final String mVersion;

	private final Handler mHandler;

	/***
	 * Create an initializer object.
	 * This constructor must be called by the UI thread.
	 *
	 * @param context
	 *            The Application context.
	 * @param name
	 *            The localized name of the application to be presented to the user that needs OpenCV.
	 * @param version
	 *            The version of OpenCV library that the application want to use.
	 *            It should be one of the constants defined in {@link OpenCVLoader} as {@code OPENCV_VERSION_*}.
	 */
	public OpenCVInitializer(final Context context, final String version) {
		mContext = context;
		mVersion = version;
		mHandler = new Handler();
	}

	/***
	 * Initialize OpenCV library.
	 * If OpenCV Manager is no installed on the device,
	 * guide the user to Google Play for download after confirmation.
	 * This method may be called by any thread.
	 *
	 * @param success
	 *            Run by the UI thread when the library is successfully
	 *            initialized and is ready for use.
	 * @param failure
	 *            Run by the UI thread when the library is not initialized,
	 *            e.g. because OpenCV is not installed on the device.
	 *            This callback is invoked after the user is guided to the
	 *            OpenCV download page.
	 *            The application needs to call {@link #initialize(Runnable, Runnable)} again
	 *            before using OpenCV.
	 *            (Or, the application can disable features that require OpenCV.)
	 */
	public void initialize(final Runnable success, final Runnable failure) {
		mHandler.post(new Runnable() {
			public void run() {
				OpenCVLoader.initAsync(mVersion, mContext, new Callback(success, failure));
			}
		});
	}

	private static final Runnable EMPTY_RUNNABLE = new Runnable() {
		public void run() {}
	};

	private class Callback implements LoaderCallbackInterface {

		private final Runnable mSuccess;

		private final Runnable mFailure;

		public Callback(final Runnable success, final Runnable failure) {
			mSuccess = success == null ? EMPTY_RUNNABLE : success;
			mFailure = failure == null ? EMPTY_RUNNABLE : failure;
		}

		public void onPackageInstall(final int operation, final InstallCallbackInterface callback) {
			// We always cancel the installation with no interaction with the user.
			// Installation guidance will be given in the code to handle INSTALL_CANCELED below.
			callback.cancel();
		}

		public void onManagerConnected(final int status) {
			switch (status) {

			case SUCCESS:
				// Initialization completed successfully.
				// We should avoid bothering the user in this case,
				// since this is normal.
				mHandler.post(mSuccess);
				return;

			case INSTALL_CANCELED:
				// Since we always invoke InstallCallbackInterface.cancel in onPackageInstall,
				// we always come here if OpenCV Manager is not installed.
				/* fall through */

			case INCOMPATIBLE_MANAGER_VERSION:
				// An OpenCV Manager is already installed on this device and runs correctly,
				// but it is older than the minimum version supported by the library
				// (i.e., "opencv library - XXX.jar" file included in the app apk).
				// I'm not sure it is ever possible,
				// because the the latest version (i.e., 2.4.6r2) support the oldest
				// released version of OpenCV Manager.
				// I believe it means as long as we stick on the library we have now,
				// the INCOMPATIBLE_MANAGER_VERSION never occurs.
				// Moreover, although the interface explicitly defines this case,
				// it lacks the opposite case; the installed OpenCV is too new.
				// I'm afraid it surely happens in a future.
				// Hmmmm.
				// Anyway, we will guide the user to the Google Play
				// for downloading another version of the OpenCV Manager.
				suggestInstallation();
				return;

			case MARKET_ERROR:
				// "market://" URI didn't work.
				// Because our onPackageInstall never calls InstallCallbackInterface.install(),
				// this status should not occur.
				/* fall through */
			case INIT_FAILED:
				// OpenCVLoader.initAsync failed with some unexpected reason.
				// Users have nothing to do to solve the case.
				// Just show a generic failure message.
				/* fall through */
			default:
				// There are no other statuses.
				// If we received other status value than the four above,
				// it should be a bug of OpenCVLoader.
				reportFailure();
				return;
			}
		}

		private void reportFailure() {
			mHandler.post(new Runnable() {
				public void run() {
					final AlertDialog dialog = new AlertDialog.Builder(mContext)
						.setMessage(R.string.opencv_failure)
						.setPositiveButton(R.string.opencv_ok, null)
						.create();
					dialog
						.setOnDismissListener(new DialogInterface.OnDismissListener() {
							public void onDismiss(DialogInterface dialog) {
								mHandler.post(mFailure);
								dialog.dismiss(); // XXX
							}
						});
					dialog.show();
				}
			});
		}

		private void suggestInstallation() {
			mHandler.post(new Runnable() {
				public void run() {
					final AlertDialog dialog = new AlertDialog.Builder(mContext)
						.setMessage(R.string.opencv_install)
						.setPositiveButton(R.string.opencv_go, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								goForDownloading();
								// Even if the user chose downloading,
								// we invoke mFailure through onDismiss below,
								// so that the app doesn't block.
								dialog.dismiss(); // XXX
							}
						})
						.setNegativeButton(R.string.opencv_cancel, null)
						.create();
					dialog
						.setOnDismissListener(new DialogInterface.OnDismissListener() {
							public void onDismiss(DialogInterface dialog) {
								mHandler.post(mFailure);
								dialog.dismiss(); // XXX
							}
						});
					dialog.show();
				}
			});
		}

		private void goForDownloading() {
			// I believe https: scheme is better than market: scheme,
			// because the user usually has more control over started activity,
			final Uri download = Uri.parse("https://play.google.com/store/apps/details?id=org.opencv.engine");
			Intent intent = new Intent(Intent.ACTION_VIEW, download);
			mContext.startActivity(intent);
		}
	}
}
