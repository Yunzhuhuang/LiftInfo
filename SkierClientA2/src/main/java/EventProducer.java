import java.util.concurrent.BlockingQueue;

/**
 * produce multiple random skier lift ride data and put them into queue
 */
public class EventProducer implements Runnable {

  private final int numberOfEvents;
  private final BlockingQueue<SkierLiftRideEvent> queue;

  public EventProducer(int numberOfEvents, BlockingQueue<SkierLiftRideEvent> queue) {
    this.numberOfEvents = numberOfEvents;
    this.queue = queue;
  }

  public void run() {
    for (int i = 0; i < numberOfEvents; i++) {
      SkierLiftRideEvent event = new SkierLiftRideEvent();
      try {
        queue.put(event);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}



