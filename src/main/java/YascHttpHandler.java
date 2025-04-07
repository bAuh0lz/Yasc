import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.ToolSource;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.requests.HttpRequest;

import javax.swing.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YascHttpHandler implements HttpHandler {

    private final Map<Integer, String> postRunScriptMap = new HashMap<>();

    /**
     * Invoked by Burp when an HTTP request is about to be sent.
     *
     * @param requestToBeSent information about the HTTP request that is going to be sent.
     * @return An instance of {@link RequestToBeSentAction}.
     */
    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {

        if (!requestToBeSent.toolSource().isFromTool(Main.config.targetToolType.toArray(ToolType[]::new))) {
            return null;
        }

        var targetRequest = RequestToBeSentAction.continueWith(requestToBeSent).request();

        var postScriptName = requestToBeSent.parameter(Main.config.postScriptParameterName, Main.config.postScriptParameterType);
        if (postScriptName != null) {
            targetRequest = targetRequest.withRemovedParameters(postScriptName);
        }

        var disablePostScriptParameter=requestToBeSent.parameter(Main.config.disablePostScriptParameterName,Main.config.disablePostScriptParameterType);
        if (disablePostScriptParameter != null) {
            targetRequest = targetRequest.withRemovedParameters(disablePostScriptParameter);
        }

        var scriptName = requestToBeSent.parameter(Main.config.preScriptParameterName, Main.config.preScriptParameterType);
        if (scriptName != null) {
            targetRequest = targetRequest.withRemovedParameters(scriptName);
        }

        var disablePreScriptParameter=requestToBeSent.parameter(Main.config.disablePreScriptParameterName,Main.config.disablePreScriptParameterType);
        if (disablePreScriptParameter != null) {
            targetRequest = targetRequest.withRemovedParameters(disablePreScriptParameter);
        }

        if (postScriptName != null && disablePostScriptParameter == null) {
            var id = requestToBeSent.messageId();
            postRunScriptMap.put(id, postScriptName.value());
        }

        if (scriptName != null && disablePreScriptParameter == null) {
            var updatedRequest = updateRequest(targetRequest, scriptName.value(), requestToBeSent.toolSource());
            if (updatedRequest != null) {
                targetRequest = updatedRequest;
            }
        }

        return RequestToBeSentAction.continueWith(targetRequest);

    }


    /**
     * Invoked by Burp when an HTTP response has been received.
     *
     * @param responseReceived information about HTTP response that was received.
     * @return An instance of {@link ResponseReceivedAction}.
     */
    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {

        if (!responseReceived.toolSource().isFromTool(Main.config.targetToolType.toArray(ToolType[]::new))) {
            return null;
        }

        var id = responseReceived.messageId();
        var postScriptName = postRunScriptMap.remove(id);

        if (postScriptName == null) {
            return null;
        }

        try {
//            todo: check if request and/or response is needed
            Main.script.run(postScriptName, responseReceived.initiatingRequest(), responseReceived, true);
        } catch (RuntimeException e) {
            checkError(e.getMessage(), responseReceived.toolSource(), postScriptName);
        }

        return null;
    }

    private HttpRequest updateRequest(HttpRequest originalRequest, String scriptName, ToolSource toolSource) {

        byte[] modifiedRequestToBeSent = null;
        try {
            modifiedRequestToBeSent = Main.script.run(scriptName, originalRequest, null, false);
        } catch (RuntimeException e) {
            Main.api.logging().logToError(e);
            checkError(e.getMessage(), toolSource, scriptName);
            if (Main.config.cancelRequestOnScriptError){
                return originalRequest.withService(HttpService.httpService("127.0.0.1", Main.config.cancelRequestPort,false));
            }
        }

        if (modifiedRequestToBeSent == null) {
            return null;
        }

        var updatedRequest = HttpRequest.httpRequest(ByteArray.byteArray(modifiedRequestToBeSent));
        updatedRequest = updatedRequest.withService(originalRequest.httpService());
//            update Content-Length
        updatedRequest = updatedRequest.withBody(updatedRequest.body());

        return updatedRequest;
    }

    private void checkError(String error, ToolSource toolSource, String scriptName) {
        if (error.isEmpty()) {
            return;
        }

        Main.api.logging().raiseErrorEvent(error);

        if (toolSource.isFromTool(ToolType.REPEATER)) {
            var textPane = new JTextPane();
            textPane.setContentType("text/html");
            textPane.setEditable(false);
            textPane.setText("<html><code>" + error.replace("\n", "<br/>") + "</code><html>");
            JOptionPane.showMessageDialog(null, textPane, "Error in " + scriptName, JOptionPane.ERROR_MESSAGE);
        }
    }

}
