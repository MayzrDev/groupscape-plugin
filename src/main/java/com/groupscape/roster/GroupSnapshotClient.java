package com.groupscape.roster;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.groupscape.HttpRequestService;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Polls {@code GET /api/characters/{accountHash}/get-group-data} on a fixed cadence and merges
 * the response into {@link GroupSnapshotState}. That endpoint is incremental - it only returns a
 * member's field when it changed since the {@code from_time} query param - so this keeps a
 * {@link #cursor} advanced to (approximately) "now" after each successful poll, the same
 * since-timestamp pattern the website's group dashboard uses (see group-data.js).
 */
@Slf4j
public class GroupSnapshotClient {
    private static final Type MEMBER_LIST_TYPE =
            new TypeToken<List<GroupSnapshotWireTypes.GroupMemberWire>>() {}.getType();

    private final HttpRequestService httpRequestService;
    private final Gson gson;
    private final GroupSnapshotState state;
    private Instant cursor = Instant.EPOCH;

    public GroupSnapshotClient(HttpRequestService httpRequestService, Gson gson, GroupSnapshotState state) {
        this.httpRequestService = httpRequestService;
        this.gson = gson;
        this.state = state;
    }

    public void poll(String baseUrl, String accountHash, String apiKey) {
        if (baseUrl == null || accountHash == null || apiKey == null || apiKey.trim().isEmpty()) return;

        // Captured before the request goes out (not after it returns) so a member update that
        // lands mid-request is still covered by the *next* poll's from_time rather than skipped.
        Instant requestStartedAt = Instant.now();
        String url = baseUrl + "/api/characters/" + accountHash + "/get-group-data?from_time="
                + urlEncode(DateTimeFormatter.ISO_INSTANT.format(cursor));

        HttpRequestService.HttpResponse response = httpRequestService.get(url, apiKey);
        if (!response.isSuccessful()) {
            log.debug("get-group-data failed: {} {}", response.getCode(), response.getBody());
            return;
        }

        try {
            List<GroupSnapshotWireTypes.GroupMemberWire> members = gson.fromJson(response.getBody(), MEMBER_LIST_TYPE);
            state.applyDelta(members);
            cursor = requestStartedAt;
        } catch (Exception e) {
            log.debug("get-group-data: failed to parse response", e);
        }
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }
}
