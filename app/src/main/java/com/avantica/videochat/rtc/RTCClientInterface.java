package com.avantica.videochat.rtc;

import androidx.annotation.Nullable;

import org.webrtc.*;

public interface RTCClientInterface {


    void rtcClientStartCallWithSdp(RTCClient client, SessionDescription sdp);
    void rtcClientCreatedAnswer(RTCClient client, SessionDescription sdp);
    void rtcClientDidReceiveLocalVideoTrack(RTCClient client, VideoTrack localVideoTrack);
    void rtcClientDidReceiveRemoteVideoTrack(RTCClient client, VideoTrack remoteVideoTrack);
    void rtcClientDidReceiveError(RTCClient client,Error error);
    void rtcClientDidChangeConnectionState(RTCClient client, PeerConnection.IceConnectionState connectionState);
    void rtcClientDidChangeState(RTCClient client, RTCClientState state);
    void rtcClientDidGenerateIceCandidate(RTCClient client, IceCandidate iceCandidate);
}
