package net.murat.util;

/**
 * Garbage Collection Monitor for tracking allocations during render. Lightweight and GC-free
 * implementation.
 */
public class GCMonitor {

  private long startTime;
  private long endTime;
  private long startMemory;
  private long endMemory;
  private long peakMemory;

  private boolean isRunning = false;

  /** Starts monitoring. */
  public void start() {
    // GC clear
    System.gc();
    try {
      Thread.sleep(10);
    } catch (InterruptedException e) {
    }

    this.startTime = System.nanoTime(); // More specific
    this.startMemory = getUsedMemory();
    this.peakMemory = this.startMemory;
    this.isRunning = true;
  }

  /** Stops monitoring and collects final stats. */
  public void stop() {
    if (!isRunning) return;

    this.endTime = System.nanoTime();
    this.endMemory = getUsedMemory();
    this.isRunning = false;
  }

  /** Captures current memory usage. Call this periodically during long operations. */
  public void captureMemory() {
    if (!isRunning) return;

    long currentMemory = getUsedMemory();
    if (currentMemory > peakMemory) {
      peakMemory = currentMemory;
    }
  }

  /** Returns current used memory in bytes. */
  private long getUsedMemory() {
    Runtime rt = Runtime.getRuntime();
    return rt.totalMemory() - rt.freeMemory();
  }

  /** Returns the duration in milliseconds. */
  public long getDurationMs() {
    return (endTime - startTime) / 1_000_000;
  }

  /** Returns the duration in nanoseconds. */
  public long getDurationNs() {
    return endTime - startTime;
  }

  /** Returns a formatted report of the monitoring results. */
  public String getReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("\n");
    sb.append("╔════════════════════════════════════════════════════════════╗\n");
    sb.append("║              MURAT RAY TRACER - RENDER REPORT             ║\n");
    sb.append("╠════════════════════════════════════════════════════════════╣\n");

    long durationMs = getDurationMs();
    sb.append(
        String.format(
            "║  Render Duration:        %,d ms (%.2f sec)          ║\n",
            durationMs, durationMs / 1000.0));

    // Corrected: memoryUsed = endMemory - startMemory
    long memoryUsed = endMemory - startMemory;
    String sign = memoryUsed >= 0 ? "+" : "";
    sb.append(
        String.format(
            "║  Memory Change:          %s%,d bytes (%s%,.2f MB)    ║\n",
            sign, memoryUsed, sign, memoryUsed / (1024.0 * 1024.0)));

    // If memory is less, GC works
    if (memoryUsed < 0) {
      long gcFreed = -memoryUsed;
      sb.append(
          String.format(
              "║  GC Freed:               %,d bytes (%,.2f MB)      ║\n",
              gcFreed, gcFreed / (1024.0 * 1024.0)));
    }

    sb.append(
        String.format(
            "║  Peak Memory:            %,d bytes (%,.2f MB)      ║\n",
            peakMemory, peakMemory / (1024.0 * 1024.0)));

    sb.append(
        String.format(
            "║  Start Memory:           %,d bytes (%,.2f MB)      ║\n",
            startMemory, startMemory / (1024.0 * 1024.0)));

    sb.append(
        String.format(
            "║  End Memory:             %,d bytes (%,.2f MB)      ║\n",
            endMemory, endMemory / (1024.0 * 1024.0)));

    sb.append("╚════════════════════════════════════════════════════════════╝\n");

    return sb.toString();
  }

  /** Returns a detailed report with GC information. */
  public String getDetailedReport() {
    StringBuilder sb = new StringBuilder();
    sb.append(getReport());
    sb.append("\n");
    sb.append("╔════════════════════════════════════════════════════════════╗\n");
    sb.append("║              HEAP STATISTICS                              ║\n");
    sb.append("╠════════════════════════════════════════════════════════════╣\n");

    Runtime rt = Runtime.getRuntime();
    long totalMemory = rt.totalMemory();
    long freeMemory = rt.freeMemory();
    long usedMemory = totalMemory - freeMemory;
    long maxMemory = rt.maxMemory();

    sb.append(
        String.format(
            "║  Total Heap:             %,d bytes (%,.2f MB)      ║\n",
            totalMemory, totalMemory / (1024.0 * 1024.0)));
    sb.append(
        String.format(
            "║  Free Heap:              %,d bytes (%,.2f MB)      ║\n",
            freeMemory, freeMemory / (1024.0 * 1024.0)));
    sb.append(
        String.format(
            "║  Used Heap:              %,d bytes (%,.2f MB)      ║\n",
            usedMemory, usedMemory / (1024.0 * 1024.0)));
    sb.append(
        String.format(
            "║  Max Heap:               %,d bytes (%,.2f MB)      ║\n",
            maxMemory, maxMemory / (1024.0 * 1024.0)));
    sb.append(
        String.format(
            "║  Heap Usage:             %.1f%%                      ║\n",
            (double) usedMemory / maxMemory * 100));

    sb.append("╚════════════════════════════════════════════════════════════╝\n");

    return sb.toString();
  }

  /** Returns a simple one-line summary. */
  public String getSummary() {
    long durationMs = getDurationMs();
    long memoryUsed = endMemory - startMemory;
    return String.format(
        "Render: %,d ms | Memory: %s%,.2f MB | Peak: %,.2f MB",
        durationMs,
        memoryUsed >= 0 ? "+" : "",
        memoryUsed / (1024.0 * 1024.0),
        peakMemory / (1024.0 * 1024.0));
  }
}
