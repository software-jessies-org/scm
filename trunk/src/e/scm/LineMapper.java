package e.scm;

import java.util.*;

public class LineMapper {
  private TreeMap treeMap = new TreeMap();
  
  public void addMapping(int fromLine, int toLine) {
    treeMap.put(new Integer(fromLine), new Integer(toLine));
  }
  
  private void printVerboseDiagnostics(String line) {
    //System.err.println(line);
  }
  
  public int translate(int fromLine) {
    int toLine = translateInteger(new Integer(fromLine)).intValue();
    printVerboseDiagnostics("(" + fromLine + " => " + toLine + ")");
    return toLine;
  }
  
  private int getOffsetAtFromLine(Object fromLineObject) {
    Integer fromLine = (Integer) fromLineObject;
    Integer toLine = (Integer) treeMap.get(fromLine);
    int offset = toLine.intValue() - fromLine.intValue();
    printVerboseDiagnostics("(" + fromLine + " => " + toLine + ") gives an offset of " + offset);
    return offset;
  }
  
  public Integer translateInteger(Integer fromLine) {
    if (treeMap.containsKey(fromLine)) {
      printVerboseDiagnostics("Found fromLine directly in map");
      return (Integer) treeMap.get(fromLine);
    }
    
    SortedMap previousContextLines = treeMap.headMap(fromLine);
    if (previousContextLines.isEmpty()) {
      printVerboseDiagnostics("Nothing before fromLine in map");
      return fromLine;
    }
    int offsetAtPreviousContextLine = getOffsetAtFromLine(previousContextLines.lastKey());
    int toLineAccordingToPreviousContext = fromLine.intValue() + offsetAtPreviousContextLine;
    
    SortedMap followingContextLines = treeMap.tailMap(fromLine);
    if (followingContextLines.isEmpty()) {
      printVerboseDiagnostics("Nothing after fromLine in map");
      return new Integer(toLineAccordingToPreviousContext);
    }
    int offsetAtNextContextLine = getOffsetAtFromLine(followingContextLines.firstKey());
    int toLineAccordingToNextContext = fromLine.intValue() + offsetAtNextContextLine;
    
    int averageToLine = (toLineAccordingToPreviousContext + toLineAccordingToNextContext) / 2;
    if (toLineAccordingToPreviousContext != toLineAccordingToNextContext) {
      // Step-by-step patching will *hopefully* avoid this but, if it happens, I want to know.
      System.err.println("Patch is ambiguous!");
      System.err.println("To line is somewhere between " + toLineAccordingToPreviousContext + " and " + toLineAccordingToNextContext);
      System.err.println("Splitting the difference to give an answer of " + averageToLine);
    }
    return new Integer(averageToLine);
  }
}
