import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SkierClient {

  private static final String BASE_URL = "http://35.94.122.244:8080/JavaServlets_war/skiers/";
  private static final int START_NUM_THREADS = 32;
  private static final int NUM_REQUESTS = 200000;
  private static final int POSTS_PER_THREAD = 1000;
  private static int newThreads = 0;
  private static final BlockingQueue<SkierLiftRideEvent> queue = new LinkedBlockingQueue<>();
  private static final String CSV_PATH = "requests_info.csv";
  private static final List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

  public static final AtomicInteger successRequests = new AtomicInteger(0);
  public static final AtomicInteger unsuccessRequests = new AtomicInteger(0);

  public static void main(String[] args) {
    long startTime = System.currentTimeMillis();
    try {
      // csv writer for requests information
      FileWriter csvWriter = new FileWriter(CSV_PATH);
      csvWriter.append("start_time,request_type,latency_ms,response_code\n");

      // thread producer that create 200k skier data and put them into queue
      Thread producer = new Thread(new EventProducer(NUM_REQUESTS, queue));
      producer.start();

      // create thread pool that take data from queue and send post requests to the skier endpoint
      ExecutorService executorService = Executors.newCachedThreadPool();
      CompletionService<Void> completionService = new ExecutorCompletionService<>(executorService);
      for (int i = 0; i < START_NUM_THREADS; i++) {
        completionService.submit(
            new EventConsumer(POSTS_PER_THREAD, queue, BASE_URL, csvWriter, latencies));
      }
      try {
        // return when any of 32 threads finished
        Future<Void> completed = completionService.take();
        completed.get();
        System.out.println("One of the 32 threads has finished, now spawning more threads");
        // creating new threads and customizing number of posts each thread handles
        int postsPerNewThread = 800;
        int remainingRequests = NUM_REQUESTS - START_NUM_THREADS * POSTS_PER_THREAD;
        newThreads = (int) Math.ceil((remainingRequests) / (double) postsPerNewThread);
        for (int i = 0; i < newThreads; i++) {
          int numRequests = Math.min(postsPerNewThread,
              remainingRequests);
          executorService.submit(
              new EventConsumer(numRequests, queue, BASE_URL, csvWriter, latencies));
          remainingRequests -= numRequests;
        }
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(1, TimeUnit.HOURS)) {
          executorService.shutdownNow();
        }
      } catch (InterruptedException e) {
        executorService.shutdownNow();
      }
      long endTime = System.currentTimeMillis();
      long totalTimeMillis = endTime - startTime;
      double totalTimeSeconds = totalTimeMillis / 1000.0;
      double throughput = NUM_REQUESTS / totalTimeSeconds;
      int numThreads = newThreads + 32;
      System.out.println("Total threads:" + numThreads);
      System.out.println("Number of successful requests sent: " + successRequests.get());
      System.out.println("Number of unsuccessful requests: " + unsuccessRequests.get());
      System.out.println("Total runtime (seconds): " + totalTimeSeconds);
      System.out.println("Total throughput (requests per second): " + throughput);
      System.out.println();
      MetricCalculation();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void MetricCalculation() {
    List<Long> sortedLatencies = latencies.stream().sorted().toList();
    long minLatency = sortedLatencies.get(0);
    long maxLatency = sortedLatencies.get(sortedLatencies.size() - 1);
    double meanLatency = sortedLatencies.stream().mapToLong(a -> a).average().orElse(0);
    double median = (sortedLatencies.get(sortedLatencies.size() / 2) + sortedLatencies.get(
        sortedLatencies.size() / 2 - 1)) / 2.0;
    int p99Index = (int) Math.ceil(0.99 * sortedLatencies.size()) - 1;
    long p99 = sortedLatencies.get(p99Index);
    System.out.println("Mean response time (ms): " + meanLatency);
    System.out.println("Median response time (ms): " + median);
    System.out.println("P99 response time (ms): " + p99);
    System.out.println("minimum response time (ms): " + minLatency);
    System.out.println("maximum response time (ms): " + maxLatency);
  }
}

