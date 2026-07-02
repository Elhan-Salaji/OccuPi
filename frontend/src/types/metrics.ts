export interface MetricsResponse {
    sensorId: string;
    cpuPercentage: number;
    memoryPercentage: number;
    queueSize: number;
    sent: number;
    dropped: number;
    avgProcessTime: number;
    timestamp: string;
}