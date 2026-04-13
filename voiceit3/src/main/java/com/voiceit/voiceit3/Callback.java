package com.voiceit.voiceit3;

import org.json.JSONObject;

/**
 * Asynchronous result callback for VoiceItAPI3 calls. Always invoked on the
 * main (UI) thread, so implementations can safely touch views.
 *
 * Exactly one of onSuccess / onFailure is invoked per request.
 */
public interface Callback {

    /**
     * The API returned a 2xx response that parsed as JSON.
     *
     * @param response the parsed JSON body. Never null.
     */
    void onSuccess(JSONObject response);

    /**
     * The request failed. This covers:
     *   - non-2xx HTTP responses (statusCode is the actual code, errorBody
     *     is the parsed JSON body when available)
     *   - network errors (statusCode is 0, error is the IOException)
     *   - JSON parse errors (statusCode reflects the HTTP code, errorBody
     *     is null, error is the parse exception)
     *   - SDK-level validation errors such as malformed userId
     *     (statusCode is 0, errorBody contains responseCode "GERR" and
     *     a human-readable message, error is null)
     *
     * @param statusCode HTTP status code, or 0 if no response was received.
     * @param errorBody  parsed JSON error body, or null if the response was
     *                   not JSON or no response was received.
     * @param error      underlying exception, or null for HTTP errors with a
     *                   parsed errorBody.
     */
    void onFailure(int statusCode, JSONObject errorBody, Throwable error);
}
