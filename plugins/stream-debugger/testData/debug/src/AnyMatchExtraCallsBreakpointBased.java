import java.util.stream.Stream;

public class AnyMatchExtraCallsBreakpointBased {
  public static void main(String[] args) {
    // Breakpoint!
    Stream.of(1).anyMatch(x -> {
      System.out.println("called");
      return false;
    });
  }
}
