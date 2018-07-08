package e.scm;

import e.util.*;
import java.util.*;

public class LineMapper {
  private TreeMap<Integer, Integer> treeMap = new TreeMap<Integer, Integer>();
  
  public void addMapping(int fromLine, int toLine) {
    treeMap.put(fromLine, toLine);
  }
  
  private void printVerboseDiagnostics(String line) {
    //Log.warn("LineMapper: " + line);
  }
  
  public int translate(int fromLine) {
    int toLine = translateInteger(fromLine);
    printVerboseDiagnostics("(" + fromLine + " => " + toLine + ")");
    return toLine;
  }
  
  private int getOffsetAtFromLine(int fromLine) {
    int toLine = treeMap.get(fromLine);
    int offset = toLine - fromLine;
    printVerboseDiagnostics("(" + fromLine + " => " + toLine + ") gives an offset of " + offset);
    return offset;
  }
  
  public Integer translateInteger(Integer fromLine) {
    if (treeMap.containsKey(fromLine)) {
      printVerboseDiagnostics("Found fromLine directly in map");
      return treeMap.get(fromLine);
    }
    
    SortedMap<Integer, Integer> previousContextLines = treeMap.headMap(fromLine);
    if (previousContextLines.isEmpty()) {
      printVerboseDiagnostics("Nothing before fromLine in map");
      return fromLine;
    }
    int offsetAtPreviousContextLine = getOffsetAtFromLine(previousContextLines.lastKey());
    int toLineAccordingToPreviousContext = fromLine.intValue() + offsetAtPreviousContextLine;
    
    SortedMap<Integer, Integer> followingContextLines = treeMap.tailMap(fromLine);
    if (followingContextLines.isEmpty()) {
      printVerboseDiagnostics("Nothing after fromLine in map");
      return Integer.valueOf(toLineAccordingToPreviousContext);
    }
    int offsetAtNextContextLine = getOffsetAtFromLine(followingContextLines.firstKey());
    int toLineAccordingToNextContext = fromLine.intValue() + offsetAtNextContextLine;
    
    int averageToLine = (toLineAccordingToPreviousContext + toLineAccordingToNextContext) / 2;
    if (toLineAccordingToPreviousContext != toLineAccordingToNextContext) {
      // Step-by-step patching will *hopefully* avoid this but, if it happens, I want to know.
      Log.warn("Patch is ambiguous!");
      Log.warn("To line is somewhere between " + toLineAccordingToPreviousContext + " and " + toLineAccordingToNextContext);
      Log.warn("Splitting the difference to give an answer of " + averageToLine);
    }
    return Integer.valueOf(averageToLine);
  }
}
