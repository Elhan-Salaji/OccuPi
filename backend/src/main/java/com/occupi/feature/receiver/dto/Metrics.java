package com.occupi.feature.receiver.dto;

import java.time.Instant;

public record Metrics(
  String sensorId,
  double cpuPercentage,
  double memoryPercentage,
  int queueSize,
  int sent,
  int dropped,
  float avgProcessTime,
  Instant timestamp
) {}
