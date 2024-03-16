import java.util.stream.Stream;

public class AllMatchExtraCallsBreakpointBased {
  public static void main(String[] args) {
    // Breakpoint!
    Stream.of(2).allMatch(x -> {
      System.out.println("called");
      return false;
    });
  }
}
