package com.voiceussd.prototype.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Handler;                    // ← ADD THIS
import android.os.Looper;                    // ← ADD THIS
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.voiceussd.prototype.audio.TTSManager;

import com.voiceussd.prototype.audio.STTManager;
import com.voiceussd.prototype.services.InputSimulator;
import java.util.Objects;

public class USSDDetectorService extends AccessibilityService {
    private static final String TAG = "USSDDetectorService";
    private boolean isUSSDActive = false;

    private TTSManager ttsManager;
    private STTManager sttManager;
    private InputSimulator inputSimulator;
    private String mainMenuContent = "";


    // The onAccessibilityEvent gets called whenever something happens on the screen (Window opens, text changes...)
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event){
//        try{

            if (event == null) return;

            // Variables that will help to identify USSD dialogue
            String packageName = event.getPackageName() != null ? event.getPackageName().toString(): "";
            String className = event.getClassName() != null ? event.getClassName().toString() : "";

            // Log all events for debugging
            Log.d(TAG, "Event" + event.getEventType() +
                    ", Package: " + packageName + ", Class: " + className);

            // Check for USSD dialogue
            if (isUSSDDialog(event, packageName, className)){
                if(!isUSSDActive){
                    isUSSDActive = true;
                    Log.d(TAG, "=== USSD WINDOW DETECTED ===");
                    handleUSSDWindow(event);
                }
            }else if (isUSSDActive && isUSSDWindowClosed(event, packageName, className)){
                isUSSDActive = false;
                Log.d(TAG, "=== USSD WINDOW CLOSED ====");
            }
//        } catch (InterruptedException e) {
//            Log.d(TAG, Objects.requireNonNull(e.getMessage()));
//        }
    }


    private boolean isUSSDDialog(AccessibilityEvent event, String packageName, String className){
        // For Android 14 + Pixel 5, USSD might come from diff packages
        boolean isPhoneRelated = packageName.contains("phone") || packageName.contains("dialer") || packageName.contains("telecom") || packageName.contains("telephony") || packageName.equals("com.android.phone") || packageName.equals("com.google.android.dialer");

        // Check if it's a dialog or popup
        boolean isDialog = className.contains("AlertDialog");

        // Check content for USSD Indicators
        boolean hasUSSDContent = false;

        if (!event.getText().isEmpty()){
            String text = event.getText().toString().toLowerCase();
            hasUSSDContent = text.contains("ussd") || text.contains("ussd code") ||text.contains("1)") || text.contains("n next") || text.contains("balance") || text.contains("amafaranga") || text.contains("kwemeza") || text.contains("pin") || text.contains ("shyiramo");
        }

        return (isPhoneRelated && isDialog) && hasUSSDContent;
    }
    private void handleUSSDWindow(AccessibilityEvent event){

        // Check for Menu window
        if(!event.getText().isEmpty()){
            String ussdText = event.getText().toString();
            if(!ussdText.contains("USSD code running")){
                // Reset main menu content for tts service
                mainMenuContent = "";

                // Try to get more detailed content using AccessibilityNodeInfo
                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode != null){
                    extractUSSDText(rootNode);

                    // Speak Main menu
                    if(!mainMenuContent.isEmpty()){
                        ttsManager.speakMenu(mainMenuContent);
                    }
                    rootNode.recycle();
                }
            }
        }

    }

    private void extractUSSDText(AccessibilityNodeInfo node){ // Uses AccessibilityNodeInfo instead of AccessibilityEvent to get detailed window content
        if (node == null) return;

        // Get text from this node
        CharSequence text = node.getText();
        if(text != null && text.length() > 0){
            String textStr = text.toString();
            Log.d(TAG, "Node Text: " + textStr);
            // ENHANCED STRUCTURE ANALYSIS
//            Log.d(TAG, "========== NODE ANALYSIS ==========");
//            Log.d(TAG, "Class Name: " + (node.getClassName() != null ? node.getClassName() : "null"));
//            Log.d(TAG, "Resource ID: " + (node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "null"));
//            Log.d(TAG, "Content Description: " + (node.getContentDescription() != null ? node.getContentDescription() : "null"));
//            Log.d(TAG, "Clickable: " + node.isClickable());
//            Log.d(TAG, "Focusable: " + node.isFocusable());
//            Log.d(TAG, "Editable: " + node.isEditable());
//            Log.d(TAG, "Child Count: " + node.getChildCount());
//            Log.d(TAG, "===================================");
        }else{
            // ENHANCED STRUCTURE ANALYSIS
//            Log.d(TAG, "========== NULL NODE ANALYSIS ==========");
//            Log.d(TAG, "Class Name: " + (node.getClassName() != null ? node.getClassName() : "null"));
//            Log.d(TAG, "Resource ID: " + (node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "null"));
//            Log.d(TAG, "Content Description: " + (node.getContentDescription() != null ? node.getContentDescription() : "null"));
//            Log.d(TAG, "Clickable: " + node.isClickable());
//            Log.d(TAG, "Focusable: " + node.isFocusable());
//            Log.d(TAG, "Editable: " + node.isEditable());
//            Log.d(TAG, "Child Count: " + node.getChildCount());
//            Log.d(TAG, "===================================");
        }
        // Collect Menu Content
        if(text != null && isMainMenuContent(text.toString())){
            mainMenuContent += text;
        }

        // Check child nodes recursively
        for(int i = 0; i < node.getChildCount(); i ++){
            AccessibilityNodeInfo child = node.getChild(i);
            if(child != null){
//                Log.d(TAG, "NODE EXTRACTED: "+child);
                extractUSSDText(child);
                child.recycle(); // Free memory
            }
        }
    }

    // Method to identify main menu content
    private boolean isMainMenuContent(String text){
        // Checks if str contains a digit followed by ')' symbol
        return text.contains("1)") || text.contains("0)");
    }

    private boolean isUSSDWindowClosed(AccessibilityEvent event, String packageName, String className){

        // Check if USSD window dialog nor popup is inactive
        return !className.contains("AlertDialog");
    }

    // Log when accessibility service is interrupted, we unfortunately can't tell which service is interrupting
    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }

    // Add this as a class variable in USSDDetectorService
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // Update your onServiceConnected() method - find this section and replace it:
    @Override
    protected void onServiceConnected(){
        super.onServiceConnected();
        Log.d(TAG, "USSDDetectorService connected and ready");

        // Initialize TTS
        ttsManager = new TTSManager(this);

        // Initialize input simulator
        inputSimulator = new InputSimulator(this);

        // Initialize STT with callback
        sttManager = new STTManager(this, new STTManager.STTCallback() {
            @Override
            public void onNumberRecognized(int number) {
                Log.d(TAG, "=== USER SPOKE NUMBER: " + number + " ===");
                boolean success = inputSimulator.inputNumberAndSend(number);
                if (!success) {
                    Log.e(TAG, "Failed to process user input");
                }
            }

            @Override
            public void onSTTError(String error) {
                Log.e(TAG, "STT Error: " + error);
            }

            @Override
            public void onSTTReady() {
                Log.d(TAG, "STT is ready");
            }
        });

        // Connect TTS to STT - FIXED VERSION
        ttsManager.setSTTCallback(new TTSManager.STTTriggerCallback() {
            @Override
            public void onTTSFinished() {
                // IMPORTANT: Switch to main thread before starting STT
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        // Now we're on the main thread - safe to start STT
                        if (sttManager != null && sttManager.isReady()) {
                            sttManager.startListening();
                        }
                    }
                });
            }
        });

        // Your existing configuration...
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
    }


    // Update onDestroy()
    @Override
    public void onDestroy(){
        super.onDestroy();
        if(ttsManager != null) {
            ttsManager.shutdown();
        }
        if(sttManager != null) {
            sttManager.shutdown();
        }
    }
}




