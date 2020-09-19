package com.avantica.videochat.signaling;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SignalingManager {

    //region Constants
    private final static String ROOMS = "rooms";
    private final static String CALLEE_CANDIDATES = "calleeCandidates";
    private final static String CALLER_CANDIDATES = "callerCandidates";
    private final static String OFFER_TYPE = "type";
    private final static String OFFER_DESCRIPTION = "sdp";
    private final static String OFFER = "offer";
    private final static String ANSWER = "answer";
    private final static String CANDIDATE = "candidate";
    private final static String OFFER_MID = "sdpMid";
    private final static String OFFER_MLINE_INDEX = "sdpMLineIndex";
    //endregion

    //region Private properties
    private SignalingManagerInterface delegate;
    private FirebaseFirestore db;
    private DocumentReference androidRoom;
    private CollectionReference androidCalleeCandidatesCollection;
    private List<IceCandidate> pendingCandidates = new ArrayList<>();
    /*
     * Holds a reference to the ICE candidates
     * It's callerCandidates when the app makes the offer (creates room)/
     * It's calleeCandidates when the app joins a room.
     * */
    private CollectionReference candidatesCollection;
    private  DocumentReference currentRoom;
    //endregion

    //region Public API
    public SignalingManager(SignalingManagerInterface delegate, String userId) {

        this.delegate = delegate;
        db = FirebaseFirestore.getInstance();
        androidRoom = db.collection(ROOMS).document(userId);
        androidCalleeCandidatesCollection = androidRoom.collection(CALLEE_CANDIDATES);
    }

    public void createOffer(SessionDescription sdp) {
        candidatesCollection = androidRoom.collection(CALLER_CANDIDATES);

        Map<String, Object> offerInfo = new HashMap<>();
        Map<String, Object> offer = new HashMap<>();
        offerInfo.put(OFFER_TYPE, sdp.type.canonicalForm());
        offerInfo.put(OFFER_DESCRIPTION, sdp.description);
        offer.put(OFFER, offerInfo);
        androidRoom.set(offer);


        // Listening for remote session description below
        androidRoom.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    return;
                }

                if (documentSnapshot != null && documentSnapshot.exists()) {
                    Map<String, Object> data = documentSnapshot.getData();
                    if (data.containsKey(ANSWER)) {

                        Map<String, Object> answer = (Map<String, Object>) data.get(ANSWER);
                        String answerValue = (String) answer.get(OFFER_DESCRIPTION);
                        SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, answerValue);
                        delegate.signalingManagerGotRemoteDescription(sessionDescription);
                    }

                }
            }
        });

        // Listen for remote ICE candidates below
        androidCalleeCandidatesCollection.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    return;
                }

                for (DocumentChange change : queryDocumentSnapshots.getDocumentChanges()) {
                    if (change.getType() == DocumentChange.Type.ADDED) {
                        Map<String, Object> data = change.getDocument().getData();
                        String sdp = (String) data.get(CANDIDATE);
                        String sdpMid = (String) data.get(OFFER_MID);
                        Long sdpMLineIndex = (Long) data.get(OFFER_MLINE_INDEX);

                        IceCandidate candidate = new IceCandidate(sdpMid, sdpMLineIndex.intValue(), sdp);
                        delegate.signalingManagerGotRemoteIceCandidate(candidate);
                    }
                }
            }
        });
        // Listen for remote ICE candidates above
    }

    public void addCandidate(IceCandidate candidate) {
        if (candidatesCollection == null) {
            pendingCandidates.add(candidate);
            return;
        } else if(pendingCandidates.isEmpty()) {
            handleCandidate(candidate);
        } else {
            pendingCandidates.add(candidate);
            for (IceCandidate iceCandidate : pendingCandidates) {
                handleCandidate(iceCandidate);
            }
            pendingCandidates.clear();
        }
    }

    public void joinRoom(String roomId) {
        currentRoom = db.collection(ROOMS).document(roomId);
        currentRoom.get().addOnCompleteListener(task -> {
            if (task.getResult().exists()) {
                candidatesCollection = currentRoom.collection(CALLEE_CANDIDATES);

                Map<String, Object> data = task.getResult().getData();
                Map<String, Object> offer = (Map<String, Object>) data.get(OFFER);
                String sdp = (String) offer.get(OFFER_DESCRIPTION);
                SessionDescription remoteOffer = new SessionDescription(SessionDescription.Type.OFFER, sdp);
                delegate.signalingManagerGotRemoteOffer(remoteOffer);


                CollectionReference calleerReference = currentRoom.collection(CALLER_CANDIDATES);
                // Listen for remote ICE candidates below
                calleerReference.addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        return;
                    }

                    for (DocumentChange change : queryDocumentSnapshots.getDocumentChanges()) {
                        if (change.getType() == DocumentChange.Type.ADDED) {
                            Map<String, Object> data1 = change.getDocument().getData();
                            String sdp1 = (String) data1.get(CANDIDATE);
                            String sdpMid = (String) data1.get(OFFER_MID);
                            Long sdpMLineIndex = (Long) data1.get(OFFER_MLINE_INDEX);

                            IceCandidate candidate = new IceCandidate(sdpMid, sdpMLineIndex.intValue(), sdp1);
                            delegate.signalingManagerGotRemoteIceCandidate(candidate);
                        }
                    }
                });
            }
        });
    }


    public void updateAnswer(SessionDescription sdp) {
        Map<String, Object> roomWithAnswer = new HashMap<>();
        Map<String, Object> answer = new HashMap<>();
        answer.put(OFFER_TYPE, sdp.type.canonicalForm());
        answer.put(OFFER_DESCRIPTION, sdp.description);
        roomWithAnswer.put(ANSWER, answer);
        currentRoom.update(roomWithAnswer);
    }

    public void finishCall() {
        androidRoom.delete();
    }
    //endregion

    //region Private helpers
    private void handleCandidate(IceCandidate candidate) {
        Map<String, Object> candidateInfo = new HashMap<>();

        candidateInfo.put(CANDIDATE, candidate.sdp);
        candidateInfo.put(OFFER_MLINE_INDEX, candidate.sdpMLineIndex);
        candidateInfo.put(OFFER_MID, candidate.sdpMid);

        candidatesCollection.add(candidateInfo);
    }
    //endregion
}


