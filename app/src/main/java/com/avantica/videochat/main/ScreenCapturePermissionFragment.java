package com.avantica.videochat.main;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;


public class ScreenCapturePermissionFragment extends Fragment {

    private static final String TAG = "ScreenCaptureFragment";

    private static final int REQUEST_MEDIA_PROJECTION = 1;

    private MediaProjectionManager mMediaProjectionManager;

    private  ScreenCapturePermissionListener mPermissionCallbackTarget;

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Activity activity = getActivity();

        mMediaProjectionManager = (MediaProjectionManager)
                activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        startActivityForResult(
                mMediaProjectionManager.createScreenCaptureIntent(),
                REQUEST_MEDIA_PROJECTION);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_MEDIA_PROJECTION) {

            mPermissionCallbackTarget.onScreenCapturePermissionResult(Activity.RESULT_OK == resultCode, data);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        try {
            mPermissionCallbackTarget = (ScreenCapturePermissionListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement ScreenCapturePermissionListener");
        }
    }
}
