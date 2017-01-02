package nl.codecentric.jenkins.appd;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.logging.Logger;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;


import hudson.model.AbstractBuild;
import nl.codecentric.jenkins.appd.rest.RestConnection;
import nl.codecentric.jenkins.appd.rest.types.MetricData;

/**
 * The {@link AppDynamicsDataCollector} will eventually fetch the performance statistics from the
 * AppDynamics REST interface and parse them into a {@link AppDynamicsReport}.<br>
 * <br>
 * Perhaps create separate Collectors again when this is more logical to create separate graphs. For
 * now this single collector should get all data.
 */
public class AppDynamicsDataCollector {
  private static final Logger LOG = Logger.getLogger(AppDynamicsDataCollector.class.getName());
  private static final String[] STATIC_METRIC_PATHS = {
      "Overall Application Performance|Average Response Time (ms)",
      "Overall Application Performance|Calls per Minute",
      "Overall Application Performance|Normal Average Response Time (ms)",
      "Overall Application Performance|Number of Slow Calls",
      "Overall Application Performance|Number of Very Slow Calls",
      "Overall Application Performance|Errors per Minute",
      "Overall Application Performance|Exceptions per Minute",
      "Overall Application Performance|Infrastructure Errors per Minute"};

  private final RestConnection restConnection;
  private final AbstractBuild<?, ?> build;
  private final PrintStream logger;
  private final int minimumDurationInMinutes;
  private final String[] METRIC_PATHS;

  public AppDynamicsDataCollector(final RestConnection connection, final AbstractBuild<?, ?> build, final PrintStream logger,
          final String customMetricPath, final int minimumDurationInMinutes) {
    this.restConnection = connection;
    this.build = build;
    this.logger = logger;
    this.minimumDurationInMinutes = minimumDurationInMinutes;

    METRIC_PATHS = getMergedMetricPaths(customMetricPath, false);
  }

  public static String[] getStaticMetricPaths() {
      return STATIC_METRIC_PATHS.clone();
  }

  public static String[] getMergedMetricPaths(final String customMetricPath, final boolean encodeCustomMetric) {
      if (StringUtils.isNotBlank(customMetricPath)) {
          String encodedCustomMetricPath = customMetricPath;
          if (encodeCustomMetric) {
            try {
              encodedCustomMetricPath = URLEncoder.encode(customMetricPath, "UTF8");
            } catch (UnsupportedEncodingException e) {
            }
          }

          return (String[]) ArrayUtils.add(STATIC_METRIC_PATHS, encodedCustomMetricPath);
      } else {
          return STATIC_METRIC_PATHS.clone();
      }
  }
  /** Parses the specified reports into {@link AppDynamicsReport}s. */
  public AppDynamicsReport createReportFromMeasurements() {
    long buildStartTime = build.getRootBuild().getTimeInMillis();
    int durationInMinutes = calculateDurationToFetch(buildStartTime);

    LOG.fine(String.format("Current time: %d - Build time: %d - Duration: %d", System.currentTimeMillis(),
        buildStartTime, durationInMinutes));

    final AppDynamicsReport adReport = new AppDynamicsReport(buildStartTime, durationInMinutes);
    for (final String metricPath : METRIC_PATHS) {
      final MetricData metric = restConnection.fetchMetricData(metricPath, durationInMinutes);
      if (metric != null) {
        adReport.addMetrics(metric);
      } else {
          logger.println(String.format("No result for metric: %s \n\t--> Check if metric exists and correctly formatted.", metricPath));
      }
    }

    return adReport;
  }


  private int calculateDurationToFetch(final Long buildStartTime) {
    long duration = System.currentTimeMillis() - buildStartTime;

    int durationInMinutes = (int) (duration / (1000*60));
    if (durationInMinutes < minimumDurationInMinutes) {
      durationInMinutes = minimumDurationInMinutes;
    }

    return durationInMinutes;
  }

}
