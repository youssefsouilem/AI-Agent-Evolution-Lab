package com.example.anomalydetection.api;

public record AlertRequest(String severity, String message, Long anomalyCheckId) {
}
