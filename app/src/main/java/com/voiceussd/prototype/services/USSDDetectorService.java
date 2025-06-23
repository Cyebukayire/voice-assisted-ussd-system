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

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        String className = event.getClassName() != null ? event.getClassName().toString() : "";

        // TEMPORARY: Log ALL phone-related dialogs
        boolean isPhoneRelated = packageName.contains("phone") || packageName.contains("dialer");
        boolean isDialog = className.contains("AlertDialog");

        if (isPhoneRelated && isDialog) {
            Log.d(TAG, "========== PHONE DIALOG DETECTED ==========");
            Log.d(TAG, "Package: " + packageName);
            Log.d(TAG, "Class: " + className);
            Log.d(TAG, "Event Text: " + event.getText().toString());
            Log.d(TAG, "==========================================");
        }

        Log.d(TAG, "Event" + event.getEventType() +
                ", Package: " + packageName + ", Class: " + className);

        if (isUSSDDialog(event, packageName, className)) {
            if (!isUSSDActive) {
                isUSSDActive = true;
                Log.d(TAG, "=== USSD WINDOW DETECTED ===");
                handleUSSDWindow(event);
            }
        } else if (isUSSDActive && isUSSDWindowClosed(event, packageName, className)) {
            isUSSDActive = false;
            Log.d(TAG, "=== USSD WINDOW CLOSED ====");
        }
    }

    // KEEP: Your existing working USSD detection
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
                    // ADD THESE FOR PHONE NUMBER DETECTION:
                    text.contains("mobile number") || text.contains("nimero ya mobile") ||
                    text.contains("recipient") || text.contains("07xxxxxxxx") ||
                    text.contains("format 07") || text.contains("enter recipient");
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

                    // ANALYZE DIFFERENT WINDOW TYPES
                    String lowerContent = currentUSSDContent.toLowerCase();

                    if (lowerContent.contains("pin") || lowerContent.contains("umubare w'ibanga")) {
                        analyzeInputFields(rootNode, "PIN");
                        handleInputWindow();
                    } else if (lowerContent.contains("mobile number") || lowerContent.contains("nimero ya mobile") ||
                            lowerContent.contains("07xxxxxxxx")) {
                        analyzeInputFields(rootNode, "PHONE NUMBER");
                        handleInputWindow();
                    } else if (isMenuContent(currentUSSDContent)) {
                        analyzeInputFields(rootNode, "MENU");
                        handleMenuWindow();
                    } else if (hasInputField) {
                        analyzeInputFields(rootNode, "UNKNOWN INPUT");
                        handleInputWindow();
                    } else {
                        analyzeInputFields(rootNode, "READ-ONLY");
                        handleReadOnlyWindow();
                    }

                    rootNode.recycle();
                }
            }
        }
    }
    // NEW: Detect if window has an input field
    private boolean detectInputField(AccessibilityNodeInfo node) {
        if (node == null) return false;

        // Check if current node is an input field
        String className = node.getClassName() != null ? node.getClassName().toString() : "";
        if ("android.widget.EditText".equals(className) || node.isEditable()) {
            Log.d(TAG, "Found input field: " + className);
            return true;
        }

        // Check children recursively
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

    // NEW: Handle menu window (your existing working logic)
    private void handleMenuWindow() {
        Log.d(TAG, "=== HANDLING MENU WINDOW ===");

        // Set STT to menu mode
        sttManager.setInputMode(STTManager.InputMode.MENU);

        // Speak the menu (your existing working TTS)
        if (!currentUSSDContent.isEmpty()) {
            ttsManager.speakMenu(currentUSSDContent);
        }
    }

    // NEW: Handle input window (PIN, phone, amount, etc.)
    private void handleInputWindow() {
        Log.d(TAG, "=== HANDLING INPUT WINDOW ===");

        // Set STT to long input mode
        sttManager.setInputMode(STTManager.InputMode.LONG_INPUT);

        // Speak instruction for input
        String speechText = "Please provide the requested information: " + currentUSSDContent +
                ". Speak each digit clearly, one at a time.";

        // Use a simple TTS call (not the menu parser)
        ttsManager.speakSimpleText(speechText);
    }

    // NEW: Handle read-only window
    private void handleReadOnlyWindow() {
        Log.d(TAG, "=== HANDLING READ-ONLY WINDOW ===");

        // Just speak the content, no STT needed
        String speechText = currentUSSDContent;
        ttsManager.speakSimpleText(speechText);
    }

    // KEEP: Your existing text extraction logic
    private void extractUSSDText(AccessibilityNodeInfo node) {
        if (node == null) return;

        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            String textStr = text.toString();
            Log.d(TAG, "Node Text: " + textStr);

            // Collect content
            if (isRelevantUSSDContent(textStr)) {
                currentUSSDContent += textStr + " ";
            }
        }

        // Check all child nodes
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                extractUSSDText(child);
                child.recycle();
            }
        }
    }

    // Add this NEW method to your USSDDetectorService
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

        // Log ALL nodes but highlight interesting ones
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
            Log.d(TAG, indent + "    Focusable: " + node.isFocusable());
            Log.d(TAG, indent + "    Enabled: " + node.isEnabled());

            // Special marking for likely input fields
            if (className.contains("Edit") || node.isEditable()) {
                Log.d(TAG, indent + "    🎯 LIKELY INPUT FIELD!");
            }

            // Special marking for likely buttons
            if (resourceId.contains("button") || text.toLowerCase().contains("send") || text.toLowerCase().contains("ok")) {
                Log.d(TAG, indent + "    🔘 LIKELY BUTTON!");
            }
        }

        // Recursively analyze children
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                analyzeAllNodesForInputs(child, depth + 1);
                child.recycle();
            }
        }
    }

    // UPDATE: Check for any relevant content (not just menu)
    private boolean isRelevantUSSDContent(String text) {
        String lowerText = text.toLowerCase();
        return !lowerText.equals("ok") &&
                !lowerText.equals("cancel") &&
                !lowerText.equals("send") &&
                !lowerText.contains("ussd code running") &&
                text.trim().length() > 2;
    }

    // KEEP: Your existing menu detection logic
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

        // Initialize STT with EXTENDED callback
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
                // NEW: Handle real-time digit input
                Log.d(TAG, "=== USER SPOKE DIGIT: " + digit + " ===");
                boolean success = inputSimulator.inputSingleDigit(digit);
                if (!success) {
                    Log.e(TAG, "Failed to input digit: " + digit);
                }
            }

            @Override
            public void onLongInputCompleted(String fullInput) {
                // NEW: Handle completed long input
                Log.d(TAG, "=== LONG INPUT COMPLETED: " + fullInput + " ===");
                boolean success = inputSimulator.submitLongInput(fullInput);
                if (!success) {
                    Log.e(TAG, "Failed to submit long input");
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

        // KEEP: Your existing working TTS to STT connection
        ttsManager.setSTTCallback(new TTSManager.STTTriggerCallback() {
            @Override
            public void onTTSFinished() {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (sttManager != null && sttManager.isReady()) {
                            sttManager.startListening();
                        }
                    }
                });
            }
        });

        // KEEP: Your existing accessibility service configuration
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