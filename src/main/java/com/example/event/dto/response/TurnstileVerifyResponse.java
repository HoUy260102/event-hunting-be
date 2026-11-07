package com.example.event.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TurnstileVerifyResponse {
    private boolean success;

    @JsonProperty("error-codes")
    private List<String> errorCodes;

    @JsonProperty("challenge_ts")
    private String challengeTs;

    private String hostname;
    private String action;
    private String cdata;
}

