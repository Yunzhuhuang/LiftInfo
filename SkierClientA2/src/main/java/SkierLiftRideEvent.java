import java.util.Random;
import java.util.UUID;

/**
 * models skier lift ride information
 */
public class SkierLiftRideEvent {

  private final int skierID;
  private final int resortID;
  private final int liftID;
  private final String seasonID;
  private final String dayID;
  private final int time;

  public SkierLiftRideEvent() {
    Random random = new Random();
    this.skierID = random.nextInt(100000) + 1;  // SkierID between 1 and 100000
    this.resortID = random.nextInt(10) + 1;     // ResortID between 1 and 10
    this.liftID = random.nextInt(40) + 1;       // LiftID between 1 and 40
    this.seasonID = "2024";                            // Fixed seasonID 2024
    this.dayID = String.valueOf(random.nextInt(366) + 1);
    this.time = random.nextInt(360) + 1;        // Time between 1 and 360
  }

  public int getSkierID() {
    return skierID;
  }

  public int getResortID() {
    return resortID;
  }

  public int getLiftID() {
    return liftID;
  }

  public String getSeasonID() {
    return seasonID;
  }

  public String getDayID() {
    return dayID;
  }

  public int getTime() {
    return time;
  }
}
