import com.google.gson.Gson;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.lang3.concurrent.EventCountCircuitBreaker;

public class EventConsumer implements Callable<Void> {
  private final int numOfRequests;
  private final String BASE_URL;
  private final BlockingQueue<SkierLiftRideEvent> queue;
  private final HttpClient httpClient = new HttpClient();
  private final Gson gson = new Gson();
  private final FileWriter csvWriter;
  private final List<Long> latencies;
  private final EventCountCircuitBreaker circuitBreaker;

  public EventConsumer(int numOfRequests, BlockingQueue<SkierLiftRideEvent> queue, String BASE_URL,
      FileWriter csvWriter, List<Long> latencies) {
    this.numOfRequests = numOfRequests;
    this.queue = queue;
    this.BASE_URL = BASE_URL;
    this.csvWriter = csvWriter;
    this.latencies = latencies;
    this.circuitBreaker = new EventCountCircuitBreaker(
        10,             // Threshold: Max failures allowed in the interval
        10,             // Check interval duration
        TimeUnit.SECONDS // Unit for the check interval
    );
  }

  @Override
  public Void call() throws IOException {
    for (int i = 0; i < numOfRequests; i++) {
      long startTime = 0;
      long endTime = 0;
      long latency = 0;
      int responseCode = 0;
      try {
        SkierLiftRideEvent event = queue.take();
        // Check the state of the circuit breaker
        if (!circuitBreaker.checkState()) {
          continue;
        }
        int resortID = event.getResortID();
        String seasonID = event.getSeasonID();
        String dayID = event.getDayID();
        int skierID = event.getSkierID();
        String endpoint =
            BASE_URL + resortID + "/seasons/" + seasonID + "/days/" + dayID + "/skiers/" + skierID;
        Map<String, Integer> payload = new HashMap<>();
        payload.put("time", event.getTime());
        payload.put("liftID", event.getLiftID());
        PostMethod method = new PostMethod(endpoint);
        String jsonPayload = gson.toJson(payload);
        StringRequestEntity requestEntity = new StringRequestEntity(
            jsonPayload,
            "application/json",
            "UTF-8"
        );
        method.setRequestEntity(requestEntity);
        method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER,
            new DefaultHttpMethodRetryHandler(5, true));
        try {
          startTime = System.currentTimeMillis();
          responseCode = httpClient.executeMethod(method);
          endTime = System.currentTimeMillis();
          latency = endTime - startTime;
          if (responseCode != HttpStatus.SC_CREATED) {
            System.err.println("Method failed: " + method.getStatusLine());
            SkierClient.unsuccessRequests.incrementAndGet();
            boolean isClosed = circuitBreaker.incrementAndCheckState(1);
          }
          SkierClient.successRequests.incrementAndGet();
          latencies.add(latency);
          boolean isClosed = circuitBreaker.incrementAndCheckState(0);
        } catch (HttpException e) {
          System.err.println("Fatal protocol violation: " + e.getMessage());
          SkierClient.unsuccessRequests.incrementAndGet();
          boolean isClosed = circuitBreaker.incrementAndCheckState(1);
          e.printStackTrace();
        } catch (IOException e) {
          System.err.println("Fatal transport error: " + e.getMessage());
          boolean isClosed = circuitBreaker.incrementAndCheckState(1);
          SkierClient.unsuccessRequests.incrementAndGet();
          e.printStackTrace();
        } finally {
          method.releaseConnection();
        }
      } catch (InterruptedException | UnsupportedEncodingException e) {
        Thread.currentThread().interrupt();
      }
      synchronized (csvWriter) {
        csvWriter.append(String.format("%d,POST,%d,%d\n", startTime, latency, responseCode));
        csvWriter.flush();
      }
    }
    return null;
  }
}
