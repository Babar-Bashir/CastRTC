package com.avantica.videochat.call;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;

import com.avantica.videochat.R;
import com.avantica.videochat.model.Call;
import com.avantica.videochat.model.CallType;
import com.avantica.videochat.rtc.RTCClientState;

import org.webrtc.EglBase;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoTrack;

public class CallActivity extends AppCompatActivity {

    private static final String CALL = "call";
    private CallViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_call);



        Intent intent = getIntent();
        Call call = intent.getParcelableExtra(CALL);
        viewModel = new CallViewModel(this, call, call.getCurrentUserId());

        TextView title = findViewById(R.id.callInfoLbl);
        View actionContainer = findViewById(R.id.callActionContainer);
        ProgressBar progressBar = findViewById(R.id.progressBar);
        if (call.callType == CallType.INCOMING) {

            title.setText(call.origin.username + " " +getString(R.string.user_incoming_call));
            actionContainer.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
        } else {
            title.setText(getString(R.string.user_outging_call) + call.destination.username);
            progressBar.setVisibility(View.VISIBLE);
            actionContainer.setVisibility(View.GONE);
        }

        viewModel.localVideoTrack.observe(this, videoTrack -> CallActivity.this.runOnUiThread(() -> {
            SurfaceViewRenderer videoView = findViewById(R.id.localView);
            videoView.setMirror(true);

            EglBase rootEglBase = EglBase.create();
            videoView.init(rootEglBase.getEglBaseContext(), null);

            videoTrack.addRenderer(new VideoRenderer(videoView));
        }));

        viewModel.remoteVideoTrack.observe(this, videoTrack -> CallActivity.this.runOnUiThread(() -> {
            SurfaceViewRenderer videoView = findViewById(R.id.remoteView);
            videoView.setMirror(true);

            EglBase rootEglBase = EglBase.create();
            videoView.init(rootEglBase.getEglBaseContext(), null);

            videoTrack.addRenderer(new VideoRenderer(videoView));
        }));

        viewModel.state.observe(this, state -> CallActivity.this.runOnUiThread(() -> {
            if (state == RTCClientState.CONNECTED) {
                View container = findViewById(R.id.callInfoContainer);
                container.setVisibility(View.GONE);
            } else if (state == RTCClientState.DISCONNECTED) {
                finish();
            }
        }));
    }

    public void acceptCall(View v) {
        viewModel.acceptCall();
    }

    public void rejectCall(View v) {
        viewModel.rejectCall();
    }

    public void endCall(View v) {
        viewModel.endCall();
    }
}
