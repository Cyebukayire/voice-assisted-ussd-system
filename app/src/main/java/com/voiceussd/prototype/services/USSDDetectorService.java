package com.voiceussd.prototype.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.voiceussd.prototype.audio.TTSManager;
import com.voiceussd.prototype.audio.STTManager;

public class USSDDetectorService extends AccessibilityService {
    private static final String TAG = "USSDDetectorService";
    private boolean isUSSDActive = false;

    private TTSManager ttsManager;
    private STTManager sttManager;
    private InputSimulator inputSimulator;
    private String currentUSSDContent = "";
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // NEW: State machine for digit-by-digit input
    private enum DigitInputState {
        IDLE,                    // Not in digit input mode
        WAITING_FOR_FIRST_DIGIT, // Just started, waiting for first digit
        WAITING_FOR_NEXT_DIGIT,  // Got a digit, waiting for next one
        COMPLETED               // User said "done" or timeout reached
    }

    private DigitInputState digitInputState = DigitInputState.IDLE;
    private StringBuilder currentDigitInput = new StringBuilder();

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        String className = event.getClassName() != null ? event.getClassName().toString() : "";

        // Log phone-related dialogs for debugging
        boolean isPhoneRelated = packageName.contains("phone") || packageName.contains("dialer");
        boolean isDialog = className.contains("AlertDialog");

        if (isPhoneRelated && isDialog) {
            Log.d(TAG, "========== PHONE DIALOG DETECTED ==========");
            Log.d(TAG, "Package: " + packageName);
            Log.d(TAG, "Class: " + className);
            Log.d(TAG, "Event Text: " + event.getText().toString());
            Log.d(TAG, "==========================================");
        }

        if (isUSSDDialog(event, packageName, className)) {
            if (!isUSSDActive) {
                isUSSDActive = true;
                Log.d(TAG, "=== USSD WINDOW DETECTED ===");
                handleUSSDWindow(event);
            }
        } else if (isUSSDActive && isUSSDWindowClosed(event, packageName, className)) {
            isUSSDActive = false;
            digitInputState = DigitInputState.IDLE; // Reset state
            Log.d(TAG, "=== USSD WINDOW CLOSED ====");
        }
    }

    private boolean isUSSDDialog(AccessibilityEvent event, String packageName, String className) {
        boolean isPhoneRelated = packageName.contains("phone") || packageName.contains("dialer") ||
                packageName.contains("telecom") || packageName.contains("telephony") ||
                packageName.equals("com.android.phone") || packageName.equals("com.google.android.dialer");

        boolean isDialog = className.contains("AlertDialog");

        boolean hasUSSDContent = false;
        if (!event.getText().isEmpty()) {
            String text = event.getText().toString().toLowerCase();
            hasUSSDContent = text.contains("ussd") || text.contains("ussd code") || text.contains("1)") ||
                    text.contains("n next") || text.contains("balance") || text.contains("amafaranga") ||
                    text.contains("kwemeza") || text.contains("pin") || text.contains("shyiramo") ||
                    text.contains("mobile number") || text.contains("nimero ya mobile") ||
                    text.contains("recipient") || text.contains("07xxxxxxxx") ||
                    text.contains("format 07") || text.contains("enter") || text.contains("amount");
        }

        return (isPhoneRelated && isDialog) && hasUSSDContent;
    }

    private void handleUSSDWindow(AccessibilityEvent event) {
        if (!event.getText().isEmpty()) {
            String ussdText = event.getText().toString();
            if (!ussdText.contains("USSD code running")) {
                currentUSSDContent = "";

                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode != null) {
                    extractUSSDText(rootNode);
                    boolean hasInputField = detectInputField(rootNode);

                    Log.d(TAG, "USSD Content: " + currentUSSDContent);
                    Log.d(TAG, "Has Input Field: " + hasInputField);

                    // Analyze different window types
                    String lowerContent = currentUSSDContent.toLowerCase();

                    if (lowerContent.contains("pin") || lowerContent.contains("umubare w'ibanga")) {
                        analyzeInputFields(rootNode, "PIN");
                        handleDigitByDigitInputWindow("Enter your PIN");
                    } else if (lowerContent.contains("mobile number") || lowerContent.contains("nimero ya mobile") ||
                            lowerContent.contains("07xxxxxxxx")) {
                        analyzeInputFields(rootNode, "PHONE NUMBER");
                        handleDigitByDigitInputWindow("Enter phone number starting with zero seven");
                    } else if (lowerContent.contains("enter amount") || lowerContent.contains("amafaranga")) {
                        Log.d(TAG, "AMOUNT WINDOW OPEN: " + event.getText());
                        analyzeInputFields(rootNode, "AMOUNT");
                        handleDigitByDigitInputWindow("Enter the amount to send");
                    } else if (isMenuContent(currentUSSDContent)) {
                        analyzeInputFields(rootNode, "MENU");
                        handleMenuWindow();
                    } else if (hasInputField) {
                        analyzeInputFields(rootNode, "UNKNOWN INPUT");
                        handleDigitByDigitInputWindow("Please provide the requested information");
                    } else {
                        analyzeInputFields(rootNode, "READ-ONLY");
                        handleReadOnlyWindow();
                    }

                    rootNode.recycle();
                }else{
                    Log.d(TAG, "ROOT NODE IS NULL & TEXT IS: " + event.getText());
                }
            }
        }
    }

    private boolean detectInputField(AccessibilityNodeInfo node) {
        if (node == null) return false;

        String className = node.getClassName() != null ? node.getClassName().toString() : "";
        if ("android.widget.EditText".equals(className) || node.isEditable()) {
            Log.d(TAG, "Found input field: " + className);
            return true;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (detectInputField(child)) {
                    child.recycle();
                    return true;
                }
                child.recycle();
            }
        }

        return false;
    }

    // KEEP: Your existing working menu logic
    private void handleMenuWindow() {
        Log.d(TAG, "=== HANDLING MENU WINDOW ===");
        digitInputState = DigitInputState.IDLE; // Ensure we're not in digit mode
        sttManager.setInputMode(STTManager.InputMode.MENU);

        if (!currentUSSDContent.isEmpty()) {
            ttsManager.speakMenu(currentUSSDContent);
        }
    }

    // NEW: Handle digit-by-digit input
    private void handleDigitByDigitInputWindow(String promptText) {
        Log.d(TAG, "=== HANDLING DIGIT-BY-DIGIT INPUT WINDOW ===");

        // Reset state
        digitInputState = DigitInputState.WAITING_FOR_FIRST_DIGIT;
        currentDigitInput.setLength(0);

        // Switch STT to digit-by-digit mode
        sttManager.setInputMode(STTManager.InputMode.DIGIT_BY_DIGIT);

        // Start the session with TTS
        ttsManager.speakDigitInputStart(promptText);
    }

    private void handleReadOnlyWindow() {
        Log.d(TAG, "=== HANDLING READ-ONLY WINDOW ===");
        digitInputState = DigitInputState.IDLE;
        String speechText = currentUSSDContent;
        ttsManager.speakSimpleText(speechText, false);
    }

    // NEW: Handle individual digit recognition
    private void handleDigitRecognized(int digit) {
        Log.d(TAG, "=== PROCESSING RECOGNIZED DIGIT: " + digit + " ===");

        // Add digit to input field
        boolean success = inputSimulator.inputSingleDigit(digit);
        if (!success) {
            Log.e(TAG, "Failed to input digit: " + digit);
            return;
        }

        // Update our local tracking
        currentDigitInput.append(digit);

        // Update state
        if (digitInputState == DigitInputState.WAITING_FOR_FIRST_DIGIT) {
            digitInputState = DigitInputState.WAITING_FOR_NEXT_DIGIT;
        }

        // Provide audio confirmation and prompt for next digit
        ttsManager.confirmDigitAndPromptNext(digit, currentDigitInput.toString());
    }

    // NEW: Handle "done" command
    private void handleDoneCommand() {
        Log.d(TAG, "=== PROCESSING DONE COMMAND ===");

        if (currentDigitInput.length() > 0) {
            digitInputState = DigitInputState.COMPLETED;

            // Speak completion confirmation
            ttsManager.speakInputCompletion(currentDigitInput.toString());

            // Submit the input
            boolean success = inputSimulator.submitLongInput(currentDigitInput.toString());
            if (!success) {
                Log.e(TAG, "Failed to submit long input");
            }

            // Reset for next session
            currentDigitInput.setLength(0);
            digitInputState = DigitInputState.IDLE;
        } else {
            Log.w(TAG, "Done command received but no digits entered");
        }
    }

    // NEW: Handle timeout completion
    private void handleLongInputCompleted(String fullInput) {
        Log.d(TAG, "=== PROCESSING INPUT COMPLETION (TIMEOUT): " + fullInput + " ===");

        digitInputState = DigitInputState.COMPLETED;

        // Speak completion confirmation
        ttsManager.speakInputCompletion(fullInput);

        // Submit the input
        boolean success = inputSimulator.submitLongInput(fullInput);
        if (!success) {
            Log.e(TAG, "Failed to submit long input");
        }

        // Reset for next session
        currentDigitInput.setLength(0);
        digitInputState = DigitInputState.IDLE;
    }

    private void extractUSSDText(AccessibilityNodeInfo node) {
        if (node == null) return;

        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            String textStr = text.toString();
            Log.d(TAG, "Node Text: " + textStr);

            if (isRelevantUSSDContent(textStr)) {
                currentUSSDContent += textStr + " ";
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                extractUSSDText(child);
                child.recycle();
            }
        }
    }

    private void analyzeInputFields(AccessibilityNodeInfo rootNode, String windowType) {
        Log.d(TAG, "========== " + windowType + " WINDOW INPUT ANALYSIS ==========");
        Log.d(TAG, "Content: " + currentUSSDContent);
        analyzeAllNodesForInputs(rootNode, 0);
        Log.d(TAG, "==========================================================");
    }

    private void analyzeAllNodesForInputs(AccessibilityNodeInfo node, int depth) {
        if (node == null) return;

        String indent = "  ".repeat(depth);
        String className = node.getClassName() != null ? node.getClassName().toString() : "null";
        String resourceId = node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "null";
        String text = node.getText() != null ? node.getText().toString() : "null";
        String contentDesc = node.getContentDescription() != null ? node.getContentDescription().toString() : "null";

        boolean isInteresting = className.contains("Edit") ||
                node.isEditable() ||
                node.isClickable() ||
                resourceId.contains("input") ||
                resourceId.contains("edit") ||
                resourceId.contains("field") ||
                resourceId.contains("button") ||
                !text.equals("null");

        if (isInteresting) {
            Log.d(TAG, indent + "*** Node[" + depth + "]: " + className + " ***");
            Log.d(TAG, indent + "    ResourceID: " + resourceId);
            Log.d(TAG, indent + "    Text: '" + text + "'");
            Log.d(TAG, indent + "    ContentDesc: '" + contentDesc + "'");
            Log.d(TAG, indent + "    Editable: " + node.isEditable());
            Log.d(TAG, indent + "    Clickable: " + node.isClickable());

            if (className.contains("Edit") || node.isEditable()) {
                Log.d(TAG, indent + "    🎯 LIKELY INPUT FIELD!");
            }

            if (resourceId.contains("button") || text.toLowerCase().contains("send") || text.toLowerCase().contains("ok")) {
                Log.d(TAG, indent + "    🔘 LIKELY BUTTON!");
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                analyzeAllNodesForInputs(child, depth + 1);
                child.recycle();
            }
        }
    }

    private boolean isRelevantUSSDContent(String text) {
        String lowerText = text.toLowerCase();
        return !lowerText.equals("ok") &&
                !lowerText.equals("cancel") &&
                !lowerText.equals("send") &&
                !lowerText.contains("ussd code running") &&
                text.trim().length() > 2;
    }

    private boolean isMenuContent(String text) {
        return text.contains("1)") || text.contains("0)");
    }

    private boolean isUSSDWindowClosed(AccessibilityEvent event, String packageName, String className) {
        return !className.contains("AlertDialog");
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "USSDDetectorService connected and ready");

        // Initialize TTS
        ttsManager = new TTSManager(this);

        // Initialize input simulator
        inputSimulator = new InputSimulator(this);

        // Initialize STT with enhanced callback
        sttManager = new STTManager(this, new STTManager.STTCallback() {
            @Override
            public void onNumberRecognized(int number) {
                // KEEP: Your existing working menu logic
                Log.d(TAG, "=== USER SPOKE MENU NUMBER: " + number + " ===");
                boolean success = inputSimulator.inputNumberAndSend(number);
                if (!success) {
                    Log.e(TAG, "Failed to process menu input");
                }
            }

            @Override
            public void onDigitRecognized(int digit) {
                // NEW: Handle digit-by-digit input
                handleDigitRecognized(digit);
            }

            @Override
            public void onDoneCommandRecognized() {
                // NEW: Handle "done" command
                handleDoneCommand();
            }

            @Override
            public void onLongInputCompleted(String fullInput) {
                // NEW: Handle timeout completion
                handleLongInputCompleted(fullInput);
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

        // Enhanced TTS to STT connection
        ttsManager.setSTTCallback(new TTSManager.STTTriggerCallback() {
            @Override
            public void onTTSFinished() {
                // Original behavior - for menu and initial input prompts
                mainHandler.post(() -> {
                    if (sttManager != null && sttManager.isReady()) {
                        sttManager.startListening();
                    }
                });
            }

            @Override
            public void onDigitConfirmationFinished() {
                // NEW: After digit confirmation, start listening for next digit
                mainHandler.post(() -> {
                    if (sttManager != null && sttManager.isReady() &&
                            digitInputState == DigitInputState.WAITING_FOR_NEXT_DIGIT) {
                        Log.d(TAG, "Starting to listen for next digit...");
                        sttManager.startListening();
                    }
                });
            }
        });

        // Configure accessibility service
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (ttsManager != null) {
            ttsManager.shutdown();
        }
        if (sttManager != null) {
            sttManager.shutdown();
        }
    }
}