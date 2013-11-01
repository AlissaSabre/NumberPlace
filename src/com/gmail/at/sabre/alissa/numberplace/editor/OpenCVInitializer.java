package com.gmail.at.sabre.alissa.numberplace.editor;

import org.opencv.android.InstallCallbackInterface;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;

import com.gmail.at.sabre.alissa.numberplace.R;

/***
 * A wrapper around {@link OpenCVLoader} including its own implementation of {@link LoaderCallbackInterface}.
 * This class is intended to be general use,
 * though the targeted use cases are of Number Place Breaker app.
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
	 *            The Activity that uses OpenCV.
	 * @param name
	 *            The localized name of the application to be presented to the user that needs OpenCV.
	 * @param version
	 *            The version of OpenCV library that the application want to use.
	 *            It should be one of the constants defined in {@link OpenCVLoader} as {@code OPENCV_VERSION_*}.
	 */
	public OpenCVInitializer(final Activity activity, final String version) {
		mContext = activity; // XXX
		mVersion = version;
		mHandler = new Handler();
	}

	/***
	 * Initialize OpenCV library.
	 * This method may be called by any thread.
	 * It returns immediately,
	 * and one of the three specified actions will be run by the UI thread later,
	 * when the initialization is finished.
	 * One, two, or all three actions may be null.
	 * <p>
	 * If OpenCV Manager is installed on the device,
	 * it initializes the OpenCV library
	 * and runs {@code success} action when the library is ready.
	 * No user interaction takes place in this case.
	 * <p>
	 * If OpenCV Manager is not installed on the device,
	 * it prompts the user to go to the download page.
	 * If the user declined, it runs {@code failure} action.
	 * Otherwise, i.e., the user followed the prompt,
	 * it runs {@code pending} action before the user completes installation.
	 * <p>
	 * The {@code pending} action may be run under other circumstances,
	 * e.g., the user dismissed the prompt by pressing a BACK button,
	 * not answering the question,
	 * or the process was interrupted by some temporary failure such as
	 * low system resources.
	 * Consider the {@code pending} is an indication of
	 * "Please try again later."
	 *
	 * @param success
	 *            Run by the UI thread when the library is successfully
	 *            initialized and is ready for use.
	 * @param failure
	 *            Run by the UI thread when the library is not initialized,
	 *            e.g. because OpenCV is not installed on the device, and
	 *            the user didn't want to install it.
	 *            The application is expected to disable its OpenCV dependent
	 *            features if this happens.
	 *            Or, it may call {@link #initialize(Runnable, Runnable)} again
	 *            if it needs to use OpenCV.
	 * @param pending
	 *            Run by the UI thread when the library is not initialized
	 *            yet but may be soon.
	 *            The application is expected to call
	 *            {@link #initialize(Runnable, Runnable, Runnable)} again.
	 *            This happens when OpenCV is not installed on the device,
	 *            and the user has not explicitly declined to install it.
	 */
	public void initialize(final Runnable success, final Runnable failure, final Runnable pending) {
		try {

			// Try calling a benign OpenCV native function.
			// If it works, it means the OpenCV library is already initialized and ready.
			// In that case, immediately post the success action
			// to avoid the overhead of full initialization.
			Core.getBuildInformation();
			mHandler.post(success);

		} catch (UnsatisfiedLinkError e) {

			// The VM throws an UnsatisfiedLinkError when OpenCV native library is not loaded yet.
			// We need to perform the full initialization if it happened.
			mHandler.post(new Runnable() {
				public void run() {
					OpenCVLoader.initAsync(mVersion, mContext, new Callback(success, failure, pending));
				}
			});
		}
	}

	/***
	 * An instance of {@link Runnable} that does nothing when run.
	 * This is only used in {@link Callback},
	 * so it should have been private in the class,
	 * but Java doesn't allow it.
	 */
	private static final Runnable EMPTY_RUNNABLE = new Runnable() {
		public void run() {}
	};

	/***
	 * The loader callback.
	 * We don't subclass {@link org.opencv.android.BaseLoaderCallback} and write our own.
	 * @author alissa
	 */
	private class Callback implements LoaderCallbackInterface {

		private final Runnable mSuccess;

		private final Runnable mFailure;

		private final Runnable mPending;

		/***
		 * The constructor.
		 * It receives three {@link Runnable}s from
		 * {@link OpenCVInitializer#initialize(Runnable, Runnable, Runnable)}.
		 * They can be null, and null actions are substituted by {@link OpenCVInitializer#EMPTY_RUNNABLE}.
		 */
		public Callback(final Runnable success, final Runnable failure, final Runnable pending) {
			mSuccess = success == null ? EMPTY_RUNNABLE : success;
			mFailure = failure == null ? EMPTY_RUNNABLE : failure;
			mPending = pending == null ? EMPTY_RUNNABLE : pending;
		}

		/***
		 * This method is called back when {@link OpenCVLoader} wants to
		 * install OpenCV Manager (as well as something else in a future?)
		 * The expected action is to show a dialog and get an approval to
		 * do so from the user.
		 * This implementation, however, never shows a dialog,
		 * and always cancels the request.
		 * We will show our dialog later, in {@link #onManagerConnected(int)}.
		 * @param operation
		 *            The requested operation. This implementation doesn't use it.
		 * @param callback
		 *            A callback that this method tells to {@link OpenCVLoader}
		 *            the user's decision.
		 *            This implementation always calls {@link InstallCallbackInterface#cancel()} immediately.
		 */
		public void onPackageInstall(final int operation, final InstallCallbackInterface callback) {
			callback.cancel();
		}

		/***
		 * This method is called back when {@link OpenCVLoader} finished
		 * the initialization, regardless it was successful.
		 */
		public void onManagerConnected(final int status) {
			switch (status) {

			case SUCCESS:
				// Initialization completed successfully.
				// We should avoid bothering the user in this case,
				// since this is normal.
				mHandler.post(mSuccess);
				return;

			case INSTALL_CANCELED:
				// Since we always invoke InstallCallbackInterface.cancel() in onPackageInstall,
				// we always come here if OpenCV Manager is not installed.
				/* fall through */

			case INCOMPATIBLE_MANAGER_VERSION:
				// An OpenCV Manager is already installed on this device and runs correctly,
				// but it is older than the minimum version supported by the library
				// (i.e., "opencv library - XXX.jar" file included in the app apk).
				// I'm not sure it is ever possible,
				// because the the latest version (i.e., 2.4.6r2) support the oldest
				// released version of OpenCV Manager.
				// I believe the INCOMPATIBLE_MANAGER_VERSION never occurs
				// as long as we stick on the library we have now.
				// (Unless the OpenCV developers released in a future older versions
				// than the very first release...)
				//
				// Moreover, although the interface explicitly defines this case,
				// it lacks the opposite case; the installed OpenCV is too new.
				// I'm afraid it can happen in a future, and the current API has
				// no way to tell the situation to this app.
				//
				// Hmmmm.
				//
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
				// There are no other defined statuses.
				// If we received other status value than the four above,
				// it should be a bug of OpenCVLoader.
				reportFailure();
				return;
			}
		}

		/***
		 * Show a dialog to tell initialization failure.
		 * The message (text content) is taken from the hosting activity's resource
		 * using id {@link R.string#opencv_failure},
		 * as well as the label for the only button using {@link R.string#opencv_ok}.
		 * It should use general wording; details are not notified to the user.
		 * <p>
		 * After the user acknowledged or dismissed the dialog,
		 * the callback's failure action ({@link #mFailure}) will be run.
		 */
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

		/***
		 * Show a dialog to suggest installing OpenCV Manager.
		 * The title, message (text contents), and button labels are taken
		 * from the hosting activity's resource using the following ids:
		 * {@link R.string#opencv_title} for the title,
		 * {@link R.string#opencv_install} for the message,
		 * {@link R.string#opencv_cancel} for the positive button label,
		 * and {@link R.string#opencv_cancel} for the negative button label.
		 * <p>
		 * If the user acknowledged positively,
		 * the download page of OpenCV Manager will be visited.
		 * <p>
		 * After the user acknowledged the dialog,
		 * an appropriate action of this callback's three actions
		 * (i.e., {@link #mSuccess}, {@link #mFailure}, or {@link #mPending} will be run.
		 */
		private void suggestInstallation() {
			mHandler.post(new Runnable() {
				private boolean mActionPosted = false;
				public void run() {
					final AlertDialog dialog = new AlertDialog.Builder(mContext)
						.setTitle(R.string.opencv_title)
						.setMessage(R.string.opencv_install)
						.setPositiveButton(R.string.opencv_go, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								goForDownloading();
								mHandler.post(mPending);
								mActionPosted = true;
								dialog.dismiss(); // XXX
							}
						})
						.setNegativeButton(R.string.opencv_cancel, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								mHandler.post(mFailure);
								mActionPosted = true;
								dialog.dismiss(); // XXX
							}
						})
						.create();
					dialog
						.setOnDismissListener(new DialogInterface.OnDismissListener() {
							public void onDismiss(DialogInterface dialog) {
								if (!mActionPosted) mHandler.post(mPending);
								mActionPosted = true;
								dialog.dismiss(); // XXX
							}
						});
					dialog.show();
				}
			});
		}

		/***
		 * Start an activity to show the download page of OpenCV Manager on Google Play App Store.
		 * The actual URI to visit is taken from the hosting activity's resource
		 * using an id {@link R.string#opencv_uri}.
		 */
		private void goForDownloading() {
			// I believe https: scheme is better than market: scheme,
			// because the user usually has more control over started activity,
			final Uri download = Uri.parse(mContext.getString(R.string.opencv_uri));
			Intent intent = new Intent(Intent.ACTION_VIEW, download);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			mContext.startActivity(intent);
		}
	}
}
